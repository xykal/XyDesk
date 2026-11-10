//! Koneksi RDP dari client web — dibuka OLEH host Windows (mstsc), bukan oleh
//! browser. Permintaan tiba lewat data channel WebRTC "rdp" (end-to-end, tidak
//! melewati server signaling), sehingga kredensial RDP tidak pernah menyentuh
//! infrastruktur XyDesk dan IP target tidak terekspos ke internet.
//!
//! Keamanan:
//! - Semua isian divalidasi ketat (karakter diizinkan saja) — tidak ada
//!   shell; semua proses dijalankan lewat argv (CreateProcess), bukan cmd.
//! - Kata sandi tidak pernah ditulis ke berkas; hanya `cmdkey` (Credential
//!   Manager) beberapa detik, lalu dihapus bersama berkas .rdp sementara.

use std::sync::Arc;

use serde::{Deserialize, Serialize};
use webrtc::data_channel::RTCDataChannel;

// (Deserialize dipakai untuk mem-parse permintaan dari data channel.)

pub const RDP_CHANNEL: &str = "rdp";

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct RdpRequest {
    pub host: String,
    #[serde(default = "default_port")]
    pub port: u16,
    pub user: String,
    #[serde(default)]
    pub pass: String,
    #[serde(default = "default_w")]
    pub width: u32,
    #[serde(default = "default_h")]
    pub height: u32,
}

fn default_port() -> u16 {
    3389
}
fn default_w() -> u32 {
    1920
}
fn default_h() -> u32 {
    1080
}

#[derive(Debug, Clone, Serialize)]
pub struct RdpStatus {
    #[serde(rename = "type")]
    pub kind: String,
    pub ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
}

impl RdpStatus {
    pub fn ok() -> Self {
        Self {
            kind: "status".into(),
            ok: true,
            reason: None,
        }
    }
    pub fn err(reason: impl Into<String>) -> Self {
        Self {
            kind: "status".into(),
            ok: false,
            reason: Some(reason.into()),
        }
    }
}

/// Alamat host: nama NetBIOS/DNS atau IPv4 — huruf, angka, titik, minus saja.
pub fn valid_host(host: &str) -> bool {
    let h = host.trim();
    if h.is_empty() || h.len() > 253 {
        return false;
    }
    if !h
        .chars()
        .all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '-')
    {
        return false;
    }
    !h.starts_with('.')
        && !h.ends_with('.')
        && !h.starts_with('-')
        && !h.ends_with('-')
        && !h.contains("..")
}

/// Username boleh `DOMAIN\user`, `user@domain`, spasi, titik, minus, underscore.
pub fn valid_user(user: &str) -> bool {
    let u = user.trim();
    !u.is_empty()
        && u.len() <= 128
        && u.chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, ' ' | '.' | '_' | '-' | '\\' | '@'))
}

/// Validasi permintaan penuh. Ok(()) bila sah; Err(pesan) untuk client.
pub fn validate(req: &RdpRequest) -> Result<(), String> {
    if !valid_host(&req.host) {
        return Err("alamat host tidak valid".into());
    }
    if req.port == 0 {
        return Err("port tidak valid".into());
    }
    if !valid_user(&req.user) {
        return Err("username tidak valid".into());
    }
    if req.pass.len() > 512 {
        return Err("kata sandi terlalu panjang".into());
    }
    if !(100..=7680).contains(&req.width) || !(100..=4320).contains(&req.height) {
        return Err("resolusi tidak valid".into());
    }
    Ok(())
}

/// Isi berkas .rdp sementara (TANPA kata sandi — autentikasi via cmdkey).
pub fn rdp_file(req: &RdpRequest) -> String {
    format!(
        "full address:s:{host}:{port}\r\nprompt for credentials:i:0\r\nauthentication level:i:2\r\nredirectclipboard:i:1\r\nredirectprinters:i:0\r\nscreen mode id:i:1\r\ndesktopwidth:i:{w}\r\ndesktopheight:i:{h}\r\nsession bpp:i:32\r\ncompression:i:1\r\nvideoplaybackmode:i:1\r\n",
        host = req.host.trim(),
        port = req.port,
        w = req.width,
        h = req.height
    )
}

