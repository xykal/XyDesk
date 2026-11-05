import SwiftUI
import GoogleWebRTC
import XyDeskCore

/// XyDesk Mac Client — kendali PC lain (Windows/macOS host) dari Mac.
/// Alur identik dengan web/src/rtc.ts: JWT → pair (kode 9 digit + password)
/// → WebRTC → kanal data "input" berisi paket StreamXy biner.
@main
struct XyDeskClientApp: App {
    @StateObject private var model = ClientModel()

    var body: some Scene {
        WindowGroup("XyDesk Klien") {
            ContentView()
                .environmentObject(model)
                .frame(minWidth: 480, minHeight: 360)
        }
        .windowResizability(.contentMinSize)
    }
}

final class ClientModel: ObservableObject {
    @Published var hostId = ""
    @Published var pin = ""
    @Published var jwt = TokenStore.jwt ?? ""
    @Published var phase = "Siap"
    @Published var connected = false
    @Published var statusNote = ""
    @Published var videoTrack: RTCVideoTrack?
    let session = ClientSession()

    init() {
        session.onPhase = { [weak self] p in
            DispatchQueue.main.async { self?.phase = p }
        }
        session.onVideoTrack = { [weak self] t in
            DispatchQueue.main.async { self?.videoTrack = t }
        }
        session.onConnected = { [weak self] in
            DispatchQueue.main.async { self?.connected = true }
        }
        session.onClosed = { [weak self] msg in
            DispatchQueue.main.async { self?.connected = false; self?.statusNote = msg }
        }
    }

    func connect() {
        TokenStore.jwt = jwt
        let digits = hostId.filter(\.isNumber)
        guard digits.count == 9 else {
            phase = "Kode perangkat harus 9 digit."
            return
        }
        session.start(jwt: jwt, hostId: digits, pin: pin)
    }

    func disconnect() {
        session.stop()
    }
}

struct ContentView: View {
    @EnvironmentObject var model: ClientModel

    var body: some View {
        Group {
            if model.connected {
                SessionView()
            } else {
                connectForm
            }
        }
        .padding(20)
    }

    private var connectForm: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Sambung ke perangkat lain")
                .font(.title2.bold())
            Text("Masukkan kode perangkat 9 digit dari layar host tujuan — PC Windows, Mac, atau lainnya.")
                .foregroundStyle(.secondary)
            TextField("Kode perangkat", text: $model.hostId)
                .textFieldStyle(.roundedBorder)
                .frame(maxWidth: 280)
            SecureField("Password pairing (bila ada)", text: $model.pin)
                .textFieldStyle(.roundedBorder)
                .frame(maxWidth: 280)
            SecureField("JWT akun XyDesk", text: $model.jwt)
                .textFieldStyle(.roundedBorder)
                .frame(maxWidth: 280)
            HStack {
                Button("Sambung") { model.connect() }
                    .keyboardShortcut(.defaultAction)
                    .buttonStyle(.borderedProminent)
                Text(model.phase)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            Spacer()
        }
    }
}

/// Tampilan sesi: video remote + HUD ringkas + tombol putus.
struct SessionView: View {
    @EnvironmentObject var model: ClientModel
    @State private var seconds = 0
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack(alignment: .topLeading) {
            RemoteVideoView(track: model.videoTrack)
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            HStack(spacing: 10) {
                Label(model.session.transportLabel, systemImage: "bolt.horizontal.circle")
                Text(timeString)
                Button("Putus") { model.disconnect() }
                    .buttonStyle(.bordered)
            }
            .font(.caption.monospaced())
            .padding(8)
            .background(.ultraThinMaterial, in: Capsule())
            .padding(10)
        }
        .onReceive(timer) { _ in seconds += 1 }
    }

    private var timeString: String {
        String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }
}
