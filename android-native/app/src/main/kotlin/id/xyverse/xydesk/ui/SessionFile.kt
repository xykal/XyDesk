package id.xyverse.xydesk.ui

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import id.xyverse.xyadapt.FileMsg
import id.xyverse.xyadapt.FileReason
import id.xyverse.xyadapt.FileRules
import id.xyverse.xyadapt.FileSender
import id.xyverse.xyadapt.FileWire
import java.security.MessageDigest

/**
 * Pengirim berkas HP → PC: menyambungkan mesin keadaan murni ([FileSender])
 * dengan `ContentResolver` dan data channel `"file"`.
 *
 * Bagian yang bisa salah sendiri — urutan pesan, jendela laju, kemajuan,
 * pembersihan nama — ada di `xyadapt` dan sudah diuji di JVM. Yang tersisa
 * di sini hanyalah membaca byte, menghitung SHA-256, dan menahan diri saat
 * antrean channel sudah panjang.
 */
class SessionFile(
    private val ctx: Context,
    private val send: (ByteArray) -> Boolean,
    private val buffered: () -> Long,
    private val onState: (Status) -> Unit,
) {
    data class Status(
        val active: Boolean = false,
        val name: String = "",
        val percent: Int = 0,
        val text: String = "",
        val failed: Boolean = false,
    )

    private val ui = Handler(Looper.getMainLooper())

    @Volatile private var sender: FileSender? = null
    @Volatile private var worker: Thread? = null

    /**
     * Tanda siap bisa tiba jauh sebelum pengguna memilih berkas, jadi ia
     * diingat. Tanpa ini transfer pertama menggantung selamanya menunggu
     * pesan yang sudah lewat.
     */
    @Volatile private var hostReady = false

    @Volatile private var startedAt = 0L

    fun busy(): Boolean = sender?.active() == true

    /** Dipanggil saat sesi putus atau layar ditutup. */
    fun stop() {
        sender?.cancel(FileReason.DISCONNECTED)?.let { send(FileWire.encode(it)) }
        worker?.interrupt()
        worker = null
        sender = null
        hostReady = false
    }

    fun cancel() {
        sender?.cancel(FileReason.USER)?.let { send(FileWire.encode(it)) }
        worker?.interrupt()
        post(Status(active = false, text = "Kiriman dibatalkan.", failed = true))
    }

    /** Satu pesan mentah dari channel `"file"`. */
    fun onMessage(bytes: ByteArray) {
        val m = FileWire.decode(bytes)
        if (m == null) {
            // Pesan cacat adalah pelanggaran protokol, bukan sesuatu yang
            // boleh didiamkan: sisa aliran tidak lagi bisa dipercaya.
            sender?.cancel(FileReason.PROTOCOL)?.let { send(FileWire.encode(it)) }
            return
        }
        if (FileWire.isBeacon(m) && sender == null) {
            hostReady = true
            return
        }
        val s = sender ?: return
        s.onMessage(m)?.let { send(FileWire.encode(it)) }
        if (!s.active()) report(s)
    }

    /**
     * Mulai mengirim berkas yang dipilih lewat pemilih dokumen Android.
     * Mengembalikan alasan penolakan, atau null bila dimulai.
     */
    fun start(uri: Uri): String? {
        if (busy()) return "Masih ada kiriman berjalan."
        val (rawName, size) = query(uri) ?: return "Berkas tidak bisa dibaca."
        if (!FileRules.acceptableSize(size)) {
            return if (size <= 0) "Berkas kosong, tidak ada yang dikirim." else
                "Berkas melebihi batas ${FileRules.humanBytes(FileRules.MAX_FILE_BYTES)}."
        }
        val s = FileSender(FileSender.newId(), rawName, size)
        sender = s
        startedAt = System.currentTimeMillis()
        post(Status(true, s.name, 0, "Menunggu PC…"))
        if (hostReady) s.onMessage(FileMsg.Ack(FileWire.BEACON_ID, 0))?.let { send(FileWire.encode(it)) }
        worker = Thread { pump(uri, s) }.also { it.isDaemon = true; it.start() }
        return null
    }

    /**
     * Membaca dan mengirim sampai habis. Dua rem dipasang: jendela ACK di
     * [FileSender] (byte yang benar-benar sampai di disk PC) dan antrean
     * channel di sini (byte yang belum keluar dari HP). Tanpa yang kedua,
     * berkas besar bisa menghabiskan memori proses jauh sebelum satu pun
     * ACK sempat balik.
     */
    private fun pump(uri: Uri, s: FileSender) {
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            ctx.contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) {
                    fail(s, "Berkas tidak bisa dibuka.")
                    return
                }
                val buf = ByteArray(FileRules.MAX_CHUNK_BYTES)
                while (s.active() && !s.ready()) {
                    if (Thread.currentThread().isInterrupted) return
                    val allow = s.allowance()
                    if (allow <= 0 || buffered() > BUFFER_CAP) {
                        Thread.sleep(8)
                        continue
                    }
                    val n = stream.read(buf, 0, allow)
                    if (n <= 0) {
                        // Berkas lebih pendek daripada ukuran yang dilaporkan
                        // sistem: lebih baik dibatalkan daripada host menunggu
                        // byte yang tidak pernah ada.
                        fail(s, "Berkas berubah saat dikirim.")
                        return
                    }
                    val part = buf.copyOf(n)
                    val msg = s.chunk(part) ?: break
                    digest.update(part)
                    if (!send(FileWire.encode(msg))) {
                        fail(s, "Jalur berkas terputus.")
                        return
                    }
                    report(s)
                }
            }
            if (s.ready()) {
                s.finish(digest.digest())?.let { send(FileWire.encode(it)) }
                post(Status(false, s.name, 100, "${s.name} terkirim."))
                sender = null
                return
            }
            report(s)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            fail(s, "Gagal membaca berkas: ${e.message}")
        }
    }

    private fun fail(s: FileSender, pesan: String) {
        s.cancel(FileReason.IO)?.let { send(FileWire.encode(it)) }
        post(Status(false, s.name, s.percent(), pesan, failed = true))
        sender = null
    }

    private fun report(s: FileSender) {
        if (!s.active()) {
            val alasan = s.failReason()
            if (s.state() == FileSender.State.FAILED) {
                post(Status(false, s.name, s.percent(), "Kiriman gagal: ${FileReason.label(alasan)}", failed = true))
                sender = null
            }
            return
        }
        val eta = FileRules.etaSeconds(s.ackedBytes(), s.size, System.currentTimeMillis() - startedAt)
        val sisa = if (eta == null) "" else " · ${eta}s lagi"
        post(
            Status(
                true, s.name, s.percent(),
                "${s.name} · ${s.percent()}% · ${FileRules.humanBytes(s.ackedBytes())}/${FileRules.humanBytes(s.size)}$sisa",
            ),
        )
    }

    private fun post(st: Status) = ui.post { onState(st) }

    /** Nama dan ukuran dari penyedia dokumen; keduanya bisa tidak ada. */
    private fun query(uri: Uri): Pair<String, Long>? {
        var name = uri.lastPathSegment.orEmpty()
        var size = -1L
        val c: Cursor? = runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)
        }.getOrNull()
        c?.use {
            if (it.moveToFirst()) {
                val ni = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (ni >= 0 && !it.isNull(ni)) name = it.getString(ni)
                val si = it.getColumnIndex(OpenableColumns.SIZE)
                if (si >= 0 && !it.isNull(si)) size = it.getLong(si)
            }
        }
        if (size < 0) {
            size = runCatching {
                ctx.contentResolver.openFileDescriptor(uri, "r")?.use { fd -> fd.statSize }
            }.getOrNull() ?: -1L
        }
        if (size < 0) return null
        return name.ifEmpty { FileRules.FALLBACK_NAME } to size
    }

    companion object {
        /** Antrean channel maksimum sebelum pengirim menahan diri. */
        const val BUFFER_CAP = 1L * 1024 * 1024
    }
}
