import Foundation
import AppKit
import WebRTC
import XyDeskCore

/// Sesi host macOS: menerima permintaan `pair` dari klien (Android/Web/klien
/// Mac lain), menjawab WebRTC, menyiarkan layar, dan menerjemahkan paket input
/// biner menjadi CGEvent. Alur signaling sama dengan host/src (Windows):
/// hello → pair → (accepted) → offer → answer → ice → kanal data "input".
final class HostSession: NSObject, CaptureEngineCoordinatorDelegate {
    static weak var shared: HostSession?

    private let deviceId: String
    private let signaling: SignalingClient
    private let link = PeerLink()
    private let capture = CaptureEngine()
    private var videoSource: RTCVideoSource?
    private var videoTrack: RTCVideoTrack?
    private var activeClient: String?
    private var displayIndex = 0

    var onStatus: ((String) -> Void)?

    init(deviceId: String) {
        self.deviceId = deviceId
        signaling = SignalingClient(deviceId: deviceId, role: .host)
        super.init()
        HostSession.shared = self
        let coord = CaptureEngineCoordinator()
        coord.delegate = self
        CaptureEngineCoordinator.shared = coord
    }

    func start() {
        Task {
            guard let token = try? await XyDeskApi.signalToken(jwt: TokenStore.jwt ?? "", deviceId: deviceId) else {
                onStatus?("Token signaling gagal — isi JWT di Pengaturan.")
                return
            }
            let ice = await XyDeskApi.turnIce(deviceId: deviceId, token: token)
            signaling.onMessage = { [weak self] m in self?.handle(m) }
            signaling.onClose = { [weak self] msg in self?.onStatus?(msg) }
            signaling.connect(token: token)
            signaling.sendHello()
            link.onIceCandidate = { [weak self] c in
                var m = SignalMessage(type: "ice")
                m.to = self?.activeClient
                m.candidate = SignalMessage.Candidate(candidate: c.sdp, sdpMid: c.sdpMid, sdpMLineIndex: Int(c.sdpMLineIndex))
                self?.signaling.send(m)
            }
            link.onInput = { data in
                if let ev = StreamXy.decode(data) { InputInjector.handle(ev) }
            }
            link.onConnected = { [weak self] in
                self?.onStatus?("Sesi aktif — layar disiarkan.")
            }
            link.onClosed = { [weak self] in
                self?.onStatus?("Sesi berakhir.")
                self?.activeClient = nil
            }
            // Simpan ICE untuk saat offer datang (peer dibuat saat pair diterima).
            pendingIce = ice
        }
    }

    private var pendingIce: [IceServer] = []

    func sendClipboard(_ s: String) {
        link.sendInput(StreamXy.clipboardSet(s))
    }

    func noteVideoHint(_ s: String) {
        onStatus?("Video: \(s)")
    }

    private func handle(_ m: SignalMessage) {
        switch m.type {
        case "pair":
            let pin = UserDefaults.standard.string(forKey: "pairingPassword") ?? ""
            let ok = pin.isEmpty || m.pin == pin
            var reply = SignalMessage(type: "pair")
            reply.to = m.from
            reply.accepted = ok
            if !ok { reply.error = "Password salah." }
            signaling.send(reply)
            guard ok, let from = m.from else { return }
            activeClient = from
            onStatus?("Permintaan dari \(m.name ?? "perangkat") — menyiapkan sesi…")
            if !InputInjector.ensureTrust() {
                onStatus?("Izinkan Accessibility agar input klien bisa diterapkan.")
            }
            link.connect(iceServers: pendingIce, isInitiator: false)
            startCaptureAndOffer()

        case "offer":
            guard let sdp = m.sdp?.sdp else { return }
            link.handleOffer(sdp: sdp) { [weak self] answer in
                guard let self, let answer else { return }
                var out = SignalMessage(type: "answer")
                out.to = m.from ?? self.activeClient
                out.sdp = SignalMessage.Sdp(type: "answer", sdp: answer.sdp)
                self.signaling.send(out)
            }

        case "ice":
            if let c = m.candidate { link.addRemoteCandidate(c) }

        case "bye":
            link.close()
            capture.stop()
            activeClient = nil
            onStatus?("Klien memutus sesi.")

        default:
            break
        }
    }

    private func startCaptureAndOffer() {
        videoSource = link.makeVideoSource()
        if let source = videoSource {
            videoTrack = link.makeVideoTrack(source: source)
            if let track = videoTrack { link.addVideo(track) }
        }
        capture.onFrame = { [weak self] pb in
            guard let self, let source = self.videoSource else { return }
            let frame = RTCVideoFrame(
                buffer: RTCCVPixelBuffer(pixelBuffer: pb),
                rotation: ._0,
                timeStampNs: Int64(CACurrentMediaTime() * 1_000_000_000)
            )
            source.capturer(RTCVideoCapturer(), didCapture: frame)
        }
        capture.start(width: 1920, height: 1080, fps: 30, displayIndex: displayIndex) { [weak self] err in
            if let err { self?.onStatus?("Capture gagal: \(err)") }
        }
    }

    func switchDisplay(_ index: Int) {
        displayIndex = index
        capture.stop()
        capture.start(width: 1920, height: 1080, fps: 30, displayIndex: index) { _ in }
    }
}

// TokenStore dipindah ke XyDeskCore/TokenStore.swift (dipakai Host + Klien).
