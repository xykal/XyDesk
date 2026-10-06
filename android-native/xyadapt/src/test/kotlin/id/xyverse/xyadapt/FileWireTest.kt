package id.xyverse.xyadapt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileWireTest {
    private fun roundTrip(m: FileMsg) = assertEquals(m, FileWire.decode(FileWire.encode(m)))

    @Test
    fun setiapPesanBolakBalikUtuh() {
        roundTrip(FileMsg.Offer(7, 123_456_789L, "laporan tahunan.pdf"))
        roundTrip(FileMsg.Accept(7))
        roundTrip(FileMsg.Reject(7, FileReason.TOO_LARGE))
        roundTrip(FileMsg.Chunk(7, 42, ByteArray(100) { it.toByte() }))
        roundTrip(FileMsg.Done(7, ByteArray(32) { (it * 7).toByte() }))
        roundTrip(FileMsg.Cancel(7, FileReason.USER))
        roundTrip(FileMsg.Ack(7, 65_536L))
    }

    @Test
    fun tataLetakByteSamaDenganHost() {
        val b = FileWire.encode(FileMsg.Offer(1, 2, "ab"))
        // 0x01, id LE, size LE, name_len LE, nama
        assertEquals(listOf(1, 1, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 2, 0, 'a'.code, 'b'.code),
            b.map { it.toInt() and 0xFF })
        val ack = FileWire.encode(FileMsg.Ack(0, 0))
        assertEquals(13, ack.size)
        assertEquals(0x07, ack[0].toInt())
    }

    @Test
    fun angkaBesarTidakRusakSaatDikodekan() {
        val m = FileMsg.Offer(-1 ushr 1, FileRules.MAX_FILE_BYTES, "besar.bin")
        roundTrip(m)
        val ack = FileMsg.Ack(0x7FFFFFFF, 4L * 1024 * 1024 * 1024)
        roundTrip(ack)
    }

    @Test
    fun pesanCacatDitolakBukanDiabaikan() {
        assertNull(FileWire.decode(ByteArray(0)))
        assertNull(FileWire.decode(byteArrayOf(0x09, 0, 0, 0, 0)))          // tipe tak dikenal
        assertNull(FileWire.decode(byteArrayOf(0x02, 0, 0, 0)))              // ACCEPT kependekan
        assertNull(FileWire.decode(byteArrayOf(0x02, 0, 0, 0, 0, 0)))        // ACCEPT kepanjangan
        assertNull(FileWire.decode(byteArrayOf(0x05, 1, 0, 0, 0) + ByteArray(31))) // DONE 31 byte
        // OFFER dengan name_len yang tidak cocok isinya.
        val cacat = FileWire.encode(FileMsg.Offer(1, 10, "halo")).copyOf(16)
        assertNull(FileWire.decode(cacat))
    }

    @Test
    fun namaUnicodeBertahan() {
        val m = FileMsg.Offer(3, 99, "berkas ⚡ café 日本.txt")
        roundTrip(m)
    }

    @Test
    fun tandaSiapDikenali() {
        assertTrue(FileWire.isBeacon(FileMsg.Ack(0, 0)))
        assertFalse(FileWire.isBeacon(FileMsg.Ack(5, 0)))
        assertFalse(FileWire.isBeacon(FileMsg.Accept(0)))
    }
    @Test
    fun doneOkBolakBalikDiKawat() {
        val pesan = FileMsg.DoneOk(0x0A0B0C0D)
        val b = FileWire.encode(pesan)
        assertEquals(FileWire.MSG_DONE_OK, b[0].toInt() and 0xFF)
        assertEquals(5, b.size)
        assertEquals(pesan, FileWire.decode(b))
        assertNull(FileWire.decode(byteArrayOf(FileWire.MSG_DONE_OK.toByte())))
        assertNull(FileWire.decode(byteArrayOf(FileWire.MSG_DONE_OK.toByte(), 1, 2, 3)))
        assertNull(FileWire.decode(byteArrayOf(FileWire.MSG_DONE_OK.toByte(), 1, 2, 3, 4, 5)))
    }

}

class FileSenderTest {
    @Test
    fun potonganKirimBawaanMuatDalamSatuPesanSctp() {
        // 9 byte header CHUNK + isi harus tetap di bawah 64 KiB, kalau tidak
        // pesannya tidak pernah berangkat dan pengiriman menggantung diam.
        assertTrue(FileRules.SEND_CHUNK_BYTES + 9 <= 64 * 1024)
        val s = FileSender(1, "a.bin", 1_000_000L)
        s.onMessage(FileMsg.Ack(0, 0))
        s.onMessage(FileMsg.Accept(1))
        assertEquals(FileRules.SEND_CHUNK_BYTES, s.allowance())
    }

    private fun sender(size: Long = 200_000L, chunk: Int = 65_536) =
        FileSender(9, "foto liburan.jpg", size, chunk)

    private fun sampaiMengirim(s: FileSender) {
        s.onMessage(FileMsg.Ack(0, 0))
        s.onMessage(FileMsg.Accept(9))
    }

