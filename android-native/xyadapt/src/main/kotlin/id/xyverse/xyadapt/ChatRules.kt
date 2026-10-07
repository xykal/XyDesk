package id.xyverse.xyadapt

/**
 * Aturan chat global di sisi klien.
 *
 * Server adalah penentu akhir (lihat `cloudflare/src/chat.js`); berkas ini
 * menyalin batas yang sama supaya aplikasi bisa menahan pesan yang pasti
 * ditolak sebelum menghabiskan satu putaran jaringan, dan supaya tombol kirim
 * bisa mati pada saat yang tepat. Semuanya fungsi murni agar bisa diuji tanpa
 * Android.
 */
object ChatRules {
    /** Harus sama dengan MAX_TEXT di server. */
    const val MAX_TEXT = 400

    /** Harus sama dengan MIN_GAP_MS di server. */
    const val MIN_GAP_MS = 700L

    /** Harus sama dengan RATE_BURST / RATE_WINDOW_MS di server. */
    const val BURST = 5
    const val WINDOW_MS = 10_000L

    /** Ambang "pesan sudah panjang" untuk menampilkan penghitung sisa karakter. */
    const val COUNTER_FROM = 320

    /**
     * Rapikan teks sebelum dikirim: buang karakter kontrol, rapatkan spasi,
     * batasi 6 baris dan MAX_TEXT karakter. Mengembalikan string kosong bila
     * tidak layak kirim.
     */
    fun sanitize(input: String): String {
        val stripped = buildString(input.length) {
            for (ch in input) {
                val code = ch.code
                val control = code < 0x20 && ch != '\n'
                val invisible = code == 0x7F ||
                    code in 0x200B..0x200F ||
                    code in 0x202A..0x202E ||
                    code in 0x2066..0x2069
                if (!control && !invisible) append(ch)
            }
        }
        val lines = stripped.split('\n')
            .take(6)
            .map { line -> line.replace(Regex("[ \t]{2,}"), " ").trim() }
        var text = lines.joinToString("\n").trim()
        while (text.contains("\n\n\n")) text = text.replace("\n\n\n", "\n\n")
        if (text.length > MAX_TEXT) text = text.take(MAX_TEXT).trim()
        return text
    }

    /** Sisa karakter; negatif berarti sudah lewat batas. */
    fun remaining(input: String): Int = MAX_TEXT - input.length

    /** Penghitung hanya muncul saat mendekati batas — bukan sepanjang waktu. */
    fun showCounter(input: String): Boolean = input.length >= COUNTER_FROM

    /**
     * Boleh kirim? `stamps` adalah waktu pesan yang sudah terkirim (ms epoch).
     * Mengembalikan sisa tunggu dalam ms; 0 berarti boleh kirim sekarang.
     */
    fun cooldownMs(stamps: List<Long>, nowMs: Long): Long {
        val recent = stamps.filter { nowMs - it < WINDOW_MS }
        val last = recent.maxOrNull() ?: return 0L
        val gap = MIN_GAP_MS - (nowMs - last)
        if (gap > 0) return gap
        if (recent.size >= BURST) return WINDOW_MS - (nowMs - recent.min())
        return 0L
    }

    /** Tombol kirim hidup hanya bila ada isi dan rem laju sedang lepas. */
    fun canSend(input: String, stamps: List<Long>, nowMs: Long, connected: Boolean): Boolean =
        connected && sanitize(input).isNotEmpty() && cooldownMs(stamps, nowMs) == 0L

    /** Buang jejak waktu yang sudah keluar jendela supaya daftarnya tidak tumbuh. */
    fun pruneStamps(stamps: List<Long>, nowMs: Long): List<Long> =
        stamps.filter { nowMs - it < WINDOW_MS }

    /**
     * Gelembung berturut-turut dari orang yang sama dalam 3 menit digabung:
     * hanya yang pertama menampilkan nama dan avatar.
     */
    fun startsGroup(previousFrom: String?, previousAt: Long, from: String, at: Long): Boolean =
        previousFrom != from || at - previousAt > 3 * 60_000L

