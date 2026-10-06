package id.xyverse.xydesk.rtc

import android.content.Context
import android.media.AudioAttributes
import android.util.Log
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.net.Api
import id.xyverse.xydesk.net.SignalMessage
import id.xyverse.xydesk.net.Signaling
import id.xyverse.xydesk.net.SignalingListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import id.xyverse.xyadapt.FileMsg
import id.xyverse.xyadapt.FileWire
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.RendererCommon
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import org.json.JSONObject
import id.xyverse.xyadapt.DisplayRules
import id.xyverse.xydesk.core.HostSpecs
import kotlin.random.Random

enum class Phase { PAIRING, NEGOTIATING, CONNECTED, REJECTED, PEER_OFFLINE, BUSY, ENDED, ERROR }

interface RtcListener {
    fun onPhase(phase: Phase, message: String?)
    fun onHostName(name: String) {}
    fun onRemoteClipboard(text: String) {}
    fun onHostSpecs(specs: HostSpecs) {}
    fun onMicInput(available: Boolean, reason: String) {}

    /**
     * Apakah host sanggup menerima laporan gamepad, dan kalau tidak,
     * kenapa. Tanpa ini tombol gamepad di layar diam saja tanpa penjelasan
     * di PC yang belum punya ViGEmBus.
     */
    fun onHostGamepad(available: Boolean, reason: String) {}

    /**
     * Daftar monitor PC dan monitor yang sedang ditangkap. Dikirim ulang
     * setiap kali meta berubah — termasuk setelah monitor dicabut di tengah
     * sesi, yang membuat daftar lama menunjuk layar yang tidak ada lagi.
     */
    fun onHostDisplays(displays: List<DisplayRules.HostDisplay>, wanted: Int) {}
    fun onHostWallpaper(jpeg: ByteArray) {}

    /** Satu pesan mentah dari data channel `"file"`. */
    fun onFileMessage(bytes: ByteArray) {}
}

/**
 * Sesi client native: signaling + PeerConnection + kanal `input` + AudioDeviceModule.
 * Decode video dipakai lewat MediaCodec (DefaultVideoDecoderFactory / LowLatencyDecoder)
 * langsung ke SurfaceView — tanpa lapisan texture Flutter.
 */