    @Test
    fun offerHanyaKeluarSetelahTandaSiap() {
        val s = sender()
        // Tanpa tanda siap belum ada apa pun yang boleh dikirim.
        assertEquals(FileSender.State.WAIT_READY, s.state())
        assertEquals(0, s.allowance())
        val offer = s.onMessage(FileMsg.Ack(0, 0))
        assertEquals(FileMsg.Offer(9, 200_000L, "foto liburan.jpg"), offer)
        assertEquals(FileSender.State.OFFERED, s.state())
        // Potongan sebelum ACCEPT tetap tidak boleh.
        assertEquals(0, s.allowance())
        assertNull(s.chunk(ByteArray(10)))
    }

    @Test
    fun namaDibersihkanSebelumDitawarkan() {
        val s = FileSender(1, "..\\..\\Windows\\System32\\drivers\\etc\\hosts", 10)
        assertEquals("hosts", s.name)
        val offer = s.onMessage(FileMsg.Ack(0, 0)) as FileMsg.Offer
        assertEquals("hosts", offer.name)
    }

    @Test
    fun berkasKosongDitolakSebelumJaringanTersentuh() {
        val s = FileSender(1, "kosong.txt", 0)
        assertNull(s.onMessage(FileMsg.Ack(0, 0)))
        assertEquals(FileSender.State.FAILED, s.state())
        assertEquals(FileReason.TOO_LARGE, s.failReason())
    }

    @Test
    fun seluruhBerkasBerpindahDenganNomorUrutBerurutan() {
        val s = sender(size = 150_000L)
        sampaiMengirim(s)
        var seqHarap = 0
        var total = 0L
        while (s.allowance() > 0) {
            val n = s.allowance()
            val c = s.chunk(ByteArray(n)) as FileMsg.Chunk
            assertEquals(seqHarap++, c.seq)
            total += n
            s.onMessage(FileMsg.Ack(9, total))
        }
        assertEquals(150_000L, total)
        assertTrue(s.ready())
        assertEquals(100, s.percent())
        val done = s.finish(ByteArray(32)) as FileMsg.Done
        assertEquals(9, done.id)
        assertEquals(FileSender.State.DONE, s.state())
    }

    @Test
    fun jendelaMenahanLajuSampaiAckDatang() {
        val s = sender(size = 10L * 1024 * 1024)
        sampaiMengirim(s)
        var terkirim = 0L
        while (s.allowance() > 0) {
            terkirim += s.chunk(ByteArray(s.allowance()))!!.let { (it as FileMsg.Chunk).data.size }
        }
        // Tanpa satu pun ACK, yang boleh keluar persis sebesar jendela.
        assertEquals(FileSender.WINDOW_BYTES, terkirim)
        assertEquals(0, s.percent())
        // ACK membuka jendela lagi.
        s.onMessage(FileMsg.Ack(9, FileSender.WINDOW_BYTES))
        assertTrue(s.allowance() > 0)
        assertEquals(5, s.percent())
    }

    @Test
    fun kemajuanDihitungDariAckBukanDariByteYangKeluar() {
        val s = sender(size = 100_000L)
        sampaiMengirim(s)
        s.chunk(ByteArray(65_536))
        assertEquals(65_536L, s.sentBytes())
        assertEquals(0, s.percent())
        s.onMessage(FileMsg.Ack(9, 65_536L))
        assertEquals(65, s.percent())
    }

    @Test
    fun ackMundurAtauMelampauiUkuranMenggagalkanTransfer() {
        val s = sender(size = 100_000L)
        sampaiMengirim(s)
        s.onMessage(FileMsg.Ack(9, 50_000L))
        s.onMessage(FileMsg.Ack(9, 40_000L))
        assertEquals(FileSender.State.FAILED, s.state())
        assertEquals(FileReason.PROTOCOL, s.failReason())

        val t = sender(size = 100_000L)
        sampaiMengirim(t)
        t.onMessage(FileMsg.Ack(9, 100_001L))
        assertEquals(FileSender.State.FAILED, t.state())
    }

    @Test
    fun pesanUntukTransferLainTidakMenyentuhKeadaan() {
        val s = sender()
        sampaiMengirim(s)
        s.onMessage(FileMsg.Cancel(1234, FileReason.USER))
        s.onMessage(FileMsg.Ack(1234, 999_999L))
        assertEquals(FileSender.State.SENDING, s.state())
        assertEquals(0L, s.ackedBytes())
    }

    @Test
    fun penolakanDanPembatalanHostMenghentikanPengiriman() {
        val tolak = sender()
        tolak.onMessage(FileMsg.Ack(0, 0))
        tolak.onMessage(FileMsg.Reject(9, FileReason.USER))
        assertEquals(FileSender.State.FAILED, tolak.state())
        assertEquals(0, tolak.allowance())

        val batal = sender()
        sampaiMengirim(batal)
        batal.onMessage(FileMsg.Cancel(9, FileReason.HASH_MISMATCH))
        assertEquals(FileReason.HASH_MISMATCH, batal.failReason())
        assertFalse(batal.active())
        assertNull(batal.chunk(ByteArray(10)))
    }

