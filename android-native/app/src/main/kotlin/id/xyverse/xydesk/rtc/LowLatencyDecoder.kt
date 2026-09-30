package id.xyverse.xydesk.rtc

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.EncodedImage
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoDecoder
import org.webrtc.VideoDecoderFactory
import java.nio.ByteBuffer

/**
 * Jalur decode "game": H.264 dari libwebrtc langsung ke MediaCodec dengan
 * KEY_LOW_LATENCY dan render langsung ke Surface (tanpa salinan tekstur,
 * tanpa antrian render tambahan). Frame TIDAK dikembalikan ke libwebrtc,
 * jadi statistik framesDecoded di getStats nol — telemetri dibaca dari
 * decoder ini sendiri.
 */
class LowLatencyDecoderFactory(
    egl: EglBase.Context,
    private val surface: () -> Surface?,
    private val onFrame: (decodeMs: Float) -> Unit,
    private val onSize: (w: Int, h: Int) -> Unit,
) : VideoDecoderFactory {
    private val fallback = DefaultVideoDecoderFactory(egl)

    override fun createDecoder(info: VideoCodecInfo): VideoDecoder? =
        if (info.name.equals("H264", true)) LowLatencyH264Decoder(surface, onFrame, onSize) else fallback.createDecoder(info)

    override fun getSupportedCodecs(): Array<VideoCodecInfo> = fallback.supportedCodecs
}

class LowLatencyH264Decoder(
    private val surfaceProvider: () -> Surface?,
    private val onFrame: (decodeMs: Float) -> Unit,
    private val onSize: (w: Int, h: Int) -> Unit,
) : VideoDecoder {
    private var codec: MediaCodec? = null
    private val inflight = HashMap<Long, Long>()

    override fun initDecode(settings: VideoDecoder.Settings, callback: VideoDecoder.Callback): VideoCodecStatus {
        val surface = surfaceProvider() ?: return VideoCodecStatus.ERR_PARAMETER
        val w = if (settings.width > 0) settings.width else 1280
        val h = if (settings.height > 0) settings.height else 720
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            setInteger("vendor.qti-ext-dec-low-latency.enable", 1)
            setInteger("vendor.rtc-ext-dec-low-latency.enable", 1)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            setInteger(MediaFormat.KEY_OPERATING_RATE, 240)
        }
        val name = pickDecoder() ?: return VideoCodecStatus.ERROR
        return try {
            codec = MediaCodec.createByCodecName(name).apply {
                setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(c: MediaCodec, index: Int) {
                        synchronized(freeInputs) { freeInputs.addLast(index) }
                    }

                    override fun onOutputBufferAvailable(c: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                        c.releaseOutputBuffer(index, true)
                        val start = inflight.remove(info.presentationTimeUs)
                        if (start != null) onFrame((System.nanoTime() - start) / 1e6f)
                    }

                    override fun onError(c: MediaCodec, e: MediaCodec.CodecException) { Log.e(TAG, "codec: ${e.diagnosticInfo}") }
                    override fun onOutputFormatChanged(c: MediaCodec, f: MediaFormat) {
                        Log.i(TAG, "format $f")
                        val w = crop(f, "crop-right", "crop-left", MediaFormat.KEY_WIDTH)
                        val h = crop(f, "crop-bottom", "crop-top", MediaFormat.KEY_HEIGHT)
                        if (w > 0 && h > 0) onSize(w, h)
                    }
                })
                configure(format, surface, null, 0)
                start()
            }
            Log.i(TAG, "decoder $name low-latency siap ${w}x$h")
            VideoCodecStatus.OK
        } catch (e: Exception) {
            Log.e(TAG, "init gagal: $e")
            VideoCodecStatus.FALLBACK_SOFTWARE
        }
    }

    private val freeInputs = ArrayDeque<Int>()

    override fun decode(image: EncodedImage, info: VideoDecoder.DecodeInfo?): VideoCodecStatus {
        val c = codec ?: return VideoCodecStatus.UNINITIALIZED
        val index = synchronized(freeInputs) { freeInputs.removeFirstOrNull() } ?: return VideoCodecStatus.NO_OUTPUT
        val buf: ByteBuffer = c.getInputBuffer(index) ?: return VideoCodecStatus.ERROR
        buf.clear()
        val src = image.buffer.duplicate()
        if (src.remaining() > buf.remaining()) return VideoCodecStatus.ERROR
        buf.put(src)
        val pts = image.captureTimeNs / 1000
        inflight[pts] = System.nanoTime()
        val flags = if (image.frameType == EncodedImage.FrameType.VideoFrameKey) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        c.queueInputBuffer(index, 0, buf.position(), pts, flags)
        return VideoCodecStatus.OK
    }

    override fun release(): VideoCodecStatus {
        runCatching { codec?.stop(); codec?.release() }
        codec = null
        return VideoCodecStatus.OK
    }

    override fun getImplementationName() = "XyDesk LowLatency MediaCodec"

    private fun crop(f: MediaFormat, hi: String, lo: String, full: String): Int =
        if (f.containsKey(hi) && f.containsKey(lo)) f.getInteger(hi) - f.getInteger(lo) + 1 else f.getInteger(full)

    private fun pickDecoder(): String? {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        val hw = list.filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
        val preferred = hw.firstOrNull { if (Build.VERSION.SDK_INT >= 29) it.isHardwareAccelerated && lowLatency(it) else false }
        return (preferred ?: hw.firstOrNull { Build.VERSION.SDK_INT < 29 || it.isHardwareAccelerated } ?: hw.firstOrNull())?.name
    }

    private fun lowLatency(info: MediaCodecInfo): Boolean = Build.VERSION.SDK_INT >= 30 &&
        info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)

    companion object {
        private const val TAG = "XyDeskDec"
    }
}