class RtcSession(
    context: Context,
    private val jwt: String,
    private val hostId: String,
    private val pin: String,
    private val selfName: String,
    private val listener: RtcListener,
    lowLatencySurface: (() -> android.view.Surface?)? = null,
    onNativeDecode: (Float) -> Unit = {},
    onNativeSize: (Int, Int) -> Unit = { _, _ -> },
    onDecodeMode: (Boolean) -> Unit = {},
    private val forceRelay: Boolean = false,
) : SignalingListener {
    val egl: EglBase = EglBase.create()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var deviceId = newClientId()
    private var signaling = Signaling(deviceId, this)
    private val adm: AudioDeviceModule
    private var factory: PeerConnectionFactory
    private var pc: PeerConnection? = null
    private var input: DataChannel? = null
    private var file: DataChannel? = null
    private val wallpaper = WallpaperTransfer().also { it.onJpeg = { jpeg -> listener.onHostWallpaper(jpeg) } }
    private var remoteAudioTrack: AudioTrack? = null
    private var micTrack: AudioTrack? = null
    private var micSrc: org.webrtc.AudioSource? = null

    /** Sinkron clipboard opt-in: default mati agar isi clipboard HP tidak bocor ke PC tanpa sengaja. */
    @Volatile var clipboardSync = false
        set(v) { field = v; if (v && input?.state() == DataChannel.State.OPEN) send(StreamXy.clipboardReq()) }
    @Volatile private var audioMuted = false
    @Volatile private var welcomed = false
    @Volatile private var retriedPreWelcome = false
    private var signalToken = ""
    private var renderer: SurfaceViewRenderer? = null
    @Volatile private var stopped = false

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions(),
        )
        adm = JavaAudioDeviceModule.builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        val decoderFactory = if (lowLatencySurface != null) {
            LowLatencyDecoderFactory(egl.eglBaseContext, lowLatencySurface, onNativeDecode, onNativeSize, onDecodeMode)
        } else {
            DefaultVideoDecoderFactory(egl.eglBaseContext)
        }
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoDecoderFactory(decoderFactory)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    fun attach(view: SurfaceViewRenderer) {
        renderer = view
        view.init(egl.eglBaseContext, null)
        // Render natural: aspect-fit, tanpa mirror/crop dan tanpa filter buatan.
        view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        view.setMirror(false)
        view.setEnableHardwareScaler(true)
    }

    fun start() {
        listener.onPhase(Phase.PAIRING, null)
        connectSignaling()
    }

    private fun connectSignaling() {
        scope.launch {
            try {
                welcomed = false
                signalToken = Api.signalToken(jwt, deviceId)
                signaling.connect(signalToken)
            } catch (e: Exception) {
                fail("Gagal mendapat izin signaling: ${e.message}")
            }
        }
    }

    override fun onOpen() {
        signaling.pair(Signaling.normalizeId(hostId), pin, selfName)
    }

    override fun onMessage(m: SignalMessage) {
        Log.d(TAG, "terima ${m.type} ${m.error ?: ""}")
        when (m.type) {
            "welcome" -> welcomed = true
            "pair-response" -> {
                welcomed = true
                if (m.accepted) {
                    m.json.optString("name").takeIf { it.isNotBlank() }?.let(listener::onHostName)
                    listener.onPhase(Phase.NEGOTIATING, null)
                    scope.launch { negotiate() }
                } else {
                    listener.onPhase(Phase.REJECTED, "Password ditolak host. Periksa huruf besar/kecil.")
                }
            }
            "answer" -> m.sdp?.let {
                pc?.setRemoteDescription(NoopSdp, SessionDescription(SessionDescription.Type.ANSWER, it.getString("sdp")))
            }
            "ice" -> m.candidate?.let {
                pc?.addIceCandidate(IceCandidate(it.optString("sdpMid"), it.optInt("sdpMLineIndex"), it.getString("candidate")))
            }
            "bye" -> stop(Phase.ENDED, "Host mengakhiri sesi.")
            "error" -> when (m.error) {
                "peer-offline" -> stop(Phase.PEER_OFFLINE, "PC tidak online. Pastikan XyDesk Host berjalan.")
                "pair-terkunci", "host-sibuk" -> stop(Phase.BUSY, "PC sedang dipakai sesi lain.")
                else -> {
                    // Celah HANDOFF.md: error pra-welcome (mis. soket zombie id-sudah-online)
                    // wajib memicu rotasi ID dan coba ulang otomatis sekali.
                    if (!welcomed && !retriedPreWelcome && !stopped) {
                        retriedPreWelcome = true
                        signaling.close()
                        deviceId = newClientId()
                        signaling = Signaling(deviceId, this)
                        scope.launch {
                            delay(600)
                            if (!stopped) connectSignaling()
                        }
                    } else {
                        fail("Signaling: ${m.error}")
                    }
                }
            }
        }
    }

    override fun onClosed(reason: String) {
        if (stopped) return
        if (!welcomed && !retriedPreWelcome) {
            retriedPreWelcome = true
            deviceId = newClientId()
            signaling = Signaling(deviceId, this)
            scope.launch {
                delay(600)
                if (!stopped) connectSignaling()
            }
            return
        }
        fail("Koneksi signaling terputus ($reason).")
    }

    private suspend fun negotiate() {
        val servers = mutableListOf(PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer())
        Api.turnIce(deviceId, signalToken).forEach { s ->
            val urls = s.optJSONArray("urls")?.let { a -> List(a.length()) { a.getString(it) } }
                ?: listOfNotNull(s.optString("urls").ifBlank { null })
            if (urls.isNotEmpty()) {
                servers += PeerConnection.IceServer.builder(urls)
                    .setUsername(s.optString("username"))
                    .setPassword(s.optString("credential"))
                    .createIceServer()
            }
        }
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            if (forceRelay) iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }
        val conn = factory.createPeerConnection(config, PcObserver()) ?: return fail("PeerConnection gagal dibuat.")
        pc = conn
        conn.addTransceiver(
            org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
        )
        val micSource = factory.createAudioSource(org.webrtc.MediaConstraints())
        val mic = factory.createAudioTrack("xy-mic", micSource).apply { setEnabled(false) }
        micTrack = mic; micSrc = micSource
        conn.addTransceiver(mic, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))
        val ch = conn.createDataChannel("input", DataChannel.Init())
        ch?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                if (ch.state() == DataChannel.State.OPEN) {
                    if (clipboardSync) send(StreamXy.clipboardReq())
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                if (!buffer.binary) { onText(String(data, Charsets.UTF_8)); return }
                if (clipboardSync) StreamXy.clipboardText(data)?.takeIf { it.isNotEmpty() }?.let(listener::onRemoteClipboard)
            }
        })
        input = ch
        // Channel terpisah untuk berkas: satu berkas 200 MB berarti ribuan
        // pesan, dan kalau ikut mengantre di jalur input setiap klik mouse
        // ikut tertahan di belakangnya.
        val fch = conn.createDataChannel(FileWire.CHANNEL, DataChannel.Init())
        fch?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            /**
             * Tanda siap ke host: pendengar di sisi kita sudah terpasang.
             * Host wajib menunggunya sebelum menawarkan berkas — tanpa itu
             * tawaran pertama bisa tiba sebelum ada yang mendengarkan dan
             * hilang tanpa jejak.
             */
            override fun onStateChange() {
                if (fch.state() == DataChannel.State.OPEN) {
                    sendFile(FileWire.encode(FileMsg.Ack(FileWire.BEACON_ID, 0)))
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                if (data.isNotEmpty()) listener.onFileMessage(data)
            }
        })
        file = fch
        conn.createOffer(object : NoopSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                conn.setLocalDescription(NoopSdp, desc)
                signaling.offer(Signaling.normalizeId(hostId), desc.description)
            }
        }, MediaConstraints())
    }

    private var specsSent = false

    /** Pesan JSON dari host di kanal input; saat ini hanya `meta.hardware` yang dipakai. */
    private fun onText(text: String) {
        if (!text.startsWith("{")) return
        val type = runCatching { JSONObject(text) }.getOrNull()?.optString("type").orEmpty()
        if (type == "wallpaper" || type == "wallpaper-error") {
            wallpaper.receiveJson(text)
            return
        }
        val meta = runCatching { JSONObject(text) }.getOrNull()?.takeIf { it.optString("type") == "meta" } ?: return
        meta.optJSONObject("micInput")?.let { listener.onMicInput(it.optBoolean("available", true), it.optString("reason", "")) }
        meta.optJSONObject("gamepad")?.let { listener.onHostGamepad(it.optBoolean("available", false), it.optString("reason", "")) }
        meta.optJSONArray("displays")?.let { arr ->
            val mentah = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    DisplayRules.HostDisplay(
                        index = o.optInt("index", -1),
                        name = o.optString("name", ""),
                        width = o.optInt("width", 0),
                        height = o.optInt("height", 0),
                        // Host menghilangkan field ini bila driver tidak melaporkannya.
                        refreshHz = o.optInt("refreshRate", 0),
                        isPrimary = o.optBoolean("isPrimary", false),
                    )
                }
            }
            listener.onHostDisplays(DisplayRules.sanitize(mentah), meta.optInt("wanted", 0))
        }
        if (specsSent) return
        val hw = meta.optJSONObject("hardware") ?: return
        val specs = HostSpecs.from(hw)
        if (specs.isEmpty) return
        specsSent = true
        listener.onHostSpecs(specs)
    }

    /** Mic HP ke host; jalur sendrecv seperti web, track hanya di-enable saat pengguna menyalakan. */
    fun setMicEnabled(on: Boolean) { micOn = on; micTrack?.setEnabled(on) }

    @Volatile var micOn = false
        private set

    /** Level mic lokal 0..1 dari stats `media-source`; hanya berarti saat micOn. */
    fun micLevel(cb: (Double) -> Unit) {
        val conn = pc ?: return
        conn.getStats { report ->
            val lvl = report.statsMap.values.firstOrNull { it.type == "media-source" && it.members["kind"] == "audio" }
                ?.members?.get("audioLevel") as? Number
            cb(lvl?.toDouble() ?: 0.0)
        }
    }

    fun setAudioMuted(muted: Boolean) {
        audioMuted = muted
        remoteAudioTrack?.let { track ->
            track.setEnabled(!muted)
            track.setVolume(if (muted) 0.0 else 1.0)
        }
    }

    data class LinkStats(
        val fps: Double,
        val frames: Long,
        val decodeSec: Double,
        val rttMs: Double,
        val path: String,
        val lossPct: Double,
    )

    /** Statistik ringkas: fps, dekode, RTT, jalur ICE (UDP RELAY/P2P), loss. */
    fun stats(cb: (LinkStats) -> Unit) {
        val conn = pc ?: return
        conn.getStats { report ->
            var fps = 0.0; var frames = 0L; var decode = 0.0; var rtt = 0.0
            var lost = 0.0; var recv = 0.0
            val type = HashMap<String, String>()
            val proto = HashMap<String, String>()
            report.statsMap.values.forEach { s ->
                if (s.type == "local-candidate") {
                    type[s.id] = s.members["candidateType"]?.toString().orEmpty()
                    proto[s.id] = s.members["protocol"]?.toString().orEmpty()
                }
            }
            var kind = "host"
            var protocol = "udp"
            report.statsMap.values.forEach { s ->
                when (s.type) {
                    "inbound-rtp" -> if (s.members["kind"] == "video") {
                        fps = (s.members["framesPerSecond"] as? Number)?.toDouble() ?: fps
                        frames = (s.members["framesDecoded"] as? Number)?.toLong() ?: frames
                        decode = (s.members["totalDecodeTime"] as? Number)?.toDouble() ?: decode
                        lost = (s.members["packetsLost"] as? Number)?.toDouble() ?: lost
                        recv = (s.members["packetsReceived"] as? Number)?.toDouble() ?: recv
                    }
                    "candidate-pair" -> if (s.members["nominated"] == true || s.members["state"] == "succeeded") {
                        rtt = (s.members["currentRoundTripTime"] as? Number)?.toDouble()?.times(1000) ?: rtt
                        val id = s.members["localCandidateId"]?.toString().orEmpty()
                        kind = type[id] ?: kind
                        protocol = proto[id].orEmpty().ifBlank { protocol }
                    }
                }
            }
            val udp = protocol.equals("tcp", true).not()
            val path = when (kind) {
                "relay" -> if (udp) "UDP RELAY" else "TCP RELAY"
                "srflx", "prflx" -> if (udp) "UDP STUN" else "TCP STUN"
                else -> if (udp) "UDP P2P" else "TCP P2P"
            }
            val total = lost + recv
            val loss = if (total > 0) lost / total * 100.0 else 0.0
            cb(LinkStats(fps, frames, decode, rtt, path, loss))
        }
    }

    fun requestWallpaperPreview() {
        val ch = input ?: return
        if (ch.state() != DataChannel.State.OPEN) return
        wallpaper.requestIfIdle()?.let(::send)
    }

    fun send(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val ch = input ?: return
        if (ch.state() == DataChannel.State.OPEN) ch.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), true))
    }

    /** Mengirim satu pesan protokol berkas; false = channel belum siap. */
    fun sendFile(bytes: ByteArray): Boolean {
        val ch = file ?: return false
        if (ch.state() != DataChannel.State.OPEN) return false
        return runCatching { ch.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), true)) }.getOrDefault(false)
    }

    /** Byte yang masih mengantre di channel berkas — rem untuk pengirim. */
    fun fileBuffered(): Long = file?.bufferedAmount() ?: 0L

    fun fileReady(): Boolean = file?.state() == DataChannel.State.OPEN

    private fun fail(message: String) = stop(Phase.ERROR, message)

    fun stop(phase: Phase = Phase.ENDED, message: String? = null) {
        if (stopped) return
        stopped = true
        runCatching { signaling.bye(Signaling.normalizeId(hostId)) }
        signaling.close()
        input?.unregisterObserver()
        input?.close()
        file?.unregisterObserver()
        file?.close()
        pc?.close()
        pc = null
        remoteAudioTrack = null
        micTrack?.dispose(); micTrack = null
        micSrc?.dispose(); micSrc = null
        scope.cancel()
        listener.onPhase(phase, message)
    }

    fun release() {
        renderer?.release()
        factory.dispose()
        adm.release()
        egl.release()
    }

    private inner class PcObserver : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate) =
            signaling.ice(Signaling.normalizeId(hostId), c.sdp, c.sdpMid, c.sdpMLineIndex)

        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> listener.onPhase(Phase.CONNECTED, null)
                PeerConnection.PeerConnectionState.FAILED -> fail("Koneksi peer gagal (ICE).")
                PeerConnection.PeerConnectionState.CLOSED -> if (!stopped) fail("Koneksi peer ditutup.")
                else -> Unit
            }
        }

        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            when (val track = receiver.track()) {
                is VideoTrack -> renderer?.let(track::addSink)
                is AudioTrack -> {
                    remoteAudioTrack = track
                    track.setEnabled(!audioMuted)
                    track.setVolume(if (audioMuted) 0.0 else 1.0)
                }
            }
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(p0: Boolean) = Unit
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>) = Unit
        override fun onAddStream(p0: MediaStream) = Unit
        override fun onRemoveStream(p0: MediaStream) = Unit
        override fun onDataChannel(p0: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
    }

    private open class NoopSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(p0: String) { Log.w(TAG, "sdp create: $p0") }
        override fun onSetFailure(p0: String) { Log.w(TAG, "sdp set: $p0") }
    }

    private object NoopSdp : NoopSdpObserver()

    companion object {
        private const val TAG = "XyDeskRtc"
        private fun newClientId(): String =
            "app-${System.currentTimeMillis() % 1_000_000}-${Random.nextInt(100, 999)}"
    }
}