    /** Label waktu pendek, 24 jam, tanpa bergantung pustaka format Android. */
    fun clock(atMs: Long, offsetMs: Int = 0): String {
        val local = atMs + offsetMs
        val minuteOfDay = Math.floorMod(local / 60_000L, 1440L).toInt()
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        return "${if (h < 10) "0$h" else "$h"}.${if (m < 10) "0$m" else "$m"}"
    }

    /**
     * Tingkat akun yang dikenal gelembung chat. Server mengirimnya sebagai
     * teks; apa pun selain "vip" diperlakukan biasa — termasuk nilai baru
     * dari server versi mendatang yang belum dikenal aplikasi ini.
     */
    fun isVip(tier: String?): Boolean = tier == "vip"

    /**
     * Inisial untuk avatar: satu huruf, dari karakter huruf/angka pertama.
     * Nama yang hanya berisi tanda baca jatuh ke tanda tanya, bukan kosong.
     */
    fun initial(name: String): String {
        val ch = name.firstOrNull { it.isLetterOrDigit() } ?: return "?"
        return ch.uppercase()
    }


    // ── Gerak: swipe untuk membalas ─────────────────────────────────────────
    //
    // Nilainya dp, bukan piksel, supaya ambangnya sama jauhnya di layar
    // rapat maupun renggang.

    /** Jarak geser yang sudah cukup untuk memicu balasan. */
    const val SWIPE_TRIGGER_DP = 56f

    /** Sejauh apa pun jarinya menarik, gelembung berhenti di sini. */
    const val SWIPE_LIMIT_DP = 76f

    /**
     * Posisi gelembung saat digeser, dengan tahanan karet setelah ambang.
     *
     * Tanpa tahanan, gelembung ikut jari sampai ke tepi layar dan gerakannya
     * terasa seperti barang jatuh, bukan kontrol. Setelah melewati ambang,
     * setiap piksel tambahan hanya memindahkan sepertiganya — jari terus
     * bergerak, gelembung melambat, dan batasnya terasa tanpa perlu dijelaskan.
     *
     * Geseran ke arah berlawanan (ke kiri) tidak memindahkan apa pun: daftar
     * pesan yang ikut bergoyang dua arah terasa longgar.
     */
    fun swipeOffsetDp(dragDp: Float): Float {
        if (dragDp <= 0f) return 0f
        if (dragDp <= SWIPE_TRIGGER_DP) return minOf(dragDp, SWIPE_LIMIT_DP)
        val extra = (dragDp - SWIPE_TRIGGER_DP) / 3f
        return minOf(SWIPE_TRIGGER_DP + extra, SWIPE_LIMIT_DP)
    }

    /** Sudah cukup jauh untuk melepaskan dan membalas? */
    fun swipeArmed(dragDp: Float): Boolean = dragDp >= SWIPE_TRIGGER_DP

    /**
     * Kepekatan ikon balas yang muncul di belakang gelembung: ikut jarak
     * geser, penuh tepat saat ambang tercapai supaya isyaratnya mendahului
     * getaran, bukan menyusulnya.
     */
    fun swipeIconAlpha(dragDp: Float): Float {
        if (dragDp <= 0f) return 0f
        return (dragDp / SWIPE_TRIGGER_DP).coerceIn(0f, 1f)
    }

    // ── Gerak: tahan untuk fokus ────────────────────────────────────────────

    /** Lama tekan sebelum satu gelembung diangkat dan latar diburamkan. */
    const val HOLD_MS = 320L

    /** Pembesaran gelembung yang sedang difokuskan. */
    const val FOCUS_SCALE = 1.04f

    /** Radius blur latar saat satu gelembung difokuskan, dalam dp. */
    const val FOCUS_BLUR_DP = 18f

    // ── "Tanpa loading" ─────────────────────────────────────────────────────

