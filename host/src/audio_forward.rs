//! Forward audio bounded: cancellation tidak menunggu datangnya paket WASAPI.
use crate::{
    audio::{AudioPacket, CaptureSource},
    session::Session,
};
use std::sync::Arc;
use std::time::Duration;
use webrtc::peer_connection::peer_connection_state::RTCPeerConnectionState;
use webrtc::track::track_local::track_local_static_sample::TrackLocalStaticSample;

const MAX_AGE: Duration = Duration::from_millis(100);
fn terminal(session: &Session) -> bool {
    matches!(
        session.peer().connection_state(),
        RTCPeerConnectionState::Closed | RTCPeerConnectionState::Failed
    )
}
fn missing_duration(previous: Option<u64>, sequence: u64) -> Duration {
    Duration::from_millis(
        previous
            .map_or(0, |last| sequence.saturating_sub(last).saturating_sub(1))
            .saturating_mul(20),
    )
}

pub async fn pump(
    session: Arc<Session>,
    track: Arc<TrackLocalStaticSample>,
    source: CaptureSource,
) {
    let (tx, mut rx) = tokio::sync::mpsc::channel::<AudioPacket>(1);
    // Receiver drop -> bridge berhenti <=50 ms -> source Drop membatalkan capture.
    std::thread::spawn(move || {
        while !tx.is_closed() {
            match source.recv_timeout(Duration::from_millis(50)) {
                Ok(packet) => match tx.try_send(packet) {
                    Ok(()) | Err(tokio::sync::mpsc::error::TrySendError::Full(_)) => {}
                    Err(tokio::sync::mpsc::error::TrySendError::Closed(_)) => break,
                },
                Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {}
                Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => break,
            }
        }
    });
    let mut tick = tokio::time::interval(Duration::from_millis(25));
    tick.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
    let mut previous = None;
    loop {
        if terminal(&session) {
            break;
        }
        let packet = tokio::select! {
            _ = tick.tick() => continue,
            packet = rx.recv() => match packet { Some(packet) => packet, None => break },
        };
        if session.peer().connection_state() != RTCPeerConnectionState::Connected
            || packet.captured_at.elapsed() > MAX_AGE
        {
            continue;
        }
        let gap = missing_duration(previous, packet.sequence);
        let write = async {
            if !gap.is_zero() {
                track
                    .write_sample(&webrtc::media::Sample {
                        duration: gap,
                        ..Default::default()
                    })
                    .await?;
            }
            track
                .write_sample(&webrtc::media::Sample {
                    data: bytes::Bytes::from(packet.data),
                    duration: Duration::from_millis(20),
                    ..Default::default()
                })
                .await
        };
        match tokio::time::timeout(Duration::from_millis(250), write).await {
            Ok(Ok(())) => previous = Some(packet.sequence),
            _ => break,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn gap_capture_tidak_memperpendek_clock_audio() {
        assert_eq!(missing_duration(None, 50), Duration::ZERO);
        assert_eq!(missing_duration(Some(50), 51), Duration::ZERO);
        assert_eq!(missing_duration(Some(50), 54), Duration::from_millis(60));
    }
}
