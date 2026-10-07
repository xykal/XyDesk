package id.xyverse.xyadapt

/**
 * Riwayat chat yang disimpan di perangkat supaya ruang obrolan terbuka
 * langsung berisi, bukan kosong dengan tulisan "menyambung".
 *
 * Kenapa bukan JSON: modul ini sengaja tanpa dependensi (tidak ada org.json
 * di JVM murni, dan menambah pustaka hanya untuk ini berlebihan). Formatnya
 * baris per pesan, kolom dipisah TAB, dengan pelolosan untuk TAB, baris baru,
 * dan backslash — cukup untuk data yang semuanya berasal dari server kita
 * sendiri, dan bisa dibaca mata saat memeriksa bug.
 *
 * Yang TIDAK disimpan: apa pun yang rahasia. Isi cache ini sama persis dengan
 * yang sudah terlihat di layar oleh siapa pun yang membuka ruang chat.
 */
object ChatCache {
    /** Versi format; baris pertama berkas. Beda versi = cache dibuang. */
    const val VERSION = "xychat1"

    /** Pesan yang ditahan di cache. Lebih dari ini tidak menambah nilai. */
    const val KEEP = 80

    /** Satu pesan dalam bentuk yang bisa disimpan. */
    data class Row(
        val id: String,
        val from: String,
        val hue: Int,
        val text: String,
        val at: Long,
        val mine: Boolean,
        val vip: Boolean,
        val photo: String,
        val replyId: String,
        val replyFrom: String,
        val replyText: String,
    )

    private fun esc(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> Unit
                else -> append(ch)
            }
        }
    }

    private fun unesc(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> out.append('\\')
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    else -> out.append(value[i + 1])
                }
                i += 2
            } else {
                out.append(ch)
                i += 1
            }
        }
        return out.toString()
    }

    fun encode(rows: List<Row>): String {
        val tail = if (rows.size > KEEP) rows.takeLast(KEEP) else rows
        return buildString {
            append(VERSION)
            for (r in tail) {
                append('\n')
                append(
                    listOf(
                        esc(r.id), esc(r.from), r.hue.toString(), esc(r.text), r.at.toString(),
                        if (r.mine) "1" else "0", if (r.vip) "1" else "0", esc(r.photo),
                        esc(r.replyId), esc(r.replyFrom), esc(r.replyText),
                    ).joinToString("\t"),
                )
            }
        }
    }

    /**
     * Baca cache. Baris yang rusak dilewati, bukan menggagalkan seluruh cache:
     * satu baris cacat tidak boleh membuat ruang obrolan terbuka kosong.
     */
    fun decode(raw: String?): List<Row> {
        if (raw.isNullOrEmpty()) return emptyList()
        val lines = raw.split('\n')
        if (lines.firstOrNull() != VERSION) return emptyList()
        val out = ArrayList<Row>(lines.size)
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) continue
            val f = line.split('\t')
            if (f.size < 11) continue
            val id = unesc(f[0])
            val text = unesc(f[3])
            val at = f[4].toLongOrNull() ?: continue
            if (id.isEmpty() || text.isEmpty()) continue
            out.add(
                Row(
                    id = id,
                    from = unesc(f[1]),
                    hue = f[2].toIntOrNull() ?: 0,
                    text = text,
                    at = at,
                    mine = f[5] == "1",
                    vip = f[6] == "1",
                    photo = unesc(f[7]),
                    replyId = unesc(f[8]),
                    replyFrom = unesc(f[9]),
                    replyText = unesc(f[10]),
                ),
            )
        }
        return out
    }

    /**
     * Gabungkan riwayat dari server dengan yang sudah ada di layar.
     *
     * Server mengirim 50 pesan terakhir saat sambungan terbuka. Kalau daftar
     * itu langsung menggantikan isi layar, pesan cache yang lebih lama akan
     * hilang dan layar "melompat" tepat saat sambungan jadi — persis kedipan
     * yang ingin dihindari. Jadi: cache lama tetap di atas, riwayat server
     * menang untuk id yang sama, dan urutannya waktu.
     */
    fun merge(cached: List<Row>, fresh: List<Row>): List<Row> {
        if (fresh.isEmpty()) return cached
        val byId = LinkedHashMap<String, Row>(cached.size + fresh.size)
        for (r in cached) byId[r.id] = r
        for (r in fresh) byId[r.id] = r
        val merged = byId.values.sortedBy { it.at }
        return if (merged.size > KEEP) merged.takeLast(KEEP) else merged
    }
}
