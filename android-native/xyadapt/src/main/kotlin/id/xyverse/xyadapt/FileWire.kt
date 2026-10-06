package id.xyverse.xyadapt

/**
 * Sisi **pengirim** transfer berkas: pengkodean pesan channel `"file"` dan
 * mesin keadaannya. Cerminan `host/src/filetransfer.rs`, yang memegang sisi
 * penerima.
 *
 * ```
 * 0x01 OFFER  id:u32  size:u64  name_len:u16  name:utf8
 * 0x02 ACCEPT id:u32
 * 0x03 REJECT id:u32  reason:u8
 * 0x04 CHUNK  id:u32  seq:u32  bytes (sisa pesan)
 * 0x05 DONE   id:u32  sha256:32 byte
 * 0x06 CANCEL id:u32  reason:u8
 * 0x07 ACK    id:u32  received:u64
 * ```
 *
 * Semua bilangan little-endian. Tidak ada I/O di berkas ini: byte berkasnya
 * dibaca pemanggil, di sini hanya diputuskan **boleh atau tidak** dan
 * **berapa banyak** — supaya seluruh aturannya bisa diuji tanpa perangkat,
 * tanpa izin penyimpanan, dan tanpa sesi WebRTC.
 */
object FileWire {
    const val MSG_OFFER = 0x01
    const val MSG_ACCEPT = 0x02
    const val MSG_REJECT = 0x03
    const val MSG_CHUNK = 0x04
    const val MSG_DONE = 0x05
    const val MSG_CANCEL = 0x06
    const val MSG_ACK = 0x07

    const val CHANNEL = "file"

    /** Id yang tidak pernah dipakai transfer sungguhan; `ACK` dengannya = penerima siap. */
    const val BEACON_ID = 0

    fun encode(m: FileMsg): ByteArray = when (m) {
        is FileMsg.Offer -> {
            val raw = m.name.toByteArray(Charsets.UTF_8)
            val out = ByteArray(1 + 4 + 8 + 2 + raw.size)
            out[0] = MSG_OFFER.toByte()
            putI32(out, 1, m.id)
            putI64(out, 5, m.size)
            out[13] = (raw.size and 0xFF).toByte()
            out[14] = ((raw.size ushr 8) and 0xFF).toByte()
            raw.copyInto(out, 15)
            out
        }
        is FileMsg.Accept -> ByteArray(5).also { it[0] = MSG_ACCEPT.toByte(); putI32(it, 1, m.id) }
        is FileMsg.Reject -> ByteArray(6).also {
            it[0] = MSG_REJECT.toByte(); putI32(it, 1, m.id); it[5] = m.reason.toByte()
        }
        is FileMsg.Chunk -> {
            val out = ByteArray(9 + m.data.size)
            out[0] = MSG_CHUNK.toByte()
            putI32(out, 1, m.id)
            putI32(out, 5, m.seq)
            m.data.copyInto(out, 9)
            out
        }
        is FileMsg.Done -> {
            require(m.sha256.size == 32) { "sha256 wajib 32 byte" }
            val out = ByteArray(37)
            out[0] = MSG_DONE.toByte()
            putI32(out, 1, m.id)
            m.sha256.copyInto(out, 5)
            out
        }
        is FileMsg.Cancel -> ByteArray(6).also {
            it[0] = MSG_CANCEL.toByte(); putI32(it, 1, m.id); it[5] = m.reason.toByte()
        }
        is FileMsg.Ack -> ByteArray(13).also {
            it[0] = MSG_ACK.toByte(); putI32(it, 1, m.id); putI64(it, 5, m.received)
        }
    }

    /**
     * `null` berarti pesan cacat — terlalu pendek, tipe tak dikenal, atau
     * panjang yang tidak cocok dengan isinya. Sama ketatnya dengan host:
     * pesan cacat adalah pelanggaran protokol, bukan sesuatu yang diabaikan.
     */
    fun decode(b: ByteArray): FileMsg? {
        if (b.isEmpty()) return null
        val rest = b.size - 1
        fun id() = i32(b, 1)
        return when (b[0].toInt() and 0xFF) {
            MSG_OFFER -> {
                if (rest < 14) return null
                val size = i64(b, 5)
                val nameLen = (b[13].toInt() and 0xFF) or ((b[14].toInt() and 0xFF) shl 8)
                if (rest != 14 + nameLen) return null
                val raw = b.copyOfRange(15, 15 + nameLen)
                val name = runCatching { decodeUtf8Strict(raw) }.getOrNull() ?: return null
                FileMsg.Offer(id(), size, name)
            }
            MSG_ACCEPT -> if (rest != 4) null else FileMsg.Accept(id())
            MSG_REJECT -> if (rest != 5) null else FileMsg.Reject(id(), b[5].toInt() and 0xFF)
            MSG_CANCEL -> if (rest != 5) null else FileMsg.Cancel(id(), b[5].toInt() and 0xFF)
            MSG_CHUNK -> if (rest < 8) null else FileMsg.Chunk(id(), i32(b, 5), b.copyOfRange(9, b.size))
            MSG_DONE -> if (rest != 36) null else FileMsg.Done(id(), b.copyOfRange(5, 37))
            MSG_ACK -> if (rest != 12) null else FileMsg.Ack(id(), i64(b, 5))
            else -> null
        }
    }

