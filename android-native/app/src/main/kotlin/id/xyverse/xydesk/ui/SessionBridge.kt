package id.xyverse.xydesk.ui

/**
 * Jembatan sesi aktif untuk panel pengaturan: mengirim paket StreamXy ke host
 * selama sesi berjalan (mis. perintah layar virtual / super screen). Didaftarkan
 * oleh SessionActivity dan dilepas saat sesi berakhir.
 */
object SessionBridge {
    @Volatile
    var send: ((ByteArray) -> Unit)? = null
        private set

    fun attach(sender: (ByteArray) -> Unit) {
        send = sender
    }

    fun detach() {
        send = null
    }
}
