package id.xyverse.xyadapt

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerCaptureTest {
    @Test
    fun tangkapan_hanya_saat_ada_mouse_fisik_di_sesi_yang_hidup() {
        assertTrue(CaptureRules.wanted(mousePresent = true, connected = true, enabled = true))
        assertFalse(CaptureRules.wanted(mousePresent = false, connected = true, enabled = true))
        assertFalse(CaptureRules.wanted(mousePresent = true, connected = false, enabled = true))
        assertFalse(CaptureRules.wanted(mousePresent = true, connected = true, enabled = false))
    }

    @Test
    fun tangkapan_dilepas_saat_mengetik_mengatur_atau_presentasi() {
        assertFalse(CaptureRules.wanted(true, true, true, imeOpen = true))
        assertFalse(CaptureRules.wanted(true, true, true, editing = true))
        assertFalse(CaptureRules.wanted(true, true, true, presenting = true))
    }

    @Test
    fun gerak_pecahan_tidak_hilang_tapi_menumpuk() {
        val m = RelMotion()
        // Lima kali 0,4 piksel: empat pertama belum satu piksel penuh.
        assertNull(m.feed(0.4f, 0f))
        assertNull(m.feed(0.4f, 0f))
        val keluar = m.feed(0.4f, 0f)
        assertNotNull(keluar)
        assertEquals(1, keluar!![0])
        // Sisanya tetap disimpan, tidak dibuang.
        assertTrue(m.pending()[0] > 0.1f)
    }

    @Test
    fun gerak_negatif_diperlakukan_sama_persis() {
        val m = RelMotion()
        assertNull(m.feed(-0.4f, 0f))
        assertNull(m.feed(-0.4f, 0f))
        assertEquals(-1, m.feed(-0.4f, 0f)!![0])
    }

    @Test
    fun kejadian_tanpa_gerak_tidak_mengirim_paket() {
        val m = RelMotion()
        assertNull(m.feed(0f, 0f))
        assertNull(m.feed(0.2f, -0.3f))
    }

    @Test
    fun sensitivitas_mengalikan_dan_dijepit_ke_rentang_waras() {
        val m = RelMotion()
        assertArrayEquals(intArrayOf(6, 0), m.feed(3f, 0f, 2f))
        assertEquals(RelMotion.MAX_SENS, m.clampSensitivity(99f), 0.001f)
        assertEquals(RelMotion.MIN_SENS, m.clampSensitivity(0f), 0.001f)
        // Sensitivitas gila tidak boleh membuat satu kejadian meledak.
        val cepat = RelMotion()
        val hasil = cepat.feed(100000f, -100000f, 5f)!!
        assertEquals(32767, hasil[0])
        assertEquals(-32768, hasil[1])
    }

    @Test
    fun nilai_rusak_dari_sensor_diabaikan() {
        val m = RelMotion()
        assertNull(m.feed(Float.NaN, 1f))
        assertNull(m.feed(1f, Float.POSITIVE_INFINITY))
        // Keadaan tetap bersih setelah nilai rusak.
        assertArrayEquals(intArrayOf(2, 0), m.feed(2f, 0f))
    }

    @Test
    fun reset_membuang_sisa_pecahan() {
        val m = RelMotion()
        m.feed(0.6f, 0.6f)
        m.reset()
        assertEquals(0f, m.pending()[0], 0.0001f)
        assertNull(m.feed(0.3f, 0.3f))
    }

    @Test
    fun scroll_dibulatkan_ke_klik_terdekat_bukan_dipotong() {
        assertEquals(120, RelMotion.wheel(1f))
        assertEquals(-120, RelMotion.wheel(-1f))
        assertEquals(72, RelMotion.wheel(0.6f))
        assertEquals(0, RelMotion.wheel(0f))
        assertEquals(0, RelMotion.wheel(Float.NaN))
        assertEquals(32767, RelMotion.wheel(1000f))
    }

    @Test
    fun ambang_satu_piksel_simetris() {
        assertTrue(RelMotion.significant(1f))
        assertTrue(RelMotion.significant(-1f))
        assertFalse(RelMotion.significant(0.99f))
        assertFalse(RelMotion.significant(-0.99f))
    }
}
