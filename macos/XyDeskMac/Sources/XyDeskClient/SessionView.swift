import SwiftUI
import WebRTC

/// Pembungkus RTCMTLVideoView (render Metal WebRTC) untuk SwiftUI.
struct RemoteVideoView: NSViewRepresentable {
    let track: RTCVideoTrack?

    func makeNSView(context: Context) -> RTCMTLVideoView {
        let view = RTCMTLVideoView(frame: .zero)
        view.videoContentMode = .resizeAspect
        return view
    }

    func updateNSView(_ nsView: RTCMTLVideoView, context: Context) {
        // Pindah track saat sesi baru (track dibuat ulang per sesi).
        if context.coordinator.boundTrack !== track {
            context.coordinator.boundTrack?.remove(nsView)
            track?.add(nsView)
            context.coordinator.boundTrack = track
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator {
        var boundTrack: RTCVideoTrack?
    }
}
