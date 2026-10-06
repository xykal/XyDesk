package id.xyverse.xyadapt

import java.security.MessageDigest

/**
 * Sisi **penerima** di HP: berkas yang dikirim dari PC.
 *
 * Cermin dari `Receiver` di `host/src/filetransfer.rs`, dan pasangan dari
 * [FileSender] di berkas sebelah. Sama seperti di host, ia tidak menyentuh
 * penyimpanan dan tidak menyentuh jaringan: ia hanya memutuskan. Penulisan
 * dilakukan pemanggil setelah [FileAction.Write].
 *
 * ## Kenapa seketat ini
 *
 * Arah PC → HP berarti PC yang memilih nama, ukuran, dan urutan — dan HP
 * yang menulis ke penyimpanan pribadinya. Setiap aturan di sini menutup satu
 * cara aliran yang menyimpang bisa menghasilkan berkas yang bukan berkas
 * yang disetujui pengguna.
 */
class FileReceiver(
    val id: Int,
    rawName: String,
    val declared: Long,
    maxBytes: Long = FileRules.MAX_FILE_BYTES,
) {
    enum class Phase { OFFERED, RECEIVING, COMPLETE, FAILED }

    /** Nama sudah bersih sejak sekarang: tidak ada jalur yang memegang nama mentah. */
    val name: String = FileRules.sanitizeName(rawName)

    /** Berkas yang langsung dijalankan sistem — untuk diperingatkan, bukan diblokir. */
    val risky: Boolean = FileRules.isRiskyExtension(name)

    private val limit = minOf(maxBytes, FileRules.MAX_FILE_BYTES)
    private var phase = if (declared <= 0 || declared > limit) Phase.FAILED else Phase.OFFERED
    private var reason = if (phase == Phase.FAILED) FileReason.TOO_LARGE else -1
    private var received = 0L
    private var nextSeq = 0
    private val hasher = MessageDigest.getInstance("SHA-256")

    fun phase(): Phase = phase
    fun received(): Long = received
    fun failReason(): Int = reason
    fun percent(): Int = FileRules.percent(received, declared)

    /** Pengguna menyetujui; `null` bila tawaran sudah tidak berlaku. */
    fun accept(): FileMsg? {
        if (phase != Phase.OFFERED) return null
        phase = Phase.RECEIVING
        return FileMsg.Accept(id)
    }

    /** Pengguna menolak. */
    fun reject(code: Int = FileReason.USER): FileMsg {
        phase = Phase.FAILED
        reason = code
        return FileMsg.Reject(id, code)
    }

    /** Sesi berakhir sebelum berkas utuh. */
    fun disconnected() {
        if (phase != Phase.COMPLETE) {
            phase = Phase.FAILED
            reason = FileReason.DISCONNECTED
        }
    }

    /** Menyuapkan satu pesan yang sudah didekode. */
    fun handle(m: FileMsg): FileAction {
        val other = when (m) {
            is FileMsg.Offer -> m.id
            is FileMsg.Accept -> m.id
            is FileMsg.Reject -> m.id
            is FileMsg.Chunk -> m.id
            is FileMsg.Done -> m.id
            is FileMsg.Cancel -> m.id
            is FileMsg.Ack -> m.id
            is FileMsg.DoneOk -> m.id
        }
        // Pesan untuk transfer lain tidak boleh menyentuh keadaan ini: tanpa
        // penjagaan ini, pengirim bisa membatalkan transfer berjalan dengan
        // menyebut id yang salah.
        if (other != id) return FileAction.None
        if (phase == Phase.FAILED || phase == Phase.COMPLETE) return FileAction.None
        return when (m) {
            is FileMsg.Cancel -> {
                phase = Phase.FAILED
                reason = m.reason
                FileAction.None
            }
            is FileMsg.Chunk -> {
                if (phase != Phase.RECEIVING) return fail(FileReason.PROTOCOL)
                if (m.seq != nextSeq || m.data.isEmpty() ||
                    m.data.size > FileRules.MAX_CHUNK_BYTES ||
                    received + m.data.size > declared
                ) {
                    return fail(FileReason.PROTOCOL)
                }
                hasher.update(m.data)
                received += m.data.size
                nextSeq++
                FileAction.Write(m.data, received)
            }
            is FileMsg.Done -> {
                if (phase != Phase.RECEIVING || received != declared) return fail(FileReason.PROTOCOL)
                if (!hasher.digest().contentEquals(m.sha256)) return fail(FileReason.HASH_MISMATCH)
                phase = Phase.COMPLETE
                FileAction.Finish
            }
            // OFFER kedua dengan id yang sama bukan percobaan ulang yang sah:
            // id dipilih pengirim dan harus baru setiap berkas.
            is FileMsg.Offer -> fail(FileReason.PROTOCOL)
            // Konfirmasi milik pengirim di arah sebaliknya; satu channel
            // dipakai dua arah, jadi ia boleh lewat tanpa dianggap
            // pelanggaran — dan tanpa mengubah apa pun di sini.
            is FileMsg.DoneOk -> FileAction.None
            // ACCEPT/REJECT/ACK adalah pesan untuk pengirim.
            else -> fail(FileReason.PROTOCOL)
        }
    }

    private fun fail(code: Int): FileAction {
        phase = Phase.FAILED
        reason = code
        return FileAction.Abort(code)
    }
}

/** Apa yang harus dilakukan pemanggil setelah satu pesan disuapkan. */
sealed class FileAction {
    /** Tidak ada yang perlu dikirim. */
    object None : FileAction()

    /** Tulis byte ini, lalu kirim `ACK` dengan [ack]. */
    data class Write(val data: ByteArray, val ack: Long) : FileAction() {
        override fun equals(other: Any?): Boolean =
            other is Write && ack == other.ack && data.contentEquals(other.data)

        override fun hashCode(): Int = ack.hashCode() * 31 + data.contentHashCode()
    }

    /**
     * Berkas utuh dan terverifikasi; pindahkan dari berkas sementara, lalu
     * kirim [FileMsg.DoneOk] — **setelah** pemindahan berhasil, bukan
     * sebelumnya. Konfirmasi yang dikirim lebih awal hanya mengulang apa
     * yang sudah diketahui pengirim.
     */
    object Finish : FileAction()

    /** Hentikan dan kirim `CANCEL` dengan alasan ini. */
    data class Abort(val reason: Int) : FileAction()
}
