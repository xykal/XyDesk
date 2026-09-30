package id.xyverse.xydesk.core

/** Jembatan ke libstreamxy.so (C++): protokol input + telemetri latensi. */
object StreamXy {
    init {
        System.loadLibrary("streamxy")
    }

    external fun version(): String
    external fun moveRel(dx: Int, dy: Int): ByteArray
    external fun moveAbs(x: Float, y: Float): ByteArray
    external fun button(button: Int, down: Boolean): ByteArray
    external fun scroll(dx: Int, dy: Int): ByteArray
    external fun key(vk: Int, down: Boolean): ByteArray
    external fun text(s: String): ByteArray
    external fun quality(preset: Int): ByteArray

    external fun statsNew(capacity: Int): Long
    external fun statsFree(handle: Long)
    external fun statsPush(handle: Long, ms: Float)
    external fun statsPercentile(handle: Long, p: Float): Float
}

/** Ring sampel latensi di sisi native; `close()` wajib dipanggil. */
class LatencyRing(capacity: Int = 600) : AutoCloseable {
    private var handle = StreamXy.statsNew(capacity)
    fun push(ms: Float) = StreamXy.statsPush(handle, ms)
    fun p(p: Float): Float = StreamXy.statsPercentile(handle, p)
    override fun close() {
        if (handle != 0L) StreamXy.statsFree(handle)
        handle = 0L
    }
}
