package id.xyverse.xyadapt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRulesTest {
    private val semua = listOf(
        "KEY", "MOUSE", "SCROLL", "SCROLL_X", "SCROLL_WHEEL", "CHORD", "TOGGLE",
        "STICK_KEYS", "STICK_MOUSE", "GAMEPAD_BUTTON", "GAMEPAD_STICK_L",
        "GAMEPAD_STICK_R", "GAMEPAD_TRIGGER_L", "GAMEPAD_TRIGGER_R",
    )

    @Test
    fun setiap_jenis_kontrol_punya_keluarga_yang_benar() {
        assertEquals(InputFamily.KEYBOARD, OverlayRules.family("KEY"))
        assertEquals(InputFamily.KEYBOARD, OverlayRules.family("CHORD"))
        assertEquals(InputFamily.KEYBOARD, OverlayRules.family("STICK_KEYS"))
        assertEquals(InputFamily.MOUSE, OverlayRules.family("MOUSE"))
        assertEquals(InputFamily.MOUSE, OverlayRules.family("SCROLL_WHEEL"))
        assertEquals(InputFamily.MOUSE, OverlayRules.family("STICK_MOUSE"))
        assertEquals(InputFamily.GAMEPAD, OverlayRules.family("GAMEPAD_BUTTON"))
        assertEquals(InputFamily.GAMEPAD, OverlayRules.family("GAMEPAD_TRIGGER_R"))
        assertEquals(InputFamily.SHARED, OverlayRules.family("TOGGLE"))
    }

    @Test
    fun jenis_baru_yang_belum_dikenal_ikut_tampil_bukan_hilang() {
        assertEquals(InputFamily.SHARED, OverlayRules.family("SESUATU_YANG_BARU"))
        assertTrue(
            OverlayRules.visible(
                "SESUATU_YANG_BARU",
                OverlayRules.MODE_AUTO,
                keyboard = true,
                mouse = true,
                gamepad = true,
            ),
        )
    }

    @Test
    fun keyboard_fisik_hanya_menyembunyikan_tombol_keyboard() {
        val m = OverlayRules.MODE_AUTO
        assertFalse(OverlayRules.visible("KEY", m, keyboard = true))
        assertFalse(OverlayRules.visible("STICK_KEYS", m, keyboard = true))
        // Inti perbaikannya: gamepad virtual TIDAK ikut hilang.
        assertTrue(OverlayRules.visible("GAMEPAD_BUTTON", m, keyboard = true))
        assertTrue(OverlayRules.visible("GAMEPAD_STICK_L", m, keyboard = true))
        assertTrue(OverlayRules.visible("MOUSE", m, keyboard = true))
    }

    @Test
    fun mouse_fisik_hanya_menyembunyikan_klik_dan_scroll() {
        val m = OverlayRules.MODE_AUTO
        assertFalse(OverlayRules.visible("MOUSE", m, mouse = true))
        assertFalse(OverlayRules.visible("SCROLL", m, mouse = true))
        assertFalse(OverlayRules.visible("SCROLL_WHEEL", m, mouse = true))
        assertTrue(OverlayRules.visible("KEY", m, mouse = true))
        assertTrue(OverlayRules.visible("GAMEPAD_TRIGGER_L", m, mouse = true))
    }

    @Test
    fun gamepad_fisik_hanya_menyembunyikan_kontrol_gamepad() {
        val m = OverlayRules.MODE_AUTO
        assertFalse(OverlayRules.visible("GAMEPAD_BUTTON", m, gamepad = true))
        assertFalse(OverlayRules.visible("GAMEPAD_STICK_R", m, gamepad = true))
        assertTrue(OverlayRules.visible("KEY", m, gamepad = true))
        assertTrue(OverlayRules.visible("MOUSE", m, gamepad = true))
    }

    @Test
    fun tiga_perangkat_sekaligus_menyisakan_kontrol_tanpa_padanan_fisik() {
        val terlihat = semua.filter {
            OverlayRules.visible(it, OverlayRules.MODE_AUTO, keyboard = true, mouse = true, gamepad = true)
        }
        assertEquals(listOf("TOGGLE"), terlihat)
    }

    @Test
    fun mode_selalu_tampil_mengalahkan_deteksi() {
        semua.forEach {
            assertTrue(
                "$it seharusnya tetap tampil",
                OverlayRules.visible(it, OverlayRules.MODE_ALWAYS, keyboard = true, mouse = true, gamepad = true),
            )
        }
    }

    @Test
    fun mode_mati_menyembunyikan_semuanya_bahkan_saat_mengatur() {
        semua.forEach {
            assertFalse(OverlayRules.visible(it, OverlayRules.MODE_OFF))
            assertFalse(OverlayRules.visible(it, OverlayRules.MODE_OFF, edit = true))
        }
        assertFalse(OverlayRules.layerVisible(semua, OverlayRules.MODE_OFF, edit = true))
    }

    @Test
    fun saat_mengatur_tata_letak_semua_tombol_terlihat() {
        // Tombol yang disembunyikan otomatis tetap harus bisa dipindah dan
        // dihapus; kalau tidak, ia mustahil diurus justru saat diurus.
        semua.forEach {
            assertTrue(
                OverlayRules.visible(it, OverlayRules.MODE_AUTO, keyboard = true, mouse = true, gamepad = true, edit = true),
            )
        }
    }

    @Test
    fun lapisan_dimatikan_hanya_kalau_benar_benar_tidak_ada_yang_tampil() {
        assertTrue(OverlayRules.layerVisible(semua, OverlayRules.MODE_AUTO))
        assertTrue(
            OverlayRules.layerVisible(semua, OverlayRules.MODE_AUTO, keyboard = true, mouse = true, gamepad = true),
        )
        val hanyaKeyboard = listOf("KEY", "CHORD")
        assertFalse(OverlayRules.layerVisible(hanyaKeyboard, OverlayRules.MODE_AUTO, keyboard = true))
        assertTrue(OverlayRules.layerVisible(hanyaKeyboard, OverlayRules.MODE_AUTO, mouse = true))
        assertFalse(OverlayRules.layerVisible(emptyList(), OverlayRules.MODE_AUTO))
    }

    @Test
    fun mode_berputar_dan_nilai_rusak_jatuh_ke_otomatis() {
        assertEquals(OverlayRules.MODE_ALWAYS, OverlayRules.nextMode(OverlayRules.MODE_AUTO))
        assertEquals(OverlayRules.MODE_OFF, OverlayRules.nextMode(OverlayRules.MODE_ALWAYS))
        assertEquals(OverlayRules.MODE_AUTO, OverlayRules.nextMode(OverlayRules.MODE_OFF))
        assertEquals(OverlayRules.MODE_AUTO, OverlayRules.normalizeMode(-3))
        assertEquals(OverlayRules.MODE_AUTO, OverlayRules.normalizeMode(99))
        // Nilai rusak dibaca sebagai AUTO lebih dulu, jadi klik berikutnya
        // maju ke ALWAYS — bukan diam di tempat.
        assertEquals(OverlayRules.MODE_ALWAYS, OverlayRules.nextMode(99))
        assertTrue(OverlayRules.modeLabel(99).isNotEmpty())
    }

    @Test
    fun pesan_deteksi_menyebut_perangkatnya_dan_akibatnya() {
        assertEquals("", OverlayRules.detectionMessage(OverlayRules.MODE_AUTO, false, false, false))
        assertEquals(
            "Gamepad terdeteksi. Tombol di layar yang digantikannya disembunyikan.",
            OverlayRules.detectionMessage(OverlayRules.MODE_AUTO, false, false, true),
        )
        assertEquals(
            "Keyboard dan Mouse terdeteksi. Tombol di layar yang digantikannya disembunyikan.",
            OverlayRules.detectionMessage(OverlayRules.MODE_AUTO, true, true, false),
        )
        assertEquals(
            "Keyboard, Mouse, dan Gamepad terdeteksi. Kontrol di layar tetap tampil.",
            OverlayRules.detectionMessage(OverlayRules.MODE_ALWAYS, true, true, true),
        )
    }
}
