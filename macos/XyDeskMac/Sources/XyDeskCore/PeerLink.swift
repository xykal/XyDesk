import Foundation
import WebRTC

/// Pembungkus WebRTC yang dipakai host DAN klien: membuat peer connection
/// unified-plan, jalur ICE dari `/turn-ice`, kanal data "input"/"pointer",
/// dan aliran track video. API GoogleWebRTC (ObjC) — lihat README bila
/// memasang xcframework lain.
public final class PeerLink: NSObject {
    private let factory: RTCPeerConnectionFactory
    public private(set) var pc: RTCPeerConnection?
    public private(set) var input: RTCDataChannel?
    public private(set) var pointer: RTCDataChannel?

    public var onInput: ((Data) -> Void)?
    public var onPointer: ((Data) -> Void)?
    public var onIceCandidate: ((RTCIceCandidate) -> Void)?
    public var onVideoTrack: ((RTCVideoTrack) -> Void)?
    public var onConnected: (() -> Void)?
    public var onClosed: (() -> Void)?

    private var pendingRemoteCandidates: [RTCIceCandidate] = []
    private var remoteDescriptionSet = false

    public override init() {
        RTCInitializeSSL()
        factory = RTCPeerConnectionFactory(encoderFactory: nil, decoderFactory: nil)
        super.init()
    }

    /// Sumber video untuk host (frame dari ScreenCaptureKit dimasukkan ke sini).
    public func makeVideoSource() -> RTCVideoSource {
        factory.videoSource()
    }

    public func makeVideoTrack(source: RTCVideoSource, trackId: String = "video0") -> RTCVideoTrack {
        factory.videoTrack(with: source, trackId: trackId)
    }

    public func connect(iceServers: [IceServer], isInitiator: Bool) {
        let config = RTCConfiguration()
        config.iceServers = iceServers.map {
            RTCIceServer(urlStrings: $0.urls, username: $0.username, credential: $0.credential)
        }
        config.sdpSemantics = .unifiedPlan
        config.continualGatheringPolicy = .gatherContinually
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: nil,
            optionalConstraints: ["DtlsSrtpKeyAgreement": "true"]
        )
        pc = factory.peerConnection(with: config, constraints: constraints, delegate: self)
        if isInitiator {
            let dcConfig = RTCDataChannelConfiguration()
            dcConfig.isOrdered = true
            input = pc?.dataChannel(forLabel: "input", configuration: dcConfig)
            input?.delegate = self
            let pointerConfig = RTCDataChannelConfiguration()
            pointerConfig.isOrdered = false
            pointerConfig.maxRetransmits = 0
            pointer = pc?.dataChannel(forLabel: "pointer", configuration: pointerConfig)
            pointer?.delegate = self
        }
    }

    public func addVideo(_ track: RTCVideoTrack) {
        pc?.add(track, streamIds: ["xydesk"])
    }

    public func sendInput(_ data: Data) {
        let buffer = RTCDataBuffer(data: data, isBinary: true)
        if input?.readyState == .open { input?.sendData(buffer) }
    }

    public func sendPointer(_ data: Data) {
        let buffer = RTCDataBuffer(data: data, isBinary: true)
        if pointer?.readyState == .open { pointer?.sendData(buffer) }
        else if input?.readyState == .open { input?.sendData(buffer) }
    }

    public func offer(completion: @escaping (RTCSessionDescription?) -> Void) {
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: ["OfferToReceiveVideo": "true", "OfferToReceiveAudio": "false"],
            optionalConstraints: nil
        )
        pc?.offer(for: constraints) { sdp, _ in completion(sdp) }
    }

    public func answer(completion: @escaping (RTCSessionDescription?) -> Void) {
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: ["OfferToReceiveVideo": "true", "OfferToReceiveAudio": "false"],
            optionalConstraints: nil
        )
        pc?.answer(for: constraints) { sdp, _ in completion(sdp) }
    }

    public func handleOffer(sdp: String, completion: @escaping (RTCSessionDescription?) -> Void) {
        let desc = RTCSessionDescription(type: .offer, sdp: sdp)
        pc?.setRemoteDescription(desc) { [weak self] error in
            guard error == nil else { completion(nil); return }
            self?.remoteDescriptionSet = true
            self?.flushPendingCandidates()
            self?.answer(completion: completion)
        }
    }

    public func handleAnswer(sdp: String) {
        let desc = RTCSessionDescription(type: .answer, sdp: sdp)
        pc?.setRemoteDescription(desc) { [weak self] _ in
            self?.remoteDescriptionSet = true
            self?.flushPendingCandidates()
        }
    }

    public func addRemoteCandidate(_ c: SignalMessage.Candidate) {
        let cand = RTCIceCandidate(sdp: c.candidate, sdpMLineIndex: Int32(c.sdpMLineIndex ?? 0), sdpMid: c.sdpMid)
        if remoteDescriptionSet {
            pc?.add(cand)
        } else {
            pendingRemoteCandidates.append(cand) // antre seperti web/src/rtc.ts
        }
    }

    private func flushPendingCandidates() {
        for c in pendingRemoteCandidates { pc?.add(c) }
        pendingRemoteCandidates.removeAll()
    }

    public func close() {
        input = nil
        pointer = nil
        pc?.close()
        pc = nil
    }
}

extension PeerLink: RTCPeerConnectionDelegate {
    public func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}

    public func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        if let track = stream.videoTracks.first {
            onVideoTrack?(track)
        }
    }

    public func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}

    public func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}

    public func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        switch newState {
        case .connected, .completed: onConnected?()
        case .closed, .failed, .disconnected: onClosed?()
        default: break
        }
    }

    public func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}

    public func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        onIceCandidate?(candidate)
    }

    public func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}

    public func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {
        dataChannel.delegate = self
        if dataChannel.label == "input" { input = dataChannel }
        if dataChannel.label == "pointer" { pointer = dataChannel }
    }

    public func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCPeerConnectionState) {
        switch newState {
        case .connected: onConnected?()
        case .closed, .failed, .disconnected: onClosed?()
        default: break
        }
    }

    public func peerConnection(_ peerConnection: RTCPeerConnection, didStartReceivingOn transceiver: RTCRtpTransceiver) {
        if let track = transceiver.receiver.track as? RTCVideoTrack {
            onVideoTrack?(track)
        }
    }
}

extension PeerLink: RTCDataChannelDelegate {
    public func dataChannelDidChangeState(_ dataChannel: RTCDataChannel) {
        if dataChannel.readyState == .open,
           dataChannel.label == "input", input == nil {
            input = dataChannel
        }
    }

    public func dataChannel(_ dataChannel: RTCDataChannel, didReceiveMessageWith buffer: RTCDataBuffer) {
        switch dataChannel.label {
        case "input": onInput?(buffer.data)
        case "pointer": onPointer?(buffer.data)
        default: break
        }
    }

    public func dataChannel(_ dataChannel: RTCDataChannel, didChangeBufferedAmount amount: UInt32, from previousAmount: UInt32) {}
}
