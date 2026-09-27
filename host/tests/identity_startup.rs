//! Proses nyata pada profil uji terisolasi; tidak menjalankan signaling/capture.
use std::path::PathBuf;
use std::process::Command;
struct Profile(PathBuf);
impl Profile {
    fn new() -> Self {
        Self(std::env::temp_dir().join(format!("xydesk-startup-{}", rand::random::<u64>())))
    }
}
impl Drop for Profile {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}
fn identity(profile: &Profile) -> std::process::Output {
    Command::new(env!("CARGO_BIN_EXE_xydesk-host"))
        .arg("--identity-json")
        .env("XYDESK_HOME", &profile.0)
        .output()
        .unwrap()
}
#[test]
fn concurrent_first_start_uses_one_persisted_identity() {
    let profile = Profile::new();
    let outputs = std::thread::scope(|scope| {
        let children: Vec<_> = (0..8).map(|_| scope.spawn(|| identity(&profile))).collect();
        children
            .into_iter()
            .map(|child| child.join().unwrap())
            .collect::<Vec<_>>()
    });
    let mut values = Vec::new();
    for output in outputs {
        assert!(
            output.status.success(),
            "identity process failed (credentials not printed)"
        );
        let value: serde_json::Value = serde_json::from_slice(&output.stdout).expect("valid JSON");
        values.push(value);
    }
    assert!(
        values.iter().all(|value| value == &values[0]),
        "concurrent identities differ"
    );
    let persisted_id = std::fs::read_to_string(profile.0.join("device_id")).unwrap();
    let persisted_pw = std::fs::read_to_string(profile.0.join("password")).unwrap();
    assert!(values[0]["deviceId"] == persisted_id && values[0]["password"] == persisted_pw);
}
#[test]
fn corrupt_existing_identity_is_not_silently_replaced() {
    let profile = Profile::new();
    std::fs::create_dir_all(&profile.0).unwrap();
    std::fs::write(profile.0.join("device_id"), "broken").unwrap();
    let output = identity(&profile);
    assert!(!output.status.success());
    assert!(output.stdout.is_empty());
    assert_eq!(
        std::fs::read_to_string(profile.0.join("device_id")).unwrap(),
        "broken"
    );
    assert!(!profile.0.join("password").exists());
}
#[test]
fn unwritable_profile_does_not_create_an_ephemeral_identity() {
    let profile = Profile::new();
    std::fs::write(&profile.0, "not a directory").unwrap();
    let output = identity(&profile);
    assert!(!output.status.success());
    assert!(output.stdout.is_empty());
    std::fs::remove_file(&profile.0).unwrap();
}
