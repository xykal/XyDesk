package id.xyverse.xydesk.ui

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import id.xyverse.xyadapt.FileAction
import id.xyverse.xyadapt.FileMsg
import id.xyverse.xyadapt.FileReason
import id.xyverse.xyadapt.FileReceiver
import id.xyverse.xyadapt.FileRules
import id.xyverse.xyadapt.FileSender
import id.xyverse.xyadapt.FileWire
import java.io.File
import java.io.OutputStream
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
    /**
     * Menanyakan ke pengguna apakah berkas dari PC boleh masuk. Arah PC → HP
     * selalu bertanya: PC yang memilih nama, ukuran, dan isinya, sementara
     * yang terisi adalah penyimpanan pribadi pemilik HP.
     */
    private val ask: (Ask) -> Unit = {},
) {
    /** Tawaran berkas masuk yang menunggu jawaban pengguna. */
    data class Ask(
        val name: String,
        val size: Long,
        val risky: Boolean,
        val accept: () -> Unit,
        val reject: () -> Unit,
    )
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

    // --- arah PC → HP ---
    @Volatile private var receiver: FileReceiver? = null
    private var sink: OutputStream? = null
    private var part: File? = null
    @Volatile private var inAt = 0L

    fun busy(): Boolean = sender?.active() == true || receiver?.phase() == FileReceiver.Phase.RECEIVING

    /** Dipanggil saat sesi putus atau layar ditutup. */
    fun stop() {
        sender?.cancel(FileReason.DISCONNECTED)?.let { send(FileWire.encode(it)) }
        worker?.interrupt()
        worker = null
        sender = null
        hostReady = false
        receiver?.let {
            it.disconnected()
            // Berkas separuh dibuang, bukan ditinggalkan di folder unduhan
            // dengan nama aslinya: berkas rusak yang terlihat utuh lebih
            // berbahaya daripada berkas yang tidak ada.
            discardIncoming()
        }
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
        if (FileWire.isBeacon(m)) {
            if (sender == null) hostReady = true
            // Balas dengan tanda siap kita sendiri. PC baru boleh menawarkan
            // berkas setelah menerimanya: pendengar di sisi ini terpasang
            // beberapa saat setelah channel terbuka, dan tawaran yang tiba di
            // celah itu hilang tanpa jejak.
            send(FileWire.encode(FileMsg.Ack(FileWire.BEACON_ID, 0)))
            return
        }
        if (m is FileMsg.Offer) {
            incomingOffer(m)
            return
        }
        // Potongan dan DONE selalu milik penerima; ACCEPT/REJECT/ACK milik
        // pengirim. Memisahkannya di sini mencegah satu arah mengacaukan
        // keadaan arah yang lain.
        if (m is FileMsg.Chunk || m is FileMsg.Done) {
            incoming(m)
            return
        }
        if (m is FileMsg.Cancel && receiver != null && sender == null) {
            incoming(m)
            return
        }
        val s = sender ?: return
        s.onMessage(m)?.let { send(FileWire.encode(it)) }
        if (!s.active()) report(s)
    }

    /** Tawaran berkas dari PC. */
    private fun incomingOffer(m: FileMsg.Offer) {
        if (busy() || receiver != null) {
            // Satu transfer dalam satu waktu; menolak membuat batasnya jelas
            // bagi kedua sisi, mengantre tidak.
            send(FileWire.encode(FileMsg.Reject(m.id, FileReason.PROTOCOL)))
            return
        }
        val r = FileReceiver(m.id, m.name, m.size)
        receiver = r
        if (r.phase() == FileReceiver.Phase.FAILED) {
            send(FileWire.encode(FileMsg.Reject(m.id, r.failReason())))
            receiver = null
            return
        }
        post(Status(true, r.name, 0, "PC menawarkan ${r.name} (${FileRules.humanBytes(m.size)})…"))
        ui.post {
            ask(
                Ask(
                    name = r.name,
                    size = m.size,
                    risky = r.risky,
                    accept = { acceptIncoming(r) },
                    reject = {
                        send(FileWire.encode(r.reject()))
                        receiver = null
                        post(Status(false, r.name, 0, "Kiriman dari PC ditolak.", failed = true))
                    },
                ),
            )
        }
    }

    private fun acceptIncoming(r: FileReceiver) {
        val tmp = runCatching { File.createTempFile("xydesk", ".xypart", ctx.cacheDir) }.getOrNull()
        if (tmp == null) {
            send(FileWire.encode(r.reject(FileReason.IO)))
            receiver = null
            post(Status(false, r.name, 0, "Tidak ada ruang untuk menerima berkas.", failed = true))
            return
        }
        part = tmp
        sink = tmp.outputStream().buffered()
        inAt = System.currentTimeMillis()
        r.accept()?.let { send(FileWire.encode(it)) }
        post(Status(true, r.name, 0, "Menerima ${r.name}…"))
    }

    private fun incoming(m: FileMsg) {
        val r = receiver ?: return
        when (val aksi = r.handle(m)) {
            is FileAction.Write -> {
                val out = sink
                if (out == null) {
                    send(FileWire.encode(FileMsg.Cancel(r.id, FileReason.IO)))
                    discardIncoming()
                    return
                }
                val ditulis = runCatching { out.write(aksi.data) }.isSuccess
                if (!ditulis) {
                    send(FileWire.encode(FileMsg.Cancel(r.id, FileReason.IO)))
                    discardIncoming()
                    post(Status(false, r.name, r.percent(), "Gagal menulis berkas masuk.", failed = true))
                    return
                }
                // ACK dikirim per potongan supaya PC bisa menahan lajunya;
                // tanpa itu PC hanya tahu byte-nya sudah keluar dari dirinya
                // sendiri, bukan bahwa byte itu sampai di HP.
                send(FileWire.encode(FileMsg.Ack(r.id, aksi.ack)))
                val eta = FileRules.etaSeconds(r.received(), r.declared, System.currentTimeMillis() - inAt)
                val sisa = if (eta == null) "" else " · ${eta}s lagi"
                post(
                    Status(
                        true, r.name, r.percent(),
                        "Masuk: ${r.name} · ${r.percent()}% · " +
                            "${FileRules.humanBytes(r.received())}/${FileRules.humanBytes(r.declared)}$sisa",
                    ),
                )
            }
            FileAction.Finish -> finishIncoming(r)
            is FileAction.Abort -> {
                send(FileWire.encode(FileMsg.Cancel(r.id, aksi.reason)))
                discardIncoming()
                post(Status(false, r.name, 0, "Kiriman dari PC gagal: ${FileReason.label(aksi.reason)}", failed = true))
            }
            FileAction.None -> {
                if (r.phase() == FileReceiver.Phase.FAILED) {
                    val alasan = r.failReason()
                    discardIncoming()
                    post(Status(false, r.name, 0, "Kiriman dari PC dibatalkan: ${FileReason.label(alasan)}", failed = true))
                }
            }
        }
    }

    /**
     * Berkas baru dipindahkan ke folder Unduhan **setelah** SHA-256 cocok.
     * Sebelum itu ia hanya berkas sementara di cache aplikasi — transfer yang
     * putus tidak meninggalkan berkas yang terlihat utuh padahal rusak.
     */
    private fun finishIncoming(r: FileReceiver) {
        runCatching { sink?.flush(); sink?.close() }
        sink = null
        val tmp = part
        if (tmp == null) {
            discardIncoming()
            return
        }
        val ok = runCatching { publish(tmp, r.name) }.getOrDefault(false)
        tmp.delete()
        part = null
        receiver = null
        post(
            if (ok) Status(false, r.name, 100, "${r.name} tersimpan di Unduhan/XyDesk.")
            else Status(false, r.name, 100, "Berkas diterima tapi gagal disimpan.", failed = true),
        )
    }

    /** Menyalin berkas sementara ke Unduhan/XyDesk lewat MediaStore. */
    private fun publish(tmp: File, name: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/XyDesk")
                // IS_PENDING menyembunyikan berkas dari aplikasi lain sampai
                // isinya benar-benar lengkap.
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            ctx.contentResolver.update(uri, values, null, null)
            return true
        }
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "XyDesk",
        )
        if (!dir.exists() && !dir.mkdirs()) return false
        var target = File(dir, name)
        var n = 2
        // Nama yang bertabrakan dinomori, tidak menimpa berkas pengguna.
        while (target.exists()) {
            val titik = name.lastIndexOf('.')
            val batang = if (titik > 0) name.substring(0, titik) else name
            val ekor = if (titik > 0) name.substring(titik) else ""
            target = File(dir, "$batang ($n)$ekor")
            n++
        }
        tmp.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        return true
    }

    private fun discardIncoming() {
        runCatching { sink?.close() }
        sink = null
        part?.delete()
        part = null
        receiver = null
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
                val buf = ByteArray(FileRules.SEND_CHUNK_BYTES)
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
