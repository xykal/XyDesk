package id.xyverse.xydesk.rtc

import android.content.Context
import android.util.Log
import id.xyverse.xydesk.net.Api
import id.xyverse.xydesk.net.SignalMessage
import id.xyverse.xydesk.net.Signaling
import id.xyverse.xydesk.net.SignalingListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
import java.nio.ByteBuffer

enum class Phase { PAIRING, NEGOTIATING, CONNECTED, REJECTED, PEER_OFFLINE, BUSY, ENDED, ERROR }

interface RtcListener {
    fun onPhase(phase: Phase, message: String?)
}

/**
 * Sesi client native: signaling + PeerConnection + kanal `input`.
 * Decode video dipakai lewat MediaCodec (DefaultVideoDecoderFactory) langsung
 * ke SurfaceViewRenderer — tanpa lapisan texture Flutter.
 */
class RtcSession(
    context: Context,
    private val jwt: String,
    private val hostId: String,
    private val pin: String,
    private val selfName: String,
    private val listener: RtcListener,
) : SignalingListener {
    val egl: EglBase = EglBase.create()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val deviceId = "app-${System.currentTimeMillis() % 1_000_000}"
    private val signaling = Signaling(deviceId, this)
    private var factory: PeerConnectionFactory
    private var pc: PeerConnection? = null
    private var input: DataChannel? = null
    private var signalToken = ""
    private var renderer: SurfaceViewRenderer? = null
    @Volatile private var stopped = false

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
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
        scope.launch {
            try {
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
            "pair-response" -> if (m.accepted) {
                listener.onPhase(Phase.NEGOTIATING, null)
                scope.launch { negotiate() }
            } else {
                listener.onPhase(Phase.REJECTED, "Password ditolak host. Periksa huruf besar/kecil.")
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
                else -> fail("Signaling: ${m.error}")
            }
        }
    }

    override fun onClosed(reason: String) {
        if (!stopped) fail("Koneksi signaling terputus ($reason).")
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
        }
        val conn = factory.createPeerConnection(config, PcObserver()) ?: return fail("PeerConnection gagal dibuat.")
        pc = conn
        conn.addTransceiver(
            org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
        )
        conn.addTransceiver(
            org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
        )
        input = conn.createDataChannel("input", DataChannel.Init().apply { ordered = false; maxRetransmits = 0 })
        conn.createOffer(object : NoopSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                conn.setLocalDescription(NoopSdp, desc)
                signaling.offer(Signaling.normalizeId(hostId), desc.description)
            }
        }, MediaConstraints())
    }

    fun send(bytes: ByteArray) {
        val ch = input ?: return
        if (ch.state() == DataChannel.State.OPEN) ch.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), true))
    }

    private fun fail(message: String) = stop(Phase.ERROR, message)

    fun stop(phase: Phase = Phase.ENDED, message: String? = null) {
        if (stopped) return
        stopped = true
        runCatching { signaling.bye(Signaling.normalizeId(hostId)) }
        signaling.close()
        input?.close()
        pc?.close()
        pc = null
        scope.cancel()
        listener.onPhase(phase, message)
    }

    fun release() {
        renderer?.release()
        factory.dispose()
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
            (receiver.track() as? VideoTrack)?.let { track -> renderer?.let(track::addSink) }
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
    }
}