    /**
     * Berapa lama ruang boleh diam sebelum mengaku sedang menyambung.
     *
     * Sambungan yang jadi dalam 300 ms tidak perlu diumumkan. Memberi label
     * "Menyambung…" pada setiap pembukaan layar membuat aplikasi terasa lambat
     * justru saat ia cepat — kata itu muncul, berkedip, lalu hilang sebelum
     * sempat dibaca, dan yang tertinggal hanyalah kesan bahwa ada yang harus
     * ditunggu.
     */
    const val QUIET_MS = 900L

    /**
     * Boleh menampilkan keadaan "sedang menyambung" kepada pengguna?
     *
     * Jawabannya tidak, selama (a) sudah ada pesan di layar — yang datang dari
     * cache perangkat — atau (b) percobaan sambung belum melewati QUIET_MS.
     * Ruang yang sudah berisi tidak punya alasan memberi tahu bahwa soketnya
     * sedang dibuka: pesan lama tetap terbaca, dan yang baru akan muncul
     * sendiri saat tiba.
     */
    fun showConnecting(online: Boolean, hasMessages: Boolean, waitingMs: Long): Boolean {
        if (online) return false
        if (hasMessages) return false
        return waitingMs >= QUIET_MS
    }

    /**
     * Teks kecil di bawah judul. Saat sudah ada isi, keadaan sambungan tidak
     * pernah merebut tempat nama ruang — paling jauh menjadi catatan "luring".
     */
    fun statusLine(online: Boolean, people: Int, hasMessages: Boolean, waitingMs: Long): String {
        if (online) return if (people > 0) "$people orang di ruang ini" else "Tersambung"
        if (hasMessages) return "Luring — pesan baru menyusul"
        if (showConnecting(false, false, waitingMs)) return "Menyambung…"
        return "Ruang obrolan XyDesk"
    }

    // ── Balasan ─────────────────────────────────────────────────────────────

    /** Panjang cuplikan kutipan; sama dengan REPLY_SNIPPET di server. */
    const val REPLY_SNIPPET = 90

    /** Cuplikan satu baris untuk blok balasan. Sama aturannya dengan server. */
    fun replySnippet(text: String): String {
        val line = text.replace(Regex("\\s+"), " ").trim()
        if (line.length <= REPLY_SNIPPET) return line
        return line.take(REPLY_SNIPPET - 1).trim() + "…"
    }

    /**
     * Label di atas kotak tulis saat sedang membalas seseorang. Membalas diri
     * sendiri berbunyi "Membalas diri sendiri" — bukan nama sendiri, yang
     * terbaca seperti berbicara pada orang lain.
     */
    fun replyHeader(from: String, me: String): String =
        if (me.isNotEmpty() && from == me) "Membalas diri sendiri" else "Membalas $from"

    /** Pesan error dari server diterjemahkan menjadi kalimat untuk pengguna. */
    fun errorText(reason: String, retryMs: Long): String = when (reason) {
        "terlalu-cepat" -> "Sabar sebentar — tunggu ${seconds(retryMs)} detik."
        "terlalu-banyak" -> "Terlalu banyak pesan. Coba lagi dalam ${seconds(retryMs)} detik."
        "kosong" -> "Pesannya kosong."
        "terlalu-panjang" -> "Pesannya terlalu panjang."
        "ruang-penuh" -> "Ruang obrolan sedang penuh. Coba lagi nanti."
        else -> "Pesan tidak terkirim."
    }

    private fun seconds(ms: Long): Long = maxOf(1L, (ms + 999L) / 1000L)

    /**
     * Jeda sambung ulang: 1, 2, 4, 8, 15, 15, ... detik. Disamakan nadanya
     * dengan ReconnectPolicy sesi supaya aplikasi tidak punya dua perilaku
     * sambung ulang yang berbeda.
     */
    fun retryDelayMs(attempt: Int): Long {
        if (attempt <= 0) return 1_000L
        val raw = 1_000L shl minOf(attempt, 4)
        return minOf(raw, 15_000L)
    }
}