    @Test
    fun pesanArahTerbalikDariHostDianggapPelanggaran() {
        val s = sender()
        sampaiMengirim(s)
        s.onMessage(FileMsg.Offer(9, 10, "aneh.txt"))
        assertEquals(FileReason.PROTOCOL, s.failReason())
    }

    @Test
    fun potonganTidakBolehMelampauiUkuranYangDijanjikan() {
        val s = sender(size = 1000L)
        sampaiMengirim(s)
        assertNotNull(s.chunk(ByteArray(1000)))
        assertNull(s.chunk(ByteArray(1)))
        assertEquals(FileSender.State.FINISHING, s.state())
    }

    @Test
    fun potonganKosongAtauTerlaluBesarDitolak() {
        val s = sender(size = 1_000_000L)
        sampaiMengirim(s)
        assertNull(s.chunk(ByteArray(0)))
        assertEquals(FileReason.PROTOCOL, s.failReason())

        val t = sender(size = 1_000_000L)
        sampaiMengirim(t)
        assertNull(t.chunk(ByteArray(FileRules.MAX_CHUNK_BYTES + 1)))
        assertEquals(FileReason.PROTOCOL, t.failReason())
    }

    @Test
    fun doneDitolakSebelumSemuaByteKeluar() {
        val s = sender(size = 100_000L)
        sampaiMengirim(s)
        s.chunk(ByteArray(10))
        assertNull(s.finish(ByteArray(32)))
        assertEquals(FileSender.State.SENDING, s.state())
    }

    @Test
    fun pembatalanDariSisiKitaMengirimCancelSekali() {
        val s = sender()
        sampaiMengirim(s)
        val c = s.cancel() as FileMsg.Cancel
        assertEquals(FileReason.USER, c.reason)
        assertEquals(9, c.id)
        assertNull(s.cancel())
        assertEquals(0, s.allowance())
    }

    @Test
    fun idBaruTidakPernahNol() {
        assertEquals(1, FileSender.newId { 0 })
        assertEquals(1, FileSender.newId { Int.MIN_VALUE })
        assertTrue(FileSender.newId { -5 } > 0)
    }
    /** Satu berkas kecil dikirim sampai DONE, tanpa konfirmasi. */
    private fun sampaiDone(s: FileSender) {
        sampaiMengirim(s)
        var kirim = 0L
        while (s.state() == FileSender.State.SENDING) {
            val allow = s.allowance()
            if (allow <= 0) break
            s.chunk(ByteArray(allow))
            kirim += allow
            s.onMessage(FileMsg.Ack(9, kirim))
        }
        s.finish(ByteArray(32))
    }

    @Test
    fun doneOkMengonfirmasiPengirim() {
        val s = sender(size = 1000L, chunk = 1000)
        sampaiDone(s)
        assertEquals(FileSender.State.DONE, s.state())
        assertFalse(s.confirmed())
        assertNull(s.onMessage(FileMsg.DoneOk(9)))
        assertTrue(s.confirmed())
        assertEquals(FileSender.State.CONFIRMED, s.state())
        assertFalse(s.active())
        assertEquals(100, s.percent())
    }

    @Test
    fun doneOkAsingAtauTerlaluCepatDiabaikan() {
        val s = sender(size = 1000L, chunk = 1000)
        s.onMessage(FileMsg.DoneOk(9))
        assertFalse("konfirmasi sebelum OFFER tidak sah", s.confirmed())
        sampaiMengirim(s)
        s.chunk(ByteArray(1000))
        s.onMessage(FileMsg.Ack(9, 1000))
        s.onMessage(FileMsg.DoneOk(9))
        assertFalse("konfirmasi sebelum DONE tidak sah", s.confirmed())
        s.finish(ByteArray(32))
        s.onMessage(FileMsg.DoneOk(8))
        assertFalse("konfirmasi untuk id lain bukan milik kita", s.confirmed())
        s.onMessage(FileMsg.DoneOk(9))
        assertTrue(s.confirmed())
    }

    /** Host lama diam saat berhasil; itu tetap kiriman yang selesai. */
    @Test
    fun tanpaDoneOkPengirimTetapSelesai() {
        val s = sender(size = 1000L, chunk = 1000)
        sampaiDone(s)
        assertEquals(FileSender.State.DONE, s.state())
        assertFalse(s.active())
        assertFalse(s.confirmed())
        assertEquals(100, s.percent())
        assertEquals(-1, s.failReason())
    }

    /** Yang sudah dikonfirmasi tersimpan tidak bisa digagalkan belakangan. */
    @Test
    fun setelahDikonfirmasiPembatalanTidakMengubahApaPun() {
        val s = sender(size = 1000L, chunk = 1000)
        sampaiDone(s)
        s.onMessage(FileMsg.DoneOk(9))
        assertNull(s.cancel())
        s.onMessage(FileMsg.Cancel(9, FileReason.IO))
        assertEquals(FileSender.State.CONFIRMED, s.state())
        assertTrue(s.confirmed())
    }
}
