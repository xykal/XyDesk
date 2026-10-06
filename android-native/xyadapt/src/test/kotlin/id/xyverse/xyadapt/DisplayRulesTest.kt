package id.xyverse.xyadapt

import id.xyverse.xyadapt.DisplayRules.HostDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayRulesTest {
    private val dua = listOf(
        HostDisplay(0, "\\\\.\\DISPLAY1", 2560, 1440, 144, isPrimary = true),
        HostDisplay(1, "\\\\.\\DISPLAY2", 1920, 1080, 60),
    )

    @Test
    fun satu_monitor_tidak_menawarkan_pilihan() {
        assertFalse(DisplayRules.shouldOffer(DisplayRules.sanitize(listOf(dua[0]))))
        assertFalse(DisplayRules.shouldOffer(emptyList()))
        assertTrue(DisplayRules.shouldOffer(dua))
    }

    @Test
    fun entri_rusak_dibuang_tanpa_menjatuhkan_sisanya() {
        val kotor = listOf(
            HostDisplay(-1, "negatif", 1920, 1080),
            HostDisplay(0, "  \\\\.\\DISPLAY1  ", 2560, 1440, 144, isPrimary = true),
            HostDisplay(2, "virtual belum siap", 0, 0),
            HostDisplay(1, "\\\\.\\DISPLAY2", 1920, 1080, 60),
            HostDisplay(1, "kembar", 1280, 720),
        )
        val bersih = DisplayRules.sanitize(kotor)
        assertEquals(listOf(0, 1), bersih.map { it.index })
        assertEquals("\\\\.\\DISPLAY1", bersih[0].name)
        // Yang pertama menang saat indeks kembar: urutan host dipercaya.
        assertEquals(1920, bersih[1].width)
    }

    @Test
    fun daftar_diurutkan_dan_dipotong() {
        val banyak = (30 downTo 0).map { HostDisplay(it, "m$it", 800, 600) }
        val bersih = DisplayRules.sanitize(banyak)
        assertEquals(DisplayRules.MAX_DISPLAYS, bersih.size)
        assertEquals((0 until DisplayRules.MAX_DISPLAYS).toList(), bersih.map { it.index })
    }

    @Test
    fun refresh_rate_tidak_masuk_akal_disembunyikan_bukan_ditulis_nol() {
        val aneh = DisplayRules.sanitize(listOf(HostDisplay(0, "virtual", 1920, 1080, refreshHz = 0)))
        assertEquals(0, aneh[0].refreshHz)
        assertFalse(DisplayRules.label(aneh[0], 0).contains("Hz"))
        val gila = DisplayRules.sanitize(listOf(HostDisplay(0, "virtual", 1920, 1080, refreshHz = 100000)))
        assertEquals(0, gila[0].refreshHz)
    }

    @Test
    fun monitor_aktif_jatuh_ke_utama_bila_yang_diminta_sudah_dicabut() {
        assertEquals(1, DisplayRules.activeIndex(dua, 1))
        assertEquals(0, DisplayRules.activeIndex(dua, 7))
        val tanpaUtama = listOf(HostDisplay(3, "a", 800, 600), HostDisplay(5, "b", 800, 600))
        assertEquals(3, DisplayRules.activeIndex(tanpaUtama, 9))
    }

    @Test
    fun putaran_berhenti_di_jumlah_monitor_nyata() {
        // Inilah bug yang ditutup: dulu putarannya selalu 0,1,2,3 apa pun isi PC.
        assertEquals(1, DisplayRules.nextIndex(dua, 0))
        assertEquals(0, DisplayRules.nextIndex(dua, 1))
        val satu = listOf(dua[0])
        assertEquals(0, DisplayRules.nextIndex(satu, 0))
        assertEquals(4, DisplayRules.nextIndex(emptyList(), 4))
    }

    @Test
    fun indeks_tidak_berurutan_tetap_bisa_diputar() {
        val lompat = listOf(HostDisplay(0, "a", 800, 600, isPrimary = true), HostDisplay(2, "b", 800, 600))
        assertEquals(2, DisplayRules.nextIndex(lompat, 0))
        assertEquals(0, DisplayRules.nextIndex(lompat, 2))
    }

    @Test
    fun memilih_monitor_yang_sudah_aktif_tidak_dikirim() {
        assertNull(DisplayRules.request(dua, current = 0, target = 0))
        assertEquals(1, DisplayRules.request(dua, current = 0, target = 1))
    }

    @Test
    fun monitor_di_luar_daftar_tidak_pernah_dikirim() {
        assertNull(DisplayRules.request(dua, current = 0, target = 3))
        assertNull(DisplayRules.request(emptyList(), current = 0, target = 1))
        // Host yang melaporkan monitor aktif di luar daftar tetap bisa pindah.
        assertEquals(1, DisplayRules.request(dua, current = 9, target = 1))
    }

    @Test
    fun label_menyebut_ukuran_dan_lencana_utama() {
        assertEquals("Layar 1 · 2560×1440 · 144 Hz · utama", DisplayRules.label(dua[0], 0))
        assertEquals("Layar 2 · 1920×1080 · 60 Hz", DisplayRules.label(dua[1], 1))
        assertEquals("Layar 2", DisplayRules.shortLabel(dua[1], 1))
    }

    @Test
    fun nomor_label_mengikuti_posisi_daftar_bukan_indeks_sistem() {
        // Monitor dengan indeks 2 yang menjadi satu-satunya tetangga tetap
        // disebut "Layar 2" bagi pengguna; indeks sistem hanya untuk protokol.
        val lompat = DisplayRules.sanitize(listOf(HostDisplay(0, "a", 800, 600), HostDisplay(2, "b", 800, 600)))
        assertEquals("Layar 2 · 800×600", DisplayRules.label(lompat[1], 1))
        assertEquals(2, lompat[1].index)
    }
}
