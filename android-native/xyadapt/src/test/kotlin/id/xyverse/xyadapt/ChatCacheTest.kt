package id.xyverse.xyadapt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCacheTest {
    private fun row(
        id: String,
        at: Long,
        text: String = "halo",
        from: String = "Budi",
        photo: String = "",
        replyId: String = "",
        replyFrom: String = "",
        replyText: String = "",
    ) = ChatCache.Row(
        id = id, from = from, hue = 120, text = text, at = at, mine = false, vip = false,
        photo = photo, replyId = replyId, replyFrom = replyFrom, replyText = replyText,
    )

    @Test
    fun `pesan bolak-balik tanpa kehilangan satu kolom pun`() {
        val rows = listOf(
            row("a", 1_000L, text = "baris satu\nbaris dua", photo = "https://x.id/f.jpg"),
            ChatCache.Row("b", "Sari", 300, "balas", 2_000L, true, true, "", "a", "Budi", "baris satu"),
        )
        val decoded = ChatCache.decode(ChatCache.encode(rows))
        assertEquals(rows, decoded)
    }

    @Test
    fun `tab dan baris baru di teks tidak memecah format`() {
        val rows = listOf(row("a", 1L, text = "kolom\tdua\nbaris\\dua"))
        val decoded = ChatCache.decode(ChatCache.encode(rows))
        assertEquals("kolom\tdua\nbaris\\dua", decoded.single().text)
    }

    @Test
    fun `cache kosong atau versi asing menghasilkan daftar kosong`() {
        assertTrue(ChatCache.decode(null).isEmpty())
        assertTrue(ChatCache.decode("").isEmpty())
        assertTrue(ChatCache.decode("xychat0\nisi lama").isEmpty())
        assertTrue(ChatCache.decode("sampah").isEmpty())
    }

    @Test
    fun `baris rusak dilewati tanpa membuang sisa cache`() {
        val sehat = ChatCache.encode(listOf(row("a", 1L), row("b", 2L)))
        val rusak = sehat + "\nkolom-kurang\tdua"
        assertEquals(listOf("a", "b"), ChatCache.decode(rusak).map { it.id })
    }

    @Test
    fun `cache tidak tumbuh melewati batas`() {
        val many = (1..200).map { row("id$it", it.toLong()) }
        val decoded = ChatCache.decode(ChatCache.encode(many))
        assertEquals(ChatCache.KEEP, decoded.size)
        // Yang disimpan adalah yang terbaru.
        assertEquals("id200", decoded.last().id)
    }

    @Test
    fun `riwayat server menang tanpa membuang pesan lama di layar`() {
        val cached = listOf(row("lama", 1L), row("a", 10L, text = "versi cache"))
        val fresh = listOf(row("a", 10L, text = "versi server"), row("baru", 20L))
        val merged = ChatCache.merge(cached, fresh)
        assertEquals(listOf("lama", "a", "baru"), merged.map { it.id })
        assertEquals("versi server", merged[1].text)
    }

    @Test
    fun `gabungan tetap urut waktu walau server mengirim yang lebih tua`() {
        val cached = listOf(row("b", 20L))
        val fresh = listOf(row("a", 10L))
        assertEquals(listOf("a", "b"), ChatCache.merge(cached, fresh).map { it.at }.let {
            ChatCache.merge(cached, fresh).map { r -> r.id }
        })
    }

    @Test
    fun `riwayat server kosong tidak mengosongkan layar`() {
        val cached = listOf(row("a", 1L))
        assertEquals(cached, ChatCache.merge(cached, emptyList()))
    }
}
