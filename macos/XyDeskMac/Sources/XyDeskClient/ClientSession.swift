import Foundation
import AppKit
import WebRTC
import XyDeskCore

/// Sesi klien — cermin alur `web/src/rtc.ts` (start → signal-token → ws →
/// hello/pair → offer → answer/ice → kanal data "input"/"pointer").
final class ClientSession: NSObject {
    private var signaling: SignalingClient?
    private let link = PeerLink()
    private var deviceId = ""
    private var hostId = ""
    private var inputCapture: InputCapture?

    private(set) var videoTrack: RTCVideoTrack?
    private(set) var transportLabel = "Menghubungkan…"

    var onPhase: ((String) -> Void)?
    var onConnected: (() -> Void)?
    var onClosed: ((String) -> Void)?
    var onVideoTrack: ((RTCVideoTrack) -> Void)?

    func start(jwt: String, hostId: String, pin: String) {
        self.hostId = hostId
        deviceId = "mac-\(UUID().uuidString.prefix(8).lowercased())"
        onPhase?("Meminta izin signaling…")
        Task {
            guard let token = try? await XyDeskApi.signalToken(jwt: jwt, deviceId: deviceId) else {
                onPhase?("Token signaling gagal — periksa JWT.")
                return
            }
            let ice = await XyDeskApi.turnIce(deviceId: deviceId, token: token)
            await MainActor.run {
                let sig = SignalingClient(deviceId: self.deviceId, role: .client)
                self.signaling = sig
                sig.onMessage = { [weak self] m in self?.handle(m) }
                sig.onClose = { [weak self] msg in self?.onClosed?(msg) }
                sig.connect(token: token)
                sig.sendHello()
                sig.sendPair(hostId: hostId, pin: pin.isEmpty ? nil : pin,
                             remember: false, resumeToken: nil,
                             name: "Mac di \(Host.current().localizedName ?? "macOS")")
                self.link.connect(iceServers: ice, isInitiator: true)
                self.link.onIceCandidate = { [weak self] c in
                    var m = SignalMessage(type: "ice")
                    m.to = self?.hostId
                    m.candidate = .init(candidate: c.sdp, sdpMid: c.sdpMid, sdpMLineIndex: Int(c.sdpMLineIndex))
                    self?.signaling?.send(m)
                }
                self.link.onVideoTrack = { [weak self] track in
                    DispatchQueue.main.async {
                        self?.videoTrack = track
                        self?.onVideoTrack?(track)
                    }
                }
                self.link.onConnected = { [weak self] in
                    DispatchQueue.main.async {
                        self?.transportLabel = "Sesi aktif"
                        self?.onConnected?()
                        let capture = InputCapture { [weak self] data, pointer in
                            if pointer { self?.link.sendPointer(data) } else { self?.link.sendInput(data) }
                        }
                        capture.start()
                        self?.inputCapture = capture
                    }
                }
                self.link.onClosed = { [weak self] in
                    DispatchQueue.main.async {
                        self?.inputCapture?.stop()
                        self?.onClosed?("Sesi berakhir.")
                    }
                }
                self.onPhase?("Menawarkan sesi…")
                self.link.offer { [weak self] sdp in
                    guard let self, let sdp else { return }
                    var m = SignalMessage(type: "offer")
                    m.to = self.hostId
                    m.sdp = .init(type: "offer", sdp: sdp.sdp)
                    self.signaling?.send(m)
                }
            }
        }
    }

    func stop() {
        inputCapture?.stop()
        signaling?.close()
        link.close()
        onClosed?("Sesi diputus.")
    }

    /// Meneruskan paket yang sudah jadi (dipanggil InputCapture).
    func sendInput(_ data: Data) { link.sendInput(data) }
    func sendPointer(_ data: Data) { link.sendPointer(data) }

    private func handle(_ m: SignalMessage) {
        switch m.type {
        case "pair":
            if m.accepted == false {
                onPhase?("Ditolak host: \(m.error ?? "tidak diizinkan")")
                stop()
            } else {
                onPhase?("Pairing diterima — negosiasi…")
            }
        case "answer":
            if let sdp = m.sdp?.sdp { link.handleAnswer(sdp: sdp) }
        case "ice":
            if let c = m.candidate { link.addRemoteCandidate(c) }
        case "bye":
            inputCapture?.stop()
            onClosed?("Host mengakhiri sesi.")
        default:
            break
        }
    }
}

