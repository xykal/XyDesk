package id.xyverse.xyadapt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji ini sengaja memakai contoh yang sama persis dengan uji Rust di
 * `host/src/filetransfer.rs`. Dua sisi yang berbeda jawabannya untuk nama
 * yang sama adalah bug keamanan, bukan sekadar beda tampilan.
 */
class FileRulesTest {
    @Test
    fun jalur_dibuang_sampai_nama_berkasnya_saja() {
        assertEquals("hosts", FileRules.sanitizeName("..\\..\\Windows\\System32\\drivers\\etc\\hosts"))
        assertEquals("passwd", FileRules.sanitizeName("/etc/passwd"))
        assertEquals("rahasia.txt", FileRules.sanitizeName("C:\\Users\\xykal\\rahasia.txt"))
    }

    @Test
    fun nama_kosong_jadi_nama_cadangan() {
        assertEquals(FileRules.FALLBACK_NAME, FileRules.sanitizeName(""))
        assertEquals(FileRules.FALLBACK_NAME, FileRules.sanitizeName(".."))
        assertEquals(FileRules.FALLBACK_NAME, FileRules.sanitizeName("   "))
        assertEquals(FileRules.FALLBACK_NAME, FileRules.sanitizeName("///"))
    }

    @Test
    fun karakter_ilegal_dan_kontrol_dibuang() {
        assertEquals("laporan.txt", FileRules.sanitizeName("lap\u0000or*an?.txt"))
        assertEquals("datastream.txt", FileRules.sanitizeName("data:stream.txt"))
        assertEquals("barisbaru.txt", FileRules.sanitizeName("baris\nbaru.txt"))
    }

    @Test
    fun titik_dan_spasi_di_ujung_dipangkas() {
        assertEquals("laporan.exe", FileRules.sanitizeName("laporan.exe "))
        assertEquals("laporan.txt", FileRules.sanitizeName("laporan.txt..."))
    }

    @Test
    fun nama_perangkat_dos_diberi_awalan() {
        assertEquals("_CON", FileRules.sanitizeName("CON"))
        assertEquals("_lpt1.txt", FileRules.sanitizeName("lpt1.txt"))
        assertEquals("_nul.log", FileRules.sanitizeName("nul.log"))
        assertEquals("console.txt", FileRules.sanitizeName("console.txt"))
    }

    @Test
    fun nama_panjang_dipotong_tapi_ekstensinya_bertahan() {
        val hasil = FileRules.sanitizeName("a".repeat(300) + ".tar.gz")
        assertEquals(FileRules.MAX_NAME_CHARS, hasil.length)
        assertTrue(hasil.endsWith(".gz"))
    }

    @Test
    fun ekstensi_berbahaya_dikenali() {
        assertTrue(FileRules.isRiskyExtension("pasang.EXE"))
        assertTrue(FileRules.isRiskyExtension("skrip.ps1"))
        assertTrue(FileRules.isRiskyExtension("pintasan.lnk"))
        assertFalse(FileRules.isRiskyExtension("foto.png"))
        assertFalse(FileRules.isRiskyExtension("tanpa-ekstensi"))
        assertFalse(FileRules.isRiskyExtension("titik.di.ujung."))
    }

    @Test
    fun ukuran_nol_dan_kebesaran_ditolak() {
        assertFalse(FileRules.acceptableSize(0))
        assertFalse(FileRules.acceptableSize(-1))
        assertTrue(FileRules.acceptableSize(1))
        assertTrue(FileRules.acceptableSize(FileRules.MAX_FILE_BYTES))
        assertFalse(FileRules.acceptableSize(FileRules.MAX_FILE_BYTES + 1))
    }

    @Test
    fun jumlah_potongan_membulat_ke_atas() {
        assertEquals(0L, FileRules.chunkCount(0))
        assertEquals(1L, FileRules.chunkCount(1, 1024))
        assertEquals(1L, FileRules.chunkCount(1024, 1024))
        assertEquals(2L, FileRules.chunkCount(1025, 1024))
        assertEquals(0L, FileRules.chunkCount(100, 0))
    }

    @Test
    fun kemajuan_tidak_pernah_100_sebelum_byte_terakhir() {
        assertEquals(0, FileRules.percent(0, 1000))
        assertEquals(99, FileRules.percent(999, 1000))
        assertEquals(100, FileRules.percent(1000, 1000))
        assertEquals(100, FileRules.percent(1200, 1000))
        assertEquals(0, FileRules.percent(10, 0))
    }

    @Test
    fun ukuran_dibaca_manusia_sama_dengan_host() {
        assertEquals("0 B", FileRules.humanBytes(0))
        assertEquals("999 B", FileRules.humanBytes(999))
        assertEquals("2 KB", FileRules.humanBytes(2048))
        assertEquals("5.0 MB", FileRules.humanBytes(5L * 1024 * 1024))
        assertEquals("3.0 GB", FileRules.humanBytes(3L * 1024 * 1024 * 1024))
    }

    @Test
    fun eta_diam_sampai_ada_cukup_data() {
        assertNull(FileRules.etaSeconds(0, 1000, 5000))
        assertNull(FileRules.etaSeconds(500, 1000, 200))
        assertNull(FileRules.etaSeconds(1000, 1000, 5000))
        // 1 MB dalam 2 detik → sisa 1 MB ≈ 2 detik lagi.
        assertEquals(2L, FileRules.etaSeconds(1024 * 1024, 2L * 1024 * 1024, 2000))
    }
}