#[cfg(windows)]
pub fn launch(req: &RdpRequest) -> Result<(), String> {
    use std::os::windows::process::CommandExt;
    use std::process::Command;

    validate(req)?;

    // 1. Simpan kredensial di Credential Manager (bukan di berkas).
    let target = format!("TERMSRV/{}", req.host.trim());
    let st = Command::new("cmdkey")
        .args(["/generic:", &target])
        .args(["/user:", req.user.trim()])
        .args(["/pass:", &req.pass])
        .creation_flags(0x0800_0000) // CREATE_NO_WINDOW
        .status()
        .map_err(|e| format!("cmdkey gagal: {e}"))?;
    if !st.success() {
        return Err("cmdkey ditolak Windows".into());
    }

    // 2. Tulis berkas .rdp sementara (tanpa kata sandi).
    let mut path = std::env::temp_dir();
    let unique = format!(
        "xydesk-rdp-{}-{}.rdp",
        std::process::id(),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis())
            .unwrap_or(0)
    );
    path.push(unique);
    std::fs::write(&path, rdp_file(req)).map_err(|e| format!("tulis .rdp gagal: {e}"))?;

    // 3. Jalankan mstsc penuh layar.
    let mut cmd = Command::new("mstsc");
    cmd.arg(&path).arg("/f").creation_flags(0x0800_0000);
    cmd.spawn().map_err(|e| format!("mstsc gagal: {e}"))?;

    // 4. Bersihkan kredensial + berkas setelah mstsc sempat tersambung (45 dtk).
    let host = req.host.trim().to_string();
    std::thread::spawn(move || {
        std::thread::sleep(std::time::Duration::from_secs(45));
        let _ = Command::new("cmdkey")
            .args(["/delete:", &format!("TERMSRV/{host}")])
            .creation_flags(0x0800_0000)
            .status();
        let _ = std::fs::remove_file(&path);
    });
    Ok(())
}

#[cfg(not(windows))]
pub fn launch(_req: &RdpRequest) -> Result<(), String> {
    Err("koneksi RDP hanya didukung pada host Windows".into())
}

/// Layani kanal data "rdp": terima JSON `RdpRequest`, buka mstsc, balas status.
/// Kata sandi tidak pernah dicatat ke log.
pub async fn serve_channel(dc: Arc<RTCDataChannel>) {
    let reply = Arc::clone(&dc);
    dc.on_message(Box::new(move |msg| {
        let reply = Arc::clone(&reply);
        Box::pin(async move {
            let text = String::from_utf8_lossy(&msg.data);
            let status = match serde_json::from_str::<RdpRequest>(&text) {
                Ok(req) => match launch(&req) {
                    Ok(()) => {
                        println!(
                            "[xydesk-host] rdp: membuka mstsc ke {}:{}",
                            req.host, req.port
                        );
                        RdpStatus::ok()
                    }
                    Err(e) => RdpStatus::err(e),
                },
                Err(_) => RdpStatus::err("format permintaan tidak dikenal"),
            };
            let payload = serde_json::to_string(&status).unwrap_or_default();
            let _ = reply.send_text(payload).await;
        })
    }));
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ok_req() -> RdpRequest {
        RdpRequest {
            host: "pc-kantor.local".into(),
            port: 3389,
            user: "KANTOR\\xyadmin".into(),
            pass: "rahasia".into(),
            width: 1920,
            height: 1080,
        }
    }

    #[test]
    fn host_valid() {
        assert!(valid_host("192.168.1.10"));
        assert!(valid_host("pc-kantor.local"));
        assert!(valid_host("SERVER01"));
        assert!(!valid_host(""));
        assert!(!valid_host("a..b"));
        assert!(!valid_host("-evil"));
        assert!(!valid_host("evil-"));
        assert!(!valid_host("host;shutdown"));
        assert!(!valid_host("host name"));
        assert!(!valid_host("host&whoami"));
        assert!(!valid_host(&"x".repeat(300)));
    }

    #[test]
    fn user_valid() {
        assert!(valid_user("xyadmin"));
        assert!(valid_user("KANTOR\\xyadmin"));
        assert!(valid_user("user@domain.id"));
        assert!(valid_user("Budi Santoso"));
        assert!(!valid_user(""));
        assert!(!valid_user("user|whoami"));
        assert!(!valid_user("user`id`"));
        assert!(!valid_user(&"u".repeat(200)));
    }

    #[test]
    fn validate_req() {
        assert!(validate(&ok_req()).is_ok());
        let mut bad = ok_req();
        bad.host = "x;calc".into();
        assert!(validate(&bad).is_err());
        let mut bad = ok_req();
        bad.port = 0;
        assert!(validate(&bad).is_err());
        let mut bad = ok_req();
        bad.width = 10;
        assert!(validate(&bad).is_err());
    }

    #[test]
    fn rdp_file_tanpa_password() {
        let f = rdp_file(&ok_req());
        assert!(f.contains("full address:s:pc-kantor.local:3389"));
        assert!(!f.to_lowercase().contains("password"));
        assert!(!f.contains("rahasia"));
        assert!(f.contains("desktopwidth:i:1920"));
    }
}
