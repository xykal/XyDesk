package id.xyverse.xyadapt

import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileReceiveTest {
    private fun sha(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun terima(data: ByteArray, potongan: Int = FileRules.MAX_CHUNK_BYTES): Pair<FileReceiver, ByteArray> {
        val r = FileReceiver(4, "catatan.txt", data.size.toLong())
        assertEquals(FileMsg.Accept(4), r.accept())
        val keluar = ArrayList<Byte>()
        data.toList().chunked(potongan).forEachIndexed { i, bagian ->
            val aksi = r.handle(FileMsg.Chunk(4, i, bagian.toByteArray()))
            assertTrue(aksi is FileAction.Write)
            keluar.addAll((aksi as FileAction.Write).data.toList())
            assertEquals(keluar.size.toLong(), aksi.ack)
        }
        assertEquals(FileAction.Finish, r.handle(FileMsg.Done(4, sha(data))))
        return r to keluar.toByteArray()
    }

    @Test
    fun berkasUtuhSampaiByteDemiByte() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        val (r, hasil) = terima(data)
        assertArrayEquals(data, hasil)
        assertEquals(FileReceiver.Phase.COMPLETE, r.phase())
        assertEquals(100, r.percent())
    }

    @Test
    fun potonganSebesarBatasTetapJalan() {
        val data = ByteArray(FileRules.MAX_CHUNK_BYTES * 2) { 7 }
        val (_, hasil) = terima(data, FileRules.MAX_CHUNK_BYTES)
        assertArrayEquals(data, hasil)
    }

    @Test
    fun namaDibersihkanSebelumDitanyakanKePengguna() {
        val r = FileReceiver(1, "../../etc/passwd", 10)
        assertEquals("passwd", r.name)
        val dos = FileReceiver(1, "CON.txt", 10)
        assertEquals("_CON.txt", dos.name)
    }

    @Test
    fun ekstensiBerbahayaDitandaiTapiTidakDiblokir() {
        val r = FileReceiver(1, "pemasang.exe", 100)
        assertTrue(r.risky)
        assertEquals(FileReceiver.Phase.OFFERED, r.phase())
        assertEquals(FileMsg.Accept(1), r.accept())
    }

    @Test
    fun ukuranNolDanKebesaranDitolakSejakTawaran() {
        val kosong = FileReceiver(1, "a.txt", 0)
        assertEquals(FileReceiver.Phase.FAILED, kosong.phase())
        assertEquals(FileReason.TOO_LARGE, kosong.failReason())
        assertNull(kosong.accept())

        val besar = FileReceiver(1, "a.bin", FileRules.MAX_FILE_BYTES + 1)
        assertEquals(FileReceiver.Phase.FAILED, besar.phase())
    }

    @Test
    fun batasPenerimaSendiriDihormati() {
        val r = FileReceiver(1, "a.bin", 10_000, maxBytes = 5_000)
        assertEquals(FileReceiver.Phase.FAILED, r.phase())
        assertEquals(FileReason.TOO_LARGE, r.failReason())
    }

    @Test
    fun potonganSebelumPersetujuanMembatalkanTransfer() {
        val r = FileReceiver(4, "a.bin", 100)
        val aksi = r.handle(FileMsg.Chunk(4, 0, ByteArray(10)))
        assertEquals(FileAction.Abort(FileReason.PROTOCOL), aksi)
        assertEquals(FileReceiver.Phase.FAILED, r.phase())
    }

    @Test
    fun nomorUrutYangMelompatAtauTerulangDitolak() {
        val lompat = FileReceiver(4, "a.bin", 100)
        lompat.accept()
        assertEquals(FileAction.Abort(FileReason.PROTOCOL), lompat.handle(FileMsg.Chunk(4, 1, ByteArray(10))))

        val ulang = FileReceiver(4, "a.bin", 100)
        ulang.accept()
        ulang.handle(FileMsg.Chunk(4, 0, ByteArray(10)))
        assertEquals(FileAction.Abort(FileReason.PROTOCOL), ulang.handle(FileMsg.Chunk(4, 0, ByteArray(10))))
    }

    @Test
    fun potonganKosongTerlaluBesarAtauMelampauiUkuranDitolak() {
        val kosong = FileReceiver(4, "a.bin", 100).also { it.accept() }
        assertEquals(FileAction.Abort(FileReason.PROTOCOL), kosong.handle(FileMsg.Chunk(4, 0, ByteArray(0))))

        val besar = FileReceiver(4, "a.bin", 1_000_000).also { it.accept() }
        assertEquals(
            FileAction.Abort(FileReason.PROTOCOL),
            besar.handle(FileMsg.Chunk(4, 0, ByteArray(FileRules.MAX_CHUNK_BYTES + 1))),
        )

        val lebih = FileReceiver(4, "a.bin", 100).also { it.accept() }
        assertEquals(FileAction.Abort(FileReason.PROTOCOL), lebih.handle(FileMsg.Chunk(4, 0, ByteArray(101))))
    }

    @Test
    fun doneSebelumSemuaByteTibaDitolak() {
        val r = FileReceiver(4, "a.bin", 100).also { it.accept() }
        r.handle(FileMsg.Chunk(4, 0, ByteArray(10)))
        assertEquals(FileAction.Abort(FileReason.PROTOCOL), r.handle(FileMsg.Done(4, ByteArray(32))))
    }

    @Test
    fun hashYangTidakCocokMembuangBerkas() {
        val data = ByteArray(50) { 3 }
        val r = FileReceiver(4, "a.bin", 50).also { it.accept() }
        r.handle(FileMsg.Chunk(4, 0, data))
        assertEquals(FileAction.Abort(FileReason.HASH_MISMATCH), r.handle(FileMsg.Done(4, ByteArray(32))))
        assertEquals(FileReceiver.Phase.FAILED, r.phase())
    }

    @Test
    fun tawaranKeduaDenganIdSamaDitolak() {
        val r = FileReceiver(4, "a.bin", 100).also { it.accept() }
        assertEquals(FileAction.Abort(FileReason.PROTOCOL), r.handle(FileMsg.Offer(4, 100, "a.bin")))
    }

    @Test
    fun pesanDenganIdLainTidakMenyentuhTransferIni() {
        val r = FileReceiver(4, "a.bin", 100).also { it.accept() }
        assertEquals(FileAction.None, r.handle(FileMsg.Cancel(99, FileReason.USER)))
        assertEquals(FileAction.None, r.handle(FileMsg.Chunk(99, 0, ByteArray(10))))
        assertEquals(FileReceiver.Phase.RECEIVING, r.phase())
        assertEquals(0L, r.received())
    }

    @Test
    fun penolakanPenggunaMenghasilkanRejectSekali() {
        val r = FileReceiver(4, "a.bin", 100)
        assertEquals(FileMsg.Reject(4, FileReason.USER), r.reject())
        assertEquals(FileReceiver.Phase.FAILED, r.phase())
        assertNull(r.accept())
        assertEquals(FileAction.None, r.handle(FileMsg.Chunk(4, 0, ByteArray(10))))
    }

    @Test
    fun sesiPutusMenggagalkanTransferYangBelumSelesai() {
        val r = FileReceiver(4, "a.bin", 100).also { it.accept() }
        r.disconnected()
        assertEquals(FileReason.DISCONNECTED, r.failReason())

        val selesai = FileReceiver(4, "a.bin", 1).also { it.accept() }
        selesai.handle(FileMsg.Chunk(4, 0, ByteArray(1)))
        selesai.handle(FileMsg.Done(4, sha(ByteArray(1))))
        selesai.disconnected()
        assertEquals(FileReceiver.Phase.COMPLETE, selesai.phase())
    }

    @Test
    fun kemajuanTidakPernah100SebelumByteTerakhir() {
        val r = FileReceiver(4, "a.bin", 100).also { it.accept() }
        r.handle(FileMsg.Chunk(4, 0, ByteArray(99)))
        assertEquals(99, r.percent())
        assertFalse(r.phase() == FileReceiver.Phase.COMPLETE)
    }

    /**
     * Dua mesin keadaan Kotlin yang saling bicara: pengirim HP → penerima HP.
     * Aturan kedua sisi ditulis terpisah, jadi hanya uji seperti ini yang
     * membuktikan keduanya menafsirkan protokol dengan cara yang sama.
     */
    @Test
    fun pengirimDanPenerimaSalingBicaraSampaiUtuh() {
        val data = ByteArray(150_000) { (it % 97).toByte() }
        val s = FileSender(21, "video.mp4", data.size.toLong())
        var r: FileReceiver? = null
        val keluar = ArrayList<Byte>()
        var offset = 0
        val digest = MessageDigest.getInstance("SHA-256")

        // Penerima mengirim tanda siap lebih dulu.
        var balasan: FileMsg? = FileMsg.Ack(FileWire.BEACON_ID, 0)
        var putaran = 0
        while (s.active() && putaran++ < 100_000) {
            if (balasan != null) {
                val keluaran = s.onMessage(balasan)
                balasan = null
                if (keluaran is FileMsg.Offer) {
                    val baru = FileReceiver(keluaran.id, keluaran.name, keluaran.size)
                    balasan = baru.accept()
                    r = baru
                }
                continue
            }
            val n = s.allowance()
            if (n > 0) {
                val bagian = data.copyOfRange(offset, offset + n)
                offset += n
                digest.update(bagian)
                val pesan = s.chunk(bagian) as FileMsg.Chunk
                val aksi = r!!.handle(pesan) as FileAction.Write
                keluar.addAll(aksi.data.toList())
                balasan = FileMsg.Ack(21, aksi.ack)
                continue
            }
            if (s.ready()) {
                val done = s.finish(digest.digest()) as FileMsg.Done
                assertEquals(FileAction.Finish, r!!.handle(done))
                break
            }
            throw AssertionError("buntu: tidak ada yang boleh dikirim dan belum selesai")
        }
        assertEquals(FileSender.State.DONE, s.state())
        assertArrayEquals(data, keluar.toByteArray())
        assertEquals(100, s.percent())
    }
    /**
     * Satu channel dipakai dua arah, jadi konfirmasi milik arah sebaliknya
     * ikut lewat di depan penerima. Itu bukan pelanggaran protokol dan tidak
     * boleh menggugurkan transfer yang sedang berjalan.
     */
    @Test
    fun penerimaMembiarkanDoneOkLewat() {
        val r = FileReceiver(5, "a.bin", 10)
        r.accept()
        assertEquals(FileAction.None, r.handle(FileMsg.DoneOk(5)))
        assertEquals(FileAction.None, r.handle(FileMsg.DoneOk(99)))
        assertEquals(FileReceiver.Phase.RECEIVING, r.phase())
        assertEquals(-1, r.failReason())
    }
}
