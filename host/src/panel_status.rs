//! Snapshot publik lokal: tidak memuat password, bearer, SDP, atau grant.
use crate::control::EngineState;

pub fn snapshot(state: EngineState, started_at_ms: u64) -> serde_json::Value {
    let capture = crate::screen::capture_telemetry();
    serde_json::json!({
        "pid": std::process::id(), "state": state, "started_at_ms": started_at_ms,
        "backend": capture["backend"], "black_frames": capture["blackFrames"],
        "session_mismatch": capture["sessionMismatch"],
        "proc_session": capture["processSession"], "active_session": capture["activeSession"],
        "armed": capture["armed"], "frames": capture["framesCaptured"],
    })
}

pub fn write(snapshot: &serde_json::Value) -> std::io::Result<()> {
    let dir = crate::identity::config_dir();
    std::fs::create_dir_all(&dir)?;
    let file = dir.join(format!("runtime-{}.json", std::process::id()));
    let temp = file.with_extension("tmp");
    std::fs::write(&temp, serde_json::to_vec(snapshot)?)?;
    std::fs::rename(temp, file)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn status_panel_hanya_field_publik_dan_keadaan_sebenarnya() {
        let value = snapshot(EngineState::Standby, 123);
        assert_eq!(value["state"], "standby");
        assert_eq!(value["pid"], std::process::id());
        for key in [
            "password",
            "token",
            "refresh",
            "resumeToken",
            "signaling_url",
        ] {
            assert!(value.get(key).is_none());
        }
    }
}
