package id.xyverse.xyadapt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PadMappingTest {
    private fun keys(list: List<PadAction>) = list.filterIsInstance<PadAction.Key>()
    private fun moves(list: List<PadAction>) = list.filterIsInstance<PadAction.Move>()
    private fun btns(list: List<PadAction>) = list.filterIsInstance<PadAction.Button>()

    @Test
    fun tombolDipetakanKeVirtualKey() {
        val m = PadKbm()
        val down = keys(m.feed(PadState(buttons = PadBits.A)))
        assertEquals(listOf(PadAction.Key(Vk.SPACE, true)), down)
        // Keadaan yang sama tidak boleh menekan ulang.
        assertTrue(m.feed(PadState(buttons = PadBits.A)).isEmpty())
        val up = keys(m.feed(PadState()))
        assertEquals(listOf(PadAction.Key(Vk.SPACE, false)), up)
    }

    @Test
    fun bitTanpaPemetaanDiabaikan() {
        val m = PadKbm(PadProfile(keys = mapOf(PadBits.A to Vk.SPACE), stickKeys = null, look = false, leftTrigger = null, rightTrigger = null))
        assertTrue(m.feed(PadState(buttons = PadBits.Y or PadBits.LB)).isEmpty())
        assertTrue(m.heldKeys().isEmpty())
    }

    @Test
    fun stikKiriJadiWasdDanDiagonalBisaBarengan() {
        val m = PadKbm()
        val out = keys(m.feed(PadState(lx = -0.9f, ly = -0.9f)))
        assertEquals(setOf(Vk.W, Vk.A), out.filter { it.down }.map { it.vk }.toSet())
        assertEquals(setOf(Vk.W, Vk.A), m.heldKeys().toSet())
    }

    @Test
    fun histeresisStikMencegahGetaran() {
        val m = PadKbm()
        // Di bawah ambang nyala: tidak terjadi apa-apa.
        assertTrue(m.feed(PadState(ly = -0.45f)).isEmpty())
        // Lewat ambang nyala: W ditekan.
        assertEquals(listOf(PadAction.Key(Vk.W, true)), keys(m.feed(PadState(ly = -0.55f))))
        // Turun ke antara dua ambang: tetap ditekan, tidak ada kejadian.
        assertTrue(m.feed(PadState(ly = -0.40f)).isEmpty())
        assertTrue(m.heldKeys().contains(Vk.W))
        // Di bawah ambang mati: baru dilepas.
        assertEquals(listOf(PadAction.Key(Vk.W, false)), keys(m.feed(PadState(ly = -0.30f))))
        assertTrue(PadKbm.hysteresisGap() > 0f)
    }

    @Test
    fun triggerJadiTombolMouseDenganHisteresis() {
        val m = PadKbm()
        assertEquals(listOf(PadAction.Button(0, true)), btns(m.feed(PadState(rt = 0.9f))))
        assertTrue(m.feed(PadState(rt = 0.4f)).isEmpty())
        assertEquals(listOf(PadAction.Button(0, false)), btns(m.feed(PadState(rt = 0.2f))))
        assertEquals(listOf(PadAction.Button(1, true)), btns(m.feed(PadState(lt = 1f))))
    }

    @Test
    fun stikKananMenggerakkanMouseDanMenghormatiZonaMati() {
        val m = PadKbm()
        // Di dalam zona mati: diam total, drift stik tidak boleh menggeser kursor.
        assertTrue(moves(m.feed(PadState(rx = 0.1f, ry = 0f))).isEmpty())
        val out = moves(m.feed(PadState(rx = 1f, ry = 0f)))
        assertEquals(1, out.size)
        assertTrue(out[0].dx > 0)
        assertEquals(0, out[0].dy)
    }

    @Test
    fun doronganKecilLebihPelanDaripadaDoronganPenuh() {
        val pelan = PadKbm()
        val penuh = PadKbm()
        var a = 0
        var b = 0
        repeat(10) {
            a += moves(pelan.feed(PadState(rx = 0.4f))).sumOf { it.dx }
            b += moves(penuh.feed(PadState(rx = 1f))).sumOf { it.dx }
        }
        assertTrue("pelan=$a penuh=$b", a in 1 until b)
    }

    @Test
    fun kecepatanPandanganTidakTergantungLajuLaporan() {
        val cepat = PadKbm()
        val lambat = PadKbm()
        var a = 0
        var b = 0
        repeat(8) { a += moves(cepat.feed(PadState(rx = 1f), frameMs = 8)).sumOf { it.dx } }
        repeat(4) { b += moves(lambat.feed(PadState(rx = 1f), frameMs = 16)).sumOf { it.dx } }
        assertEquals(b.toFloat(), a.toFloat(), 2f)
    }

    @Test
    fun gerakPelanTidakHilangKarenaPembulatan() {
        val m = PadKbm(PadProfile(sensitivity = 0.2f))
        var total = 0
        repeat(40) { total += moves(m.feed(PadState(rx = 0.2f))).sumOf { it.dx } }
        assertTrue("total=$total", total >= 1)
    }

    @Test
    fun releaseMelepasSemuaYangDitahan() {
        val m = PadKbm()
        m.feed(PadState(buttons = PadBits.A or PadBits.RB, lx = -1f, rt = 1f))
        assertEquals(setOf(Vk.SPACE, Vk.SHIFT, Vk.A), m.heldKeys().toSet())
        val out = m.release()
        assertEquals(setOf(Vk.SPACE, Vk.SHIFT, Vk.A), keys(out).map { it.vk }.toSet())
        assertTrue(keys(out).none { it.down })
        assertEquals(listOf(PadAction.Button(0, false)), btns(out))
        assertTrue(m.heldKeys().isEmpty())
        assertTrue(m.heldButtons().isEmpty())
        assertTrue(m.release().isEmpty())
    }

    @Test
    fun gantiProfilMelepasTombolProfilLama() {
        val m = PadKbm()
        m.feed(PadState(buttons = PadBits.A))
        val out = m.setProfile(PadProfile.DESKTOP)
        assertEquals(listOf(PadAction.Key(Vk.SPACE, false)), keys(out))
        // Profil desktop tidak memetakan A sama sekali.
        assertTrue(m.feed(PadState(buttons = PadBits.A)).isEmpty())
        // Dan tidak punya WASD.
        assertTrue(m.feed(PadState(lx = -1f)).isEmpty())
    }

    @Test
    fun nilaiTidakWajarDiabaikan() {
        val m = PadKbm()
        assertTrue(m.feed(PadState(lx = Float.NaN, ly = Float.NaN, rx = Float.NaN, ry = Float.POSITIVE_INFINITY, lt = Float.NaN)).isEmpty())
        assertTrue(m.heldKeys().isEmpty())
        assertTrue(m.heldButtons().isEmpty())
    }

    @Test
    fun modeOtomatisHidupHanyaSaatHostTanpaVigem() {
        assertFalse(PadKbm.active(PadKbm.MODE_AUTO, padPresent = false, hostGamepadAvailable = false))
        assertTrue(PadKbm.active(PadKbm.MODE_AUTO, padPresent = true, hostGamepadAvailable = false))
        assertFalse(PadKbm.active(PadKbm.MODE_AUTO, padPresent = true, hostGamepadAvailable = true))
        assertTrue(PadKbm.active(PadKbm.MODE_ON, padPresent = true, hostGamepadAvailable = true))
        assertFalse(PadKbm.active(PadKbm.MODE_OFF, padPresent = true, hostGamepadAvailable = false))
    }

    @Test
    fun putaranModeDanLabelnya() {
        assertEquals(PadKbm.MODE_ON, PadKbm.nextMode(PadKbm.MODE_AUTO))
        assertEquals(PadKbm.MODE_OFF, PadKbm.nextMode(PadKbm.MODE_ON))
        assertEquals(PadKbm.MODE_AUTO, PadKbm.nextMode(PadKbm.MODE_OFF))
        assertEquals(PadKbm.MODE_AUTO, PadKbm.nextMode(99))
        assertEquals("Otomatis", PadKbm.modeLabel(PadKbm.MODE_AUTO))
        assertEquals("Selalu", PadKbm.modeLabel(PadKbm.MODE_ON))
        assertEquals("Mati", PadKbm.modeLabel(PadKbm.MODE_OFF))
    }

    @Test
    fun dpadDanTombolSistemIkutTerpetakan() {
        val m = PadKbm()
        val out = keys(m.feed(PadState(buttons = PadBits.DPAD_LEFT or PadBits.START or PadBits.BACK)))
        assertEquals(setOf(Vk.LEFT, Vk.ESC, Vk.TAB), out.map { it.vk }.toSet())
        assertEquals("DPAD_LEFT", PadBits.name(PadBits.DPAD_LEFT))
    }
}
