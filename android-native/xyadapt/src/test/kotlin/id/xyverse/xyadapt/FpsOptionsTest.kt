package id.xyverse.xyadapt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FpsOptionsTest {
    @Test
    fun panel_60hz_tidak_pernah_ditawari_120() {
        assertEquals(listOf(30, 60), FpsOptions.forDisplay(60f))
        assertEquals(listOf(30, 60), FpsOptions.forDisplay(59.94f))
    }

    @Test
    fun panel_cepat_membuka_laju_tinggi() {
        assertEquals(listOf(30, 60, 120), FpsOptions.forDisplay(120f))
        assertEquals(listOf(30, 60, 120, 144), FpsOptions.forDisplay(144f))
        assertEquals(listOf(30, 60, 120, 144), FpsOptions.forDisplay(165f))
    }

    @Test
    fun refresh_rate_dilaporkan_meleset_tetap_dikenali() {
        // Panel nyata melapor 119,98 / 143,86 — bukan angka bulat.
        assertEquals(listOf(30, 60, 120), FpsOptions.forDisplay(119.98f))
        assertEquals(listOf(30, 60, 120, 144), FpsOptions.forDisplay(143.86f))
        // Tapi toleransi tidak boleh selebar satu anak tangga penuh:
        // 90 Hz tidak berhak atas 120.
        assertEquals(listOf(30, 60), FpsOptions.forDisplay(90f))
    }

    @Test
    fun refresh_rate_tak_terbaca_jatuh_ke_aman() {
        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY * 0f)) {
            assertEquals("refreshHz=$bad", FpsOptions.BASE, FpsOptions.forDisplay(bad))
        }
        // Panel 30 Hz (atau salah lapor) tetap dapat 30 dan 60: 60 adalah
        // bawaan tersimpan, menghapusnya akan mengunci pengguna di 30.
        assertEquals(listOf(30, 60), FpsOptions.forDisplay(30f))
    }

    @Test
    fun preferensi_tersimpan_dijatuhkan_ke_opsi_valid() {
        // Pengguna pindah dari HP 144 Hz ke HP 60 Hz: 144 tersimpan → jadi 60.
        assertEquals(60, FpsOptions.clampToDisplay(144, 60f))
        assertEquals(60, FpsOptions.clampToDisplay(120, 60f))
        // Di panel cepat, nilai tersimpan dipertahankan apa adanya.
        assertEquals(144, FpsOptions.clampToDisplay(144, 144f))
        assertEquals(60, FpsOptions.clampToDisplay(60, 144f))
        // Nilai liar tidak pernah lolos jadi pilihan.
        assertEquals(30, FpsOptions.clampToDisplay(1, 144f))
        assertEquals(144, FpsOptions.clampToDisplay(240, 144f))
    }

    @Test
    fun hasil_selalu_himpunan_bagian_yang_dikenal_host() {
        for (hz in listOf(0f, 30f, 48f, 60f, 75f, 90f, 120f, 144f, 165f, 240f)) {
            val options = FpsOptions.forDisplay(hz)
            assertTrue("$hz: $options", options.all { it in FpsOptions.ALL })
            assertTrue("$hz: harus menanjak", options == options.sorted())
            assertTrue("$hz: minimal 30/60", options.containsAll(FpsOptions.BASE))
            assertEquals("$hz: tanpa duplikat", options.size, options.distinct().size)
        }
    }
}