/// Pemantau input lokal (keyboard/mouse) → paket StreamXy.
/// `pointer=true` melewati kanal rendah-latensi (tidak terurut).
final class InputCapture {
    private var monitors: [Any] = []
    private let send: (Data, Bool) -> Void
    private var lastPos: CGPoint = .zero

    init(send: @escaping (Data, Bool) -> Void) {
        self.send = send
    }

    func start() {
        let keyMask: NSEvent.EventTypeMask = [.keyDown, .keyUp]
        monitors.append(NSEvent.addLocalMonitorForEvents(matching: keyMask) { [weak self] ev in
            guard let self else { return ev }
            if let vk = WindowsVK.map(ev.keyCode) {
                self.send(StreamXy.key(vk: vk, down: ev.type == .keyDown), true)
            } else if ev.type == .keyDown, let chars = ev.characters, !chars.isEmpty {
                self.send(StreamXy.text(chars), false)
            }
            return nil // telan: jangan bunyi "beep" sistem
        })
        let mouseMask: NSEvent.EventTypeMask = [.mouseMoved, .leftMouseDragged, .rightMouseDragged, .otherMouseDragged,
                                                .leftMouseDown, .leftMouseUp,
                                                .rightMouseDown, .rightMouseUp,
                                                .otherMouseDown, .otherMouseUp, .scrollWheel]
        monitors.append(NSEvent.addLocalMonitorForEvents(matching: mouseMask) { [weak self] ev in
            guard let self else { return ev }
            switch ev.type {
            case .mouseMoved, .leftMouseDragged, .rightMouseDragged, .otherMouseDragged:
                if let win = ev.window {
                    let f = win.frame
                    let x = Double(ev.locationInWindow.x / max(1, f.width))
                    let y = Double(1 - ev.locationInWindow.y / max(1, f.height))
                    self.send(StreamXy.moveAbs(x: x, y: y), true)
                }
            case .leftMouseDown: self.send(StreamXy.button(1, down: true), true)
            case .leftMouseUp: self.send(StreamXy.button(1, down: false), true)
            case .rightMouseDown: self.send(StreamXy.button(2, down: true), true)
            case .rightMouseUp: self.send(StreamXy.button(2, down: false), true)
            case .otherMouseDown: self.send(StreamXy.button(3, down: true), true)
            case .otherMouseUp: self.send(StreamXy.button(3, down: false), true)
            case .scrollWheel:
                self.send(StreamXy.scroll(dx: Int16(ev.scrollingDeltaX), dy: Int16(ev.scrollingDeltaY)), true)
            default: break
            }
            return nil
        })
    }

    func stop() {
        for m in monitors { NSEvent.removeMonitor(m) }
        monitors.removeAll()
    }
}

/// Peta CGKeyCode macOS → virtual-key Windows (kebalikan InputInjector.keyCode).
enum WindowsVK {
    static func map(_ code: UInt16) -> UInt16? {
        let map: [UInt16: UInt16] = [
            51: 0x08, 48: 0x09, 36: 0x0D, 56: 0x10, 59: 0x11, 58: 0x12, 57: 0x14,
            53: 0x1B, 49: 0x20, 126: 0x26, 124: 0x27, 125: 0x28, 123: 0x25,
            105: 0x2C, 118: 0x2D, 117: 0x2E, 55: 0x5B, 54: 0x5C,
            122: 0x70, 120: 0x71, 99: 0x72, 118: 0x73, 96: 0x74, 97: 0x75,
            98: 0x76, 100: 0x77, 101: 0x78, 109: 0x79, 103: 0x7A, 111: 0x7B,
        ]
        if let vk = map[code] { return vk }
        let ansiLetters: [UInt16: UInt16] = [
            0: 0x41, 13: 0x42, 8: 0x43, 2: 0x44, 14: 0x45, 3: 0x46, 5: 0x47,
            4: 0x48, 34: 0x49, 38: 0x4A, 40: 0x4B, 37: 0x4C, 46: 0x4D, 45: 0x4E,
            31: 0x4F, 35: 0x50, 12: 0x51, 15: 0x52, 1: 0x53, 17: 0x54, 32: 0x55,
            9: 0x56, 11: 0x57, 7: 0x58, 16: 0x59, 6: 0x5A,
        ]
        if let vk = ansiLetters[code] { return vk }
        let ansiDigits: [UInt16: UInt16] = [
            29: 0x30, 18: 0x31, 19: 0x32, 20: 0x33, 21: 0x34,
            23: 0x35, 22: 0x36, 26: 0x37, 28: 0x38, 25: 0x39,
        ]
        return ansiDigits[code]
    }
}
