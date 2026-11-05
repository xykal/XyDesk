import Foundation
import ScreenCaptureKit
import CoreVideo

/// Penangkap layar via ScreenCaptureKit (macOS 13+). Frame dikirim ke
/// `onFrame` sebagai CVPixelBuffer (BGRA) untuk dirakit menjadi RTCVideoFrame.
final class CaptureEngine: NSObject, SCStreamOutput, SCStreamDelegate {
    private var stream: SCStream?
    private let queue = DispatchQueue(label: "id.xydesk.capture")
    var onFrame: ((CVPixelBuffer) -> Void)?
    var onStopped: ((String) -> Void)?

    func start(width: Int = 1920, height: Int = 1080, fps: Int = 30,
               displayIndex: Int = 0, completion: @escaping (String?) -> Void) {
        SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true) { [weak self] content, error in
            guard let self else { return }
            if let error {
                completion("Izin perekaman layar diperlukan: \(error.localizedDescription)")
                return
            }
            guard let content, !content.displays.isEmpty else {
                completion("Tidak ada layar untuk ditangkap.")
                return
            }
            let display = content.displays[min(max(0, displayIndex), content.displays.count - 1)]
            let filter = SCContentFilter(display: display, excludingWindows: [])
            let config = SCStreamConfiguration()
            config.width = width
            config.height = height
            config.minimumFrameInterval = CMTime(value: 1, timescale: CMTimeScale(max(1, fps)))
            config.queueDepth = 3
            config.pixelFormat = kCVPixelFormatType_32BGRA
            config.showsCursor = true
            let stream = SCStream(filter: filter, configuration: config, delegate: self)
            do {
                try stream.addStreamOutput(self, type: .screen, sampleHandlerQueue: self.queue)
                stream.startCapture { error in
                    completion(error?.localizedDescription)
                }
                self.stream = stream
            } catch {
                completion(error.localizedDescription)
            }
        }
    }

    func stop() {
        stream?.stopCapture { _ in }
        stream = nil
    }

    func captureStream(_ stream: SCStream, didOutputSampleBuffer sampleBuffer: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .screen,
              let pb = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        onFrame?(pb)
    }

    func stream(_ stream: SCStream, didStopRecordingWithError error: Error) {
        onStopped?(error.localizedDescription)
    }
}
