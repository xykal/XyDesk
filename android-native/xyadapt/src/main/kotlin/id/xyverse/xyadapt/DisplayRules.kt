package id.xyverse.xyadapt

/**
 * Daftar monitor host dan aturan memilihnya dari HP.
 *
 * ## Kenapa modul ini ada
 *
 * Sampai versi sebelumnya aplikasi Android menawarkan satu pil "Monitor N"
 * yang berputar `(n + 1) % 4`: angka tetap, tidak pernah membaca keadaan PC.
 * Akibatnya tiga hal yang semuanya salah dengan cara yang sama — UI mengaku
 * tahu sesuatu yang tidak diketahuinya:
 *
 *  1. PC dengan satu monitor tetap menawarkan Monitor 2, 3, dan 4. Host
 *     menolak diam-diam (`select_display` mengembalikan false), layar tidak
 *     berubah, tetapi pil sudah telanjur menulis "Monitor 3".
 *  2. PC dengan lima monitor tidak pernah bisa mencapai yang kelima.
 *  3. Tidak ada nama, ukuran, atau penanda monitor utama, jadi pengguna
 *     memilih dengan cara mencoba satu per satu sampai gambarnya benar.
 *
 * Host sebenarnya sudah mengirim semua yang dibutuhkan di pesan `meta`
 * (`displays[]` + `wanted`) sejak lama; hanya client Android yang tidak
 * membacanya. Modul ini memegang bagian yang murni logika supaya bisa diuji
 * tanpa perangkat: pembersihan daftar, label, dan pemilihan.
 *
 * ## Yang sengaja TIDAK dilakukan di sini
 *
 * Penguraian JSON dan pengiriman paket `0x07 DISPLAY_SELECT` bukan urusan
 * modul ini: yang pertama butuh `org.json` milik Android, yang kedua dibangun
 * di sisi native (`sx_input_display`). Di sini hanya data dan keputusan.
 */
object DisplayRules {
    /** Batas jumlah monitor yang ditampilkan. Windows sendiri berhenti di 64. */
    const val MAX_DISPLAYS = 16

    /** Satu monitor host, sudah bersih dan siap digambar. */
    data class HostDisplay(
        /** Indeks sistem Windows; inilah yang dikirim ke host, bukan posisi di daftar. */
        val index: Int,
        /** Nama perangkat GDI apa adanya, mis. `\\.\DISPLAY1`. Boleh kosong. */
        val name: String = "",
        val width: Int = 0,
        val height: Int = 0,
        /** Hz dari driver. 0 atau negatif = tidak dilaporkan; jangan ditulis "0 Hz". */
        val refreshHz: Int = 0,
        val isPrimary: Boolean = false,
    )

    /**
     * Buang yang tidak bisa dipercaya, urutkan, dan potong di [MAX_DISPLAYS].
     *
     * Host yang sehat tidak pernah mengirim daftar aneh, tetapi daftar ini
     * datang lewat jaringan dari proses lain: indeks negatif, indeks kembar
     * (dua entri untuk monitor yang sama saat daftar dibaca tepat ketika
     * monitor dicabut), atau ukuran 0×0 dari driver virtual yang belum siap.
     * Satu entri rusak tidak boleh membuat seluruh pemilih menghilang.
     */
    fun sanitize(raw: List<HostDisplay>): List<HostDisplay> {
        val seen = HashSet<Int>()
        return raw.asSequence()
            .filter { it.index >= 0 && it.width > 0 && it.height > 0 }
            .filter { seen.add(it.index) }
            .sortedBy { it.index }
            .take(MAX_DISPLAYS)
            .map { it.copy(name = it.name.trim().take(80), refreshHz = if (it.refreshHz in 1..1000) it.refreshHz else 0) }
            .toList()
    }

    /**
     * Pemilih hanya pantas muncul bila benar-benar ada pilihan.
     *
     * Satu monitor berarti tombol yang satu-satunya kemungkinan hasilnya
     * adalah tidak terjadi apa-apa.
     */
    fun shouldOffer(displays: List<HostDisplay>): Boolean = displays.size > 1

    /**
     * Indeks yang sedang dipakai, dijatuhkan ke sesuatu yang nyata.
     *
     * [wanted] berasal dari host dan biasanya benar, tetapi bisa menunjuk
     * monitor yang baru saja dicabut. Dalam hal itu yang ditandai aktif adalah
     * monitor utama, bukan "tidak ada" — layar toh tetap menampilkan sesuatu.
     */
    fun activeIndex(displays: List<HostDisplay>, wanted: Int): Int {
        if (displays.isEmpty()) return wanted.coerceAtLeast(0)
        displays.firstOrNull { it.index == wanted }?.let { return it.index }
        return (displays.firstOrNull { it.isPrimary } ?: displays.first()).index
    }

    /**
     * Monitor berikutnya untuk tombol putar, berputar di daftar **nyata**.
     *
     * Dipakai jalur pintas (satu ketukan di HUD) dan sebagai jaring pengaman
     * bila panel sempit. Daftar kosong = tetap di tempat.
     */
    fun nextIndex(displays: List<HostDisplay>, current: Int): Int {
        if (displays.isEmpty()) return current
        val at = displays.indexOfFirst { it.index == activeIndex(displays, current) }
        return displays[(at + 1) % displays.size].index
    }

    /** Label pendek untuk pil: "Layar 1", memakai nomor urut manusia. */
    fun shortLabel(display: HostDisplay, position: Int): String = "Layar ${position + 1}"

    /**
     * Label penuh: "Layar 1 · 2560×1440 · 144 Hz · utama".
     *
     * Nama perangkat GDI (`\\.\DISPLAY1`) sengaja tidak dipakai sebagai judul:
     * itu nama untuk program, bukan untuk orang, dan nomornya kerap tidak
     * cocok dengan urutan monitor di meja. Ukuran dan lencana "utama" jauh
     * lebih berguna untuk mengenali layar mana yang dimaksud.
     */
    fun label(display: HostDisplay, position: Int): String = buildString {
        append(shortLabel(display, position))
        if (display.width > 0 && display.height > 0) append(" · ${display.width}×${display.height}")
        if (display.refreshHz > 0) append(" · ${display.refreshHz} Hz")
        if (display.isPrimary) append(" · utama")
    }

    /**
     * Permintaan yang pantas dikirim ke host, atau null bila tidak ada gunanya.
     *
     * Memilih monitor yang sedang aktif tidak dikirim: host akan menanggapinya
     * dengan respawn capture — layar berkedip hitam sekejap — demi hasil yang
     * sudah ada di layar. Indeks yang tidak ada di daftar juga tidak dikirim.
     */
    fun request(displays: List<HostDisplay>, current: Int, target: Int): Int? {
        if (displays.none { it.index == target }) return null
        if (activeIndex(displays, current) == target) return null
        return target
    }
}
