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
