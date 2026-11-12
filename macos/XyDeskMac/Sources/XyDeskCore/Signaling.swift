import Foundation

/// Endpoint signaling XyDesk — cermin `web/src/api.ts` (Cloudflare Worker).
/// Semua klien (Web/Android/macOS) memakai protokol yang sama:
/// GET /signal-token → token soket; GET /turn-ice → daftar ICE/TURN;
/// WSS /ws?id=<id>&role=<host|client>&token=<token> → pesan JSON hello/pair/
/// offer/answer/ice/bye.
public enum XyDeskApi {
    public static var base = URL(string: "https://signal.xydesk.my.id")!

    public static var wsURL: URL {
        URL(string: base.absoluteString.replacingOccurrences(of: "https://", with: "wss://") + "/ws")!
    }

    /// Token signaling untuk `deviceId`; butuh JWT akun (Google/OTP).
    public static func signalToken(jwt: String, deviceId: String) async throws -> String {
        var req = URLRequest(url: base.appendingPathComponent("signal-token"))
        req.url = URL(string: base.absoluteString + "/signal-token?id=\(deviceId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? deviceId)")
        req.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")
        let (data, res) = try await URLSession.shared.data(for: req)
        guard let http = res as? HTTPURLResponse, http.statusCode == 200 else {
            throw NSError(domain: "XyDesk", code: 1, userInfo: [NSLocalizedDescriptionKey: "Gagal mendapat izin signaling."])
        }
        return String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    /// Daftar ICE/TURN untuk sesi ini (mirip `turnIce` di web/src/api.ts).
    public static func turnIce(deviceId: String, token: String) async -> [IceServer] {
        guard var comps = URLComponents(url: base.appendingPathComponent("turn-ice"), resolvingAgainstBaseURL: false) else { return [] }
        comps.queryItems = [URLQueryItem(name: "id", value: deviceId), URLQueryItem(name: "token", value: token)]
        guard let url = comps.url, let (data, res) = try? await URLSession.shared.data(from: url),
              let http = res as? HTTPURLResponse, http.statusCode == 200,
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let list = json["iceServers"] as? [[String: Any]] else { return [] }
        return list.compactMap { s in
            guard let urls = s["urls"] as? [String] else { return nil }
            return IceServer(urls: urls, username: s["username"] as? String, credential: s["credential"] as? String)
        }
    }
}

public struct IceServer {
    public let urls: [String]
    public let username: String?
    public let credential: String?
}

/// Satu pesan signaling JSON — cermin `SignalMessage` di web/src/rtc.ts.
public struct SignalMessage: Codable {
    public var type: String
    public var to: String?
    public var from: String?
    public var pin: String?
    public var remember: Bool?
    public var resumeToken: String?
    public var accepted: Bool?
    public var sdp: Sdp?
    public var candidate: Candidate?
    public var error: String?
    public var reason: String?
    public var name: String?
    public var platform: String?

    public struct Sdp: Codable {
        public var type: String
        public var sdp: String
        public init(type: String, sdp: String) { self.type = type; self.sdp = sdp }
    }

    public struct Candidate: Codable {
        public var candidate: String
        public var sdpMid: String?
        public var sdpMLineIndex: Int?
        public init(candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
            self.candidate = candidate; self.sdpMid = sdpMid; self.sdpMLineIndex = sdpMLineIndex
        }
    }

    public init(type: String) { self.type = type }
}

/// Klien WebSocket signaling — protokol hello/pair/offer/answer/ice/bye.
/// Urutan pesan dijaga WebSocket; antrean `onMessage` berurutan seperti
/// `messages` chain di web/src/rtc.ts (kandidat ICE menunggu remote description).
public final class SignalingClient: NSObject {
    public enum Role: String { case host, client }

    public private(set) var deviceId: String
    private let role: Role
    private var task: URLSessionWebSocketTask?
    private let queue = DispatchQueue(label: "id.xydesk.signaling")
    private var pending: [SignalMessage] = []
    private var draining = false

    public var onMessage: ((SignalMessage) -> Void)?
    public var onClose: ((String) -> Void)?

    public init(deviceId: String, role: Role) {
        self.deviceId = deviceId
        self.role = role
    }

    public func connect(token: String) {
        var comps = URLComponents(url: XyDeskApi.wsURL, resolvingAgainstBaseURL: false)!
        comps.queryItems = [
            URLQueryItem(name: "id", value: deviceId),
            URLQueryItem(name: "role", value: role.rawValue),
            URLQueryItem(name: "token", value: token),
        ]
        let task = URLSession.shared.webSocketTask(with: comps.url!)
        self.task = task
        task.resume()
        receiveLoop()
    }

    public func send(_ m: SignalMessage) {
        guard let data = try? JSONEncoder().encode(m),
              let text = String(data: data, encoding: .utf8) else { return }
        task?.send(.string(text)) { _ in }
    }

    /// Alur pembuka klien: hello untuk diri sendiri, lalu pair ke host.
    public func sendHello() {
        var m = SignalMessage(type: "hello")
        m.to = deviceId
        m.reason = role.rawValue
        send(m)
    }

    public func sendPair(hostId: String, pin: String?, remember: Bool, resumeToken: String?, name: String) {
        var m = SignalMessage(type: "pair")
        m.to = hostId
        m.pin = pin
        m.remember = remember
        m.resumeToken = resumeToken
        m.name = name
        m.platform = "mac"
        send(m)
    }

    public func close(reason: String = "bye") {
        var m = SignalMessage(type: "bye")
        m.reason = reason
        send(m)
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
    }

    private func receiveLoop() {
        task?.receive { [weak self] result in
            guard let self else { return }
            switch result {
            case .failure:
                self.onClose?("Koneksi signaling terputus.")
            case .success(.string(let text)):
                if let m = try? JSONDecoder().decode(SignalMessage.self, from: Data(text.utf8)) {
                    self.queue.async {
                        self.pending.append(m)
                        self.drain()
                    }
                }
                self.receiveLoop()
            case .success(.data(let data)):
                if let m = try? JSONDecoder().decode(SignalMessage.self, from: data) {
                    self.queue.async {
                        self.pending.append(m)
                        self.drain()
                    }
                }
                self.receiveLoop()
            @unknown default:
                self.receiveLoop()
            }
        }
    }

    private func drain() {
        guard !draining else { return }
        draining = true
        while !pending.isEmpty {
            let m = pending.removeFirst()
            onMessage?(m)
        }
        draining = false
    }
}
