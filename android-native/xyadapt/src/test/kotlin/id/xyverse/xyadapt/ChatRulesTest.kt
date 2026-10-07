package id.xyverse.xyadapt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRulesTest {
    @Test
    fun `sanitasi membuang karakter kontrol dan penanda arah teks`() {
        assertEquals("halo dunia", ChatRules.sanitize("halo\u0000 dunia"))
        assertEquals("ab", ChatRules.sanitize("a\u202Eb"))
        assertEquals("kosong semu", ChatRules.sanitize("\u200Bkosong semu\u200B"))
        assertEquals("", ChatRules.sanitize("   "))
        assertEquals("", ChatRules.sanitize("\n\n\n"))
    }

    @Test
    fun `sanitasi merapatkan spasi dan membatasi baris serta panjang`() {
        assertEquals("a b", ChatRules.sanitize("a      b"))
        assertEquals("1\n2\n3\n4\n5\n6", ChatRules.sanitize("1\n2\n3\n4\n5\n6\n7\n8"))
        assertEquals(ChatRules.MAX_TEXT, ChatRules.sanitize("x".repeat(ChatRules.MAX_TEXT + 50)).length)
    }

    @Test
    fun `batas klien sama persis dengan batas server`() {
        // Angka ini disalin dari cloudflare/src/chat.js. Kalau salah satu
        // berubah, tes ini yang pertama berteriak.
        assertEquals(400, ChatRules.MAX_TEXT)
        assertEquals(700L, ChatRules.MIN_GAP_MS)
        assertEquals(5, ChatRules.BURST)
        assertEquals(10_000L, ChatRules.WINDOW_MS)
    }

    @Test
    fun `rem laju klien menahan pesan yang terlalu rapat`() {
        val stamps = listOf(10_000L)
        assertTrue(ChatRules.cooldownMs(stamps, 10_100L) > 0)
        assertEquals(0L, ChatRules.cooldownMs(stamps, 10_000L + ChatRules.MIN_GAP_MS))
        assertEquals(0L, ChatRules.cooldownMs(emptyList(), 1L))
    }

    @Test
    fun `burst lima pesan menutup kiriman keenam sampai jendela lewat`() {
        var now = 0L
        val stamps = mutableListOf<Long>()
        repeat(ChatRules.BURST) {
            now += ChatRules.MIN_GAP_MS
            assertEquals("pesan ke-${it + 1} harus lolos", 0L, ChatRules.cooldownMs(stamps, now))
            stamps.add(now)
        }
        now += ChatRules.MIN_GAP_MS
        assertTrue(ChatRules.cooldownMs(stamps, now) > 0)
        assertEquals(0L, ChatRules.cooldownMs(stamps, now + ChatRules.WINDOW_MS))
    }

    @Test
    fun `jejak waktu lama dibuang`() {
        val stamps = listOf(0L, 5_000L, 9_900L)
        assertEquals(listOf(5_000L, 9_900L), ChatRules.pruneStamps(stamps, 11_000L))
        assertEquals(emptyList<Long>(), ChatRules.pruneStamps(stamps, 30_000L))
    }

    @Test
    fun `tombol kirim mati saat kosong, terputus, atau sedang kena rem`() {
        assertTrue(ChatRules.canSend("halo", emptyList(), 0L, connected = true))
        assertFalse(ChatRules.canSend("   ", emptyList(), 0L, connected = true))
        assertFalse(ChatRules.canSend("halo", emptyList(), 0L, connected = false))
        assertFalse(ChatRules.canSend("halo", listOf(0L), 100L, connected = true))
    }

    @Test
    fun `penghitung karakter hanya muncul saat mendekati batas`() {
        assertFalse(ChatRules.showCounter("pendek"))
        assertTrue(ChatRules.showCounter("x".repeat(ChatRules.COUNTER_FROM)))
        assertEquals(0, ChatRules.remaining("x".repeat(ChatRules.MAX_TEXT)))
    }

    @Test
    fun `gelembung berturut-turut dari orang sama digabung`() {
        assertFalse(ChatRules.startsGroup("budi", 0L, "budi", 60_000L))
        assertTrue(ChatRules.startsGroup("budi", 0L, "siti", 1_000L))
        assertTrue(ChatRules.startsGroup("budi", 0L, "budi", 4 * 60_000L))
        assertTrue(ChatRules.startsGroup(null, 0L, "budi", 0L))
    }

    @Test
    fun `jam ditulis dua digit dan tidak pernah keluar rentang hari`() {
        assertEquals("00.00", ChatRules.clock(0L))
        assertEquals("01.05", ChatRules.clock(3_900_000L))
        assertEquals("07.00", ChatRules.clock(0L, offsetMs = 7 * 3_600_000))
        assertEquals("17.00", ChatRules.clock(0L, offsetMs = -7 * 3_600_000))
    }

    @Test
    fun `alasan server diterjemahkan jadi kalimat, detik dibulatkan ke atas`() {
        assertEquals("Sabar sebentar — tunggu 1 detik.", ChatRules.errorText("terlalu-cepat", 300))
        assertEquals("Terlalu banyak pesan. Coba lagi dalam 3 detik.", ChatRules.errorText("terlalu-banyak", 2_100))
        assertEquals("Pesan tidak terkirim.", ChatRules.errorText("entah-apa", 0))
    }

    @Test
    fun `hanya tier vip yang mendapat bingkai`() {
        assertTrue(ChatRules.isVip("vip"))
        assertFalse(ChatRules.isVip("free"))
        assertFalse(ChatRules.isVip(null))
        assertFalse(ChatRules.isVip(""))
        // Nilai baru dari server mendatang tidak boleh diam-diam jadi VIP.
        assertFalse(ChatRules.isVip("VIP"))
        assertFalse(ChatRules.isVip("premium"))
    }

    @Test
    fun `inisial avatar selalu satu karakter yang terbaca`() {
        assertEquals("B", ChatRules.initial("Budi"))
        assertEquals("S", ChatRules.initial("siti"))
        assertEquals("7", ChatRules.initial("7even"))
        assertEquals("A", ChatRules.initial("...andi"))
        assertEquals("?", ChatRules.initial("..."))
        assertEquals("?", ChatRules.initial(""))
    }

    @Test
    fun `jeda sambung ulang naik lalu berhenti di lima belas detik`() {
        assertEquals(1_000L, ChatRules.retryDelayMs(0))
        assertEquals(2_000L, ChatRules.retryDelayMs(1))
        assertEquals(4_000L, ChatRules.retryDelayMs(2))
        assertEquals(8_000L, ChatRules.retryDelayMs(3))
        assertEquals(15_000L, ChatRules.retryDelayMs(4))
        assertEquals(15_000L, ChatRules.retryDelayMs(99))
    }

    // ── Swipe untuk membalas ───────────────────────────────────────────────

    @Test
    fun `geser hanya ke satu arah dan melambat setelah ambang`() {
        assertEquals(0f, ChatRules.swipeOffsetDp(0f), 0.01f)
        assertEquals(0f, ChatRules.swipeOffsetDp(-40f), 0.01f)
        assertEquals(30f, ChatRules.swipeOffsetDp(30f), 0.01f)
        // Tepat di ambang: masih mengikuti jari satu banding satu.
        assertEquals(ChatRules.SWIPE_TRIGGER_DP, ChatRules.swipeOffsetDp(ChatRules.SWIPE_TRIGGER_DP), 0.01f)
        // Setelah ambang: sepertiga, jadi jarak tambahan 30 dp hanya 10 dp.
        assertEquals(ChatRules.SWIPE_TRIGGER_DP + 10f, ChatRules.swipeOffsetDp(ChatRules.SWIPE_TRIGGER_DP + 30f), 0.01f)
        // Sejauh apa pun ditarik, berhenti di batas.
        assertEquals(ChatRules.SWIPE_LIMIT_DP, ChatRules.swipeOffsetDp(10_000f), 0.01f)
    }

    @Test
    fun `balasan terpicu hanya setelah ambang terlewati`() {
        assertFalse(ChatRules.swipeArmed(0f))
        assertFalse(ChatRules.swipeArmed(ChatRules.SWIPE_TRIGGER_DP - 1f))
        assertTrue(ChatRules.swipeArmed(ChatRules.SWIPE_TRIGGER_DP))
        assertTrue(ChatRules.swipeArmed(200f))
    }

    @Test
    fun `ikon balas penuh tepat saat ambang tercapai`() {
        assertEquals(0f, ChatRules.swipeIconAlpha(0f), 0.01f)
        assertEquals(0.5f, ChatRules.swipeIconAlpha(ChatRules.SWIPE_TRIGGER_DP / 2f), 0.01f)
        assertEquals(1f, ChatRules.swipeIconAlpha(ChatRules.SWIPE_TRIGGER_DP), 0.01f)
        assertEquals(1f, ChatRules.swipeIconAlpha(999f), 0.01f)
    }

    // ── Tanpa loading ──────────────────────────────────────────────────────

    @Test
    fun `ruang yang sudah berisi tidak pernah bilang sedang menyambung`() {
        assertFalse(ChatRules.showConnecting(online = false, hasMessages = true, waitingMs = 60_000L))
        assertFalse(ChatRules.showConnecting(online = true, hasMessages = false, waitingMs = 60_000L))
    }

    @Test
    fun `sambungan cepat tidak sempat mengumumkan dirinya`() {
        assertFalse(ChatRules.showConnecting(online = false, hasMessages = false, waitingMs = 0L))
        assertFalse(ChatRules.showConnecting(online = false, hasMessages = false, waitingMs = ChatRules.QUIET_MS - 1))
        assertTrue(ChatRules.showConnecting(online = false, hasMessages = false, waitingMs = ChatRules.QUIET_MS))
    }

    @Test
    fun `baris status menyebut jumlah orang saat tersambung`() {
        assertEquals("7 orang di ruang ini", ChatRules.statusLine(true, 7, true, 0L))
        assertEquals("Tersambung", ChatRules.statusLine(true, 0, true, 0L))
        assertEquals("Luring — pesan baru menyusul", ChatRules.statusLine(false, 3, true, 30_000L))
        assertEquals("Ruang obrolan XyDesk", ChatRules.statusLine(false, 0, false, 0L))
        assertEquals("Menyambung…", ChatRules.statusLine(false, 0, false, 5_000L))
    }

    // ── Balasan ────────────────────────────────────────────────────────────

    @Test
    fun `cuplikan balasan sama aturannya dengan server`() {
        assertEquals("halo semua", ChatRules.replySnippet("halo\n\nsemua"))
        assertEquals("rapat", ChatRules.replySnippet("   rapat  "))
        val panjang = "a".repeat(200)
        assertEquals(ChatRules.REPLY_SNIPPET, ChatRules.replySnippet(panjang).length)
        assertTrue(ChatRules.replySnippet(panjang).endsWith("…"))
    }

    @Test
    fun `membalas diri sendiri tidak menyebut nama sendiri`() {
        assertEquals("Membalas Budi", ChatRules.replyHeader("Budi", "Sari"))
        assertEquals("Membalas diri sendiri", ChatRules.replyHeader("Sari", "Sari"))
        assertEquals("Membalas Budi", ChatRules.replyHeader("Budi", ""))
    }
}