    /** Apakah pesan ini tanda "penerima siap" (ACK id 0). */
    fun isBeacon(m: FileMsg): Boolean = m is FileMsg.Ack && m.id == BEACON_ID

    private fun decodeUtf8Strict(raw: ByteArray): String {
        val s = String(raw, Charsets.UTF_8)
        // Byte tak sah menjadi U+FFFD; nama yang berubah bukan nama yang sama.
        if (s.contains('\uFFFD') && !raw.contains(0xEF.toByte())) throw IllegalArgumentException("bukan UTF-8")
        return s
    }

    private fun putI32(out: ByteArray, at: Int, v: Int) {
        out[at] = (v and 0xFF).toByte()
        out[at + 1] = ((v ushr 8) and 0xFF).toByte()
        out[at + 2] = ((v ushr 16) and 0xFF).toByte()
        out[at + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun putI64(out: ByteArray, at: Int, v: Long) {
        for (i in 0..7) out[at + i] = ((v ushr (8 * i)) and 0xFF).toByte()
    }

    private fun i32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

    private fun i64(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
        return v
    }
}

/** Alasan penolakan/pembatalan; kodenya sama persis dengan `Reason` di host. */
object FileReason {
    const val USER = 0
    const val TOO_LARGE = 1
    const val PROTOCOL = 2
    const val HASH_MISMATCH = 3
    const val IO = 4
    const val DISCONNECTED = 5

    fun label(code: Int): String = when (code) {
        USER -> "ditolak di PC"
        TOO_LARGE -> "berkas terlalu besar untuk PC"
        PROTOCOL -> "aliran tidak sesuai protokol"
        HASH_MISMATCH -> "isi berkas tidak cocok, transfer dibuang"
        IO -> "PC gagal menulis ke disk"
        DISCONNECTED -> "sesi terputus di tengah transfer"
        else -> "alasan tidak dikenal ($code)"
    }
}

sealed class FileMsg {
    data class Offer(val id: Int, val size: Long, val name: String) : FileMsg()
    data class Accept(val id: Int) : FileMsg()
    data class Reject(val id: Int, val reason: Int) : FileMsg()
    data class Chunk(val id: Int, val seq: Int, val data: ByteArray) : FileMsg() {
        override fun equals(other: Any?): Boolean =
            other is Chunk && id == other.id && seq == other.seq && data.contentEquals(other.data)

        override fun hashCode(): Int = (id * 31 + seq) * 31 + data.contentHashCode()
    }

    data class Done(val id: Int, val sha256: ByteArray) : FileMsg() {
        override fun equals(other: Any?): Boolean =
            other is Done && id == other.id && sha256.contentEquals(other.sha256)

        override fun hashCode(): Int = id * 31 + sha256.contentHashCode()
    }

    data class Cancel(val id: Int, val reason: Int) : FileMsg()
    data class Ack(val id: Int, val received: Long) : FileMsg()
}

/**
 * Mesin keadaan pengirim.
 *
 * Urutannya: tunggu tanda siap → `OFFER` → tunggu `ACCEPT` → potongan →
 * `DONE`. Tiga hal yang membuatnya tidak sekadar perulangan:
 *
 * - **Tanda siap wajib ditunggu.** Channel sudah `OPEN` di sisi kita
 *   beberapa saat sebelum host sempat memasang pendengarnya; `OFFER` yang
 *   tiba di celah itu hilang tanpa jejak dan transfer menggantung tanpa satu
 *   pun pesan kesalahan. Host mengirim `ACK` dengan id 0 setelah siap.
 * - **Laju ditahan oleh ACK, bukan oleh buffer sendiri.** Tanpa jendela,
 *   `CHUNK` masuk antrean WebRTC secepat berkas bisa dibaca; memori pecah
 *   pada berkas besar, dan "100%" muncul saat byte baru keluar dari HP, bukan
 *   saat sampai di disk PC. Jendela di sini dihitung dari byte yang **sudah
 *   di-ACK** host.
 * - **Kemajuan dilaporkan dari ACK.** Angka yang ditunjukkan ke pengguna
 *   adalah byte yang benar-benar tertulis di PC.
 */
class FileSender(
    val id: Int,
    rawName: String,
    val size: Long,
    private val chunkSize: Int = FileRules.SEND_CHUNK_BYTES,
) {
    enum class State { WAIT_READY, OFFERED, SENDING, FINISHING, DONE, FAILED }

    /** Nama yang dibersihkan — yang dilihat pengguna harus yang ditulis host. */
    val name: String = FileRules.sanitizeName(rawName)

    private var state = State.WAIT_READY
    private var sent = 0L
    private var acked = 0L
    private var seq = 0
    private var reason = -1

    fun state(): State = state
    fun sentBytes(): Long = sent
    fun ackedBytes(): Long = acked
    fun failReason(): Int = reason
    fun active(): Boolean = state != State.DONE && state != State.FAILED

    /** Kemajuan yang ditunjukkan ke pengguna: byte yang sampai di disk PC. */
    fun percent(): Int = FileRules.percent(acked, size)

    /**
     * Pesan yang perlu dikirim sekarang, atau `null`. Dipanggil setelah
     * [onMessage] dan di awal; `OFFER` baru keluar setelah tanda siap.
     */
    fun pending(): FileMsg? = null

    /**
     * Menyuapkan satu pesan dari host. Mengembalikan pesan balasan yang
     * harus dikirim (hanya `OFFER` saat tanda siap tiba), atau `null`.
     */
    fun onMessage(m: FileMsg): FileMsg? {
        if (!active()) return null
        if (FileWire.isBeacon(m)) {
            if (state != State.WAIT_READY) return null
            if (!FileRules.acceptableSize(size)) {
                fail(FileReason.TOO_LARGE)
                return null
            }
            state = State.OFFERED
            return FileMsg.Offer(id, size, name)
        }
        // Pesan untuk transfer lain tidak boleh menyentuh keadaan ini.
        val other = when (m) {
            is FileMsg.Offer -> m.id
            is FileMsg.Accept -> m.id
            is FileMsg.Reject -> m.id
            is FileMsg.Chunk -> m.id
            is FileMsg.Done -> m.id
            is FileMsg.Cancel -> m.id
            is FileMsg.Ack -> m.id
        }
        if (other != id) return null
        when (m) {
            is FileMsg.Accept -> if (state == State.OFFERED) state = State.SENDING
            is FileMsg.Reject -> fail(m.reason)
            is FileMsg.Cancel -> fail(m.reason)
            is FileMsg.Ack -> {
                // ACK mundur atau melampaui ukuran berarti ada yang salah
                // membaca aliran; lebih baik berhenti daripada menampilkan
                // kemajuan yang dikarang.
                if (m.received < acked || m.received > size) fail(FileReason.PROTOCOL) else acked = m.received
            }
            // OFFER/CHUNK/DONE adalah pesan untuk penerima; menerimanya di
            // sini berarti ada yang salah membaca arah.
            else -> fail(FileReason.PROTOCOL)
        }
        return null
    }

    /**
     * Berapa byte yang boleh dibaca dan dikirim sekarang. Nol berarti
     * tunggu: jendela penuh, belum diterima, atau sudah habis.
     */
    fun allowance(): Int {
        if (state != State.SENDING) return 0
        val remaining = size - sent
        if (remaining <= 0) return 0
        val inFlight = sent - acked
        val room = WINDOW_BYTES - inFlight
        if (room < chunkSize && remaining > room) return 0
        return minOf(remaining, chunkSize.toLong(), maxOf(room, 0L)).toInt()
    }

    /** Membungkus potongan yang baru dibaca; `null` bila tidak boleh dikirim. */
    fun chunk(data: ByteArray): FileMsg? {
        if (state != State.SENDING) return null
        if (data.isEmpty() || data.size > chunkSize) {
            fail(FileReason.PROTOCOL)
            return null
        }
        if (sent + data.size > size) {
            fail(FileReason.PROTOCOL)
            return null
        }
        val m = FileMsg.Chunk(id, seq, data)
        seq++
        sent += data.size
        if (sent == size) state = State.FINISHING
        return m
    }

    /** Semua byte sudah keluar; tinggal `DONE`. */
    fun ready(): Boolean = state == State.FINISHING

    /**
     * `DONE` dengan SHA-256 berkas. Host diam bila berkasnya tersimpan dan
     * mengirim `CANCEL` bila hashnya tidak cocok — jadi keadaan di sini
     * menjadi DONE, dan pembatalan yang datang kemudian tetap membuatnya
     * gagal.
     */
    fun finish(sha256: ByteArray): FileMsg? {
        if (state != State.FINISHING) return null
        if (sha256.size != 32) {
            fail(FileReason.IO)
            return null
        }
        state = State.DONE
        return FileMsg.Done(id, sha256)
    }

    /** Dibatalkan dari sisi kita (pengguna menekan batal, sesi putus, baca gagal). */
    fun cancel(reason: Int = FileReason.USER): FileMsg? {
        if (!active()) return null
        fail(reason)
        return FileMsg.Cancel(id, reason)
    }

    private fun fail(code: Int) {
        if (state == State.DONE && code == FileReason.USER) return
        state = State.FAILED
        reason = code
    }

    companion object {
        /** Byte yang boleh "di udara" sebelum menunggu ACK. */
        const val WINDOW_BYTES = 512L * 1024

        /** Id transfer baru: acak, tidak pernah 0 (0 dipakai tanda siap). */
        fun newId(random: () -> Int = { (Math.random() * Int.MAX_VALUE).toInt() }): Int {
            val v = random() and 0x7FFFFFFF
            return if (v == FileWire.BEACON_ID) 1 else v
        }
    }
}
