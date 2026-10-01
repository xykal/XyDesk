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
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import org.json.JSONObject
import id.xyverse.xydesk.core.HostSpecs
import kotlin.random.Random

enum class Phase { PAIRING, NEGOTIATING, CONNECTED, REJECTED, PEER_OFFLINE, BUSY, ENDED, ERROR }

interface RtcListener {
    fun onPhase(phase: Phase, message: String?)
    fun onHostName(name: String) {}
    fun onRemoteClipboard(text: String) {}
    fun onHostSpecs(specs: HostSpecs) {}
    fun onMicInput(available: Boolean, reason: String) {}
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
        val ch = conn.createDataChannel("input", DataChannel.Init().apply { ordered = false; maxRetransmits = 0 })
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
        val meta = runCatching { JSONObject(text) }.getOrNull()?.takeIf { it.optString("type") == "meta" } ?: return
        meta.optJSONObject("micInput")?.let { listener.onMicInput(it.optBoolean("available", true), it.optString("reason", "")) }
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

    /** Statistik ringkas dari getStats: fps, frame terdekode, total waktu dekode (s), RTT ms, relay? */
    fun stats(cb: (fps: Double, frames: Long, decodeSec: Double, rttMs: Double, relay: Boolean) -> Unit) {
        val conn = pc ?: return
        conn.getStats { report ->
            var fps = 0.0; var frames = 0L; var decode = 0.0; var rtt = 0.0; var relay = false
            val locals = HashMap<String, String>()
            report.statsMap.values.forEach { s ->
                if (s.type == "local-candidate") locals[s.id] = s.members["candidateType"]?.toString().orEmpty()
            }
            report.statsMap.values.forEach { s ->
                when (s.type) {
                    "inbound-rtp" -> if (s.members["kind"] == "video") {
                        fps = (s.members["framesPerSecond"] as? Number)?.toDouble() ?: fps
                        frames = (s.members["framesDecoded"] as? Number)?.toLong() ?: frames
                        decode = (s.members["totalDecodeTime"] as? Number)?.toDouble() ?: decode
                    }
                    "candidate-pair" -> if (s.members["nominated"] == true || s.members["state"] == "succeeded") {
                        rtt = (s.members["currentRoundTripTime"] as? Number)?.toDouble()?.times(1000) ?: rtt
                        relay = locals[s.members["localCandidateId"]?.toString()] == "relay"
                    }
                }
            }
            cb(fps, frames, decode, rtt, relay)
        }
    }

    fun send(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val ch = input ?: return
        if (ch.state() == DataChannel.State.OPEN) ch.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), true))
    }

    private fun fail(message: String) = stop(Phase.ERROR, message)

    fun stop(phase: Phase = Phase.ENDED, message: String? = null) {
        if (stopped) return
        stopped = true
        runCatching { signaling.bye(Signaling.normalizeId(hostId)) }
        signaling.close()
        input?.unregisterObserver()
        input?.close()
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
