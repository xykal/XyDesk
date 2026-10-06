package id.xyverse.xyadapt

/**
 * Aturan transfer berkas sisi klien — cerminan `host/src/filetransfer.rs`.
 *
 * Dua sisi harus sepakat sebelum satu byte pun berpindah: kalau aplikasi
 * menampilkan "laporan.exe " sementara host menyimpannya sebagai
 * "laporan.exe", pengguna menyetujui nama yang bukan nama yang ditulis ke
 * disknya. Karena itu pembersihan nama, batas ukuran, dan ukuran potongan
 * ditulis ulang di sini persis seperti di host, lengkap dengan ujinya.
 *
 * Tidak ada I/O di berkas ini. Semua fungsi murni supaya bisa diuji tanpa
 * perangkat, tanpa sesi, dan tanpa izin penyimpanan.
 */
object FileRules {
    /** Batas satu berkas: 4 GiB, sama dengan `MAX_FILE_BYTES` di host. */
    const val MAX_FILE_BYTES: Long = 4L * 1024 * 1024 * 1024

    /** Isi satu potongan: 64 KiB, sama dengan `MAX_CHUNK_BYTES` di host. */
    const val MAX_CHUNK_BYTES: Int = 64 * 1024

    /**
     * Isi satu potongan yang **dikirim**: 16 KiB.
     *
     * Batas protokol tetap 64 KiB supaya pengirim lama tetap diterima,
     * tetapi mengirim sebesar itu sendiri tidak aman: satu pesan SCTP
     * dibatasi 64 KiB **termasuk** 9 byte header `CHUNK`, jadi potongan
     * 65536 byte menghasilkan pesan 65545 byte yang tidak pernah berangkat —
     * pengiriman menggantung tanpa satu pun pesan kesalahan. Ditemukan oleh
     * uji loopback arah host → client, bukan oleh uji unit mana pun.
     */
    const val SEND_CHUNK_BYTES: Int = 16 * 1024

    /** Panjang nama setelah dibersihkan, dalam karakter. */
    const val MAX_NAME_CHARS: Int = 120

    /** Nama cadangan bila kiriman tidak menyisakan apa pun yang aman. */
    const val FALLBACK_NAME: String = "berkas"

    private val DOS_DEVICES = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    private val ILLEGAL = setOf('<', '>', ':', '"', '|', '?', '*')

    private val RISKY = setOf(
        "exe", "msi", "bat", "cmd", "com", "scr", "pif", "ps1", "vbs", "vbe",
        "js", "jse", "wsf", "wsh", "hta", "cpl", "lnk",
    )

    /**
     * Membersihkan nama berkas menjadi nama yang aman ditulis di Windows.
     * Selalu mengembalikan nama yang tidak kosong.
     */
    fun sanitizeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        var cleaned = base.filter { !it.isISOControl() && it !in ILLEGAL }
        cleaned = cleaned.trimEnd('.', ' ').trimStart()
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return FALLBACK_NAME
        val stem = cleaned.substringBefore('.').uppercase()
        var name = if (stem in DOS_DEVICES) "_$cleaned" else cleaned
        if (name.length > MAX_NAME_CHARS) {
            val dot = name.lastIndexOf('.')
            val ext = if (dot > 0 && name.length - dot - 1 in 1..10) name.substring(dot) else ""
            name = name.take(MAX_NAME_CHARS - ext.length) + ext
        }
        return name
    }

    /**
     * Apakah berkas ini akan dijalankan Windows begitu diklik dua kali.
     * Dipakai untuk memberi peringatan, bukan untuk memblokir — memblokir
     * diam-diam membuat transfer sah gagal tanpa penjelasan.
     */
    fun isRiskyExtension(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return false
        return name.substring(dot + 1).lowercase() in RISKY
    }

    /** Ukuran berkas yang boleh ditawarkan. Nol byte ikut ditolak. */
    fun acceptableSize(size: Long): Boolean = size in 1..MAX_FILE_BYTES

    /** Jumlah potongan untuk ukuran tertentu, dibulatkan ke atas. */
    fun chunkCount(size: Long, chunk: Int = MAX_CHUNK_BYTES): Long {
        if (size <= 0 || chunk <= 0) return 0
        return (size + chunk - 1) / chunk
    }

    /** Kemajuan 0..100, dibulatkan ke bawah supaya tidak pernah 100 terlalu dini. */
    fun percent(sent: Long, total: Long): Int {
        if (total <= 0) return 0
        val value = sent.coerceAtLeast(0).coerceAtMost(total) * 100 / total
        return value.toInt()
    }

    /** Ukuran dalam bentuk yang dibaca manusia, format sama dengan host. */
    fun humanBytes(bytes: Long): String {
        val kb = 1024.0
        val b = bytes.toDouble()
        return when {
            bytes < 1024 -> "$bytes B"
            b < kb * kb -> "%.0f KB".format(b / kb)
            b < kb * kb * kb -> "%.1f MB".format(b / (kb * kb))
            else -> "%.1f GB".format(b / (kb * kb * kb))
        }
    }

    /**
     * Perkiraan sisa waktu dalam detik dari laju rata-rata sejauh ini.
     * Mengembalikan null bila belum ada cukup data — menampilkan "0 detik
     * lagi" pada transfer yang baru mulai lebih menyesatkan daripada tidak
     * menampilkan apa-apa.
     */
    fun etaSeconds(sent: Long, total: Long, elapsedMs: Long): Long? {
        if (sent <= 0 || elapsedMs < 1000 || sent >= total) return null
        val bytesPerMs = sent.toDouble() / elapsedMs
        if (bytesPerMs <= 0.0) return null
        return ((total - sent) / bytesPerMs / 1000.0).toLong().coerceAtLeast(1)
    }
}
