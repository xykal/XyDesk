import SwiftUI
import WebRTC

/// Pembungkus RTCMTLNSVideoView (render Metal WebRTC) untuk SwiftUI.
struct RemoteVideoView: NSViewRepresentable {
    let track: RTCVideoTrack?

    func makeNSView(context: Context) -> RTCMTLNSVideoView {
        let view = RTCMTLNSVideoView(frame: .zero)
        return view
    }

    func updateNSView(_ nsView: RTCMTLNSVideoView, context: Context) {
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
