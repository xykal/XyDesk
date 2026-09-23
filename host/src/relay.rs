//! Kredensial relay TURN untuk host — beserta sebabnya bila tidak ada.
//!
//! ## Kenapa ini modul sendiri
//!
//! Relay adalah jalan keluar terakhir WebRTC: host di belakang CGNAT atau NAT
//! simetris tidak bisa dijangkau sama sekali tanpa relay. Justru karena itu
//! kegagalannya harus terdengar. Sebelum modul ini, kegagalan mengambil
//! kredensial hanya menjadi satu baris `eprintln` yang hilang di log —
//! sementara pengguna menatap "menyambung…" tanpa sebab, dan operator tidak
//! punya apa pun untuk dibaca.
//!
//! Worker signaling sekarang menjawab penolakan dengan `reason` + `hint`
//! (lihat `cloudflare/src/worker.js`). Modul ini yang mengubahnya menjadi
//! status yang bisa dipakai: baris log host, `relay` di `/status`, dan panel.
//!
//! ## Yang tidak dilakukan modul ini
//!
//! Ketiadaan relay **tidak pernah** menggagalkan sesi. Banyak jaringan memang
//! tersambung langsung, dan mematikan sesi hanya karena relay tidak ada akan
//! merusak kasus yang paling umum. Yang wajib adalah menyebut sebabnya.

use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::Deserialize;

use crate::recover_lock;

/// Satu server relay yang siap dipakai (bentuk netral — pemetaan ke tipe
/// WebRTC dilakukan pemanggil, supaya modul ini tetap ringan untuk diuji).
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RelayServer {
    pub urls: Vec<String>,
    pub username: String,
    pub credential: String,
}

/// Hasil satu percobaan mengambil kredensial relay.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum RelayOutcome {
    Ready(Vec<RelayServer>),
    Unavailable {
        /// Kode sebab dari server, mis. `token-invalid`, `providers-failed`,
        /// `network`. Kode ini yang dicari operator di log dan di `/status`.
        reason: String,
        /// Saran tindak lanjut dari server, apa adanya.
        detail: Option<String>,
    },
}

/// Bentuk balasan `/turn-ice`. Bidang yang tidak dikenal diabaikan supaya
/// server yang menambah diagnostik baru tidak mematahkan host lama.
#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase")]
struct TurnBody {
    #[serde(default)]
    ice_servers: Vec<serde_json::Value>,
    #[serde(default)]
    degraded: bool,
    error: Option<String>,
    reason: Option<String>,
    hint: Option<String>,
}

/// Terjemahan kode sebab menjadi kalimat yang bisa dibaca manusia. Dipakai
/// log host dan panel; kode mentahnya tetap ikut dicatat supaya sebab baru
/// dari server tidak pernah hilang diam-diam.
pub fn reason_label(reason: &str) -> &'static str {
    match reason {
        "no-credentials" => "server menolak permintaan tanpa token perangkat",
        "token-invalid" => "token perangkat ditolak server (kemungkinan sudah kedaluwarsa)",
        "ticket-invalid" => "tiket perangkat tidak sah untuk relay",
        "ticket-revoked" => "sesi akun dicabut server",
        "turn-forbidden" => "permintaan kredensial relay ditolak server",
        "turn-not-configured" => "server signaling belum dikonfigurasi TURN",
        "turn-auth-unavailable" => "server otorisasi sedang tidak bisa dihubungi",
        "providers-failed" => "semua penyedia relay tidak menjawab",
        "no-servers" => "server tidak mengirim daftar relay",
        "bad-response" => "balasan server tidak dikenali",
        "network" => "jaringan ke server signaling gagal",
        _ => "sebab tidak diketahui",
    }
}

/// Baca balasan `/turn-ice` menjadi hasil yang jujur. Status HTTP dan isi
/// badan sama-sama diperiksa: 200 dengan daftar kosong bukan keberhasilan.
pub fn parse_response(status: u16, body: &str) -> RelayOutcome {
    let parsed: Result<TurnBody, _> = serde_json::from_str(body);

    if !(200..300).contains(&status) {
        let (reason, detail) = match parsed {
            Ok(body) => (
                body.reason
                    .or(body.error)
                    .unwrap_or_else(|| format!("http-{status}")),
                body.hint,
            ),
            // Badan non-JSON (mis. halaman gateway) tidak pernah dikutip utuh
            // ke log: yang penting statusnya, bukan HTML-nya.
            Err(_) => (format!("http-{status}"), None),
        };
        return RelayOutcome::Unavailable { reason, detail };
    }

    let Ok(body) = parsed else {
        return RelayOutcome::Unavailable {
            reason: "bad-response".to_string(),
            detail: None,
        };
    };

    let servers: Vec<RelayServer> = body.ice_servers.iter().filter_map(server_from).collect();
    if servers.is_empty() {
        // Penyedia sudah dikonfigurasi tetapi tidak satu pun menjawab berbeda
        // dari server yang memang belum punya penyedia: beda tindakan.
        let reason = if body.degraded {
            "providers-failed"
        } else {
            "no-servers"
        };
        return RelayOutcome::Unavailable {
            reason: reason.to_string(),
            detail: body.hint,
        };
    }
    RelayOutcome::Ready(servers)
}

fn server_from(value: &serde_json::Value) -> Option<RelayServer> {
    let urls = match value.get("urls")? {
        serde_json::Value::String(url) => vec![url.clone()],
        serde_json::Value::Array(items) => items
            .iter()
            .filter_map(|item| item.as_str().map(str::to_owned))
            .collect(),
        _ => return None,
    };
    if urls.is_empty() {
        return None;
    }
    Some(RelayServer {
        urls,
        username: value
            .get("username")
            .and_then(|v| v.as_str())
            .unwrap_or_default()
            .to_string(),
        credential: value
            .get("credential")
            .and_then(|v| v.as_str())
            .unwrap_or_default()
            .to_string(),
    })
}

/// Ambil kredensial relay untuk perangkat ini, lalu catat hasilnya.
///
/// Token yang dipakai adalah token signaling `role=host` yang sama seperti
/// saat menyambung ke `/ws` — host tidak memegang `ADMIN_SECRET`.
pub fn fetch(device_id: &str, token: &str) -> RelayOutcome {
    let url = format!("https://signal.xydesk.my.id/turn-ice?id={device_id}&role=host");
    let outcome = match ureq::get(&url)
        .set("Authorization", &format!("Bearer {token}"))
        .timeout(std::time::Duration::from_secs(5))
        .call()
    {
        Ok(response) => {
            let status = response.status();
            match response.into_string() {
                Ok(body) => parse_response(status, &body),
                Err(error) => unavailable("network", error.to_string()),
            }
        }
        // ureq mengembalikan status non-2xx sebagai galat: badan balasannya
        // tetap dibaca supaya sebab dari server tidak hilang.
        Err(ureq::Error::Status(status, response)) => match response.into_string() {
            Ok(body) => parse_response(status, &body),
            Err(error) => unavailable("network", error.to_string()),
        },
        Err(error) => unavailable("network", error.to_string()),
    };
    record(&outcome);
    outcome
}

fn unavailable(reason: &str, detail: impl Into<String>) -> RelayOutcome {
    RelayOutcome::Unavailable {
        reason: reason.to_string(),
        detail: Some(detail.into()),
    }
}

/// Hasil terakhir + waktunya, untuk `/status` dan panel.
static LAST: Mutex<Option<(RelayOutcome, u64)>> = Mutex::new(None);

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

/// Simpan hasil terakhir. Dipanggil `fetch`; dipisah supaya bisa diuji tanpa
/// jaringan.
pub fn record(outcome: &RelayOutcome) {
    *recover_lock(&LAST) = Some((outcome.clone(), now_ms()));
}

/// Status relay terakhir sebagai JSON untuk `/status`.
///
/// `state` selalu salah satu dari `ready`, `unavailable`, atau `unknown`.
/// `unknown` berarti belum pernah dicoba — bukan berarti relay tidak ada.
pub fn telemetry() -> serde_json::Value {
    match recover_lock(&LAST).clone() {
        None => serde_json::json!({ "state": "unknown", "servers": 0 }),
        Some((RelayOutcome::Ready(servers), at_ms)) => serde_json::json!({
            "state": "ready",
            "servers": servers.len(),
            "checkedAtMs": at_ms,
        }),
        Some((RelayOutcome::Unavailable { reason, detail }, at_ms)) => serde_json::json!({
            "state": "unavailable",
            "servers": 0,
            "reason": reason,
            "label": reason_label(&reason),
            "detail": detail,
            "checkedAtMs": at_ms,
        }),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn reset() {
        *recover_lock(&LAST) = None;
    }

    #[test]
    fn balasan_siap_membaca_urls_string_dan_array() {
        let body = r#"{"iceServers":[
            {"urls":"turn:relay.example:3478","username":"u","credential":"c"},
            {"urls":["turn:relay.example:3478?transport=tcp","turns:relay.example:5349"]},
            {"credential":"tanpa-urls"}
        ]}"#;
        match parse_response(200, body) {
            RelayOutcome::Ready(servers) => {
                assert_eq!(servers.len(), 2);
                assert_eq!(servers[0].urls, vec!["turn:relay.example:3478"]);
                assert_eq!(servers[0].username, "u");
                assert_eq!(servers[1].urls.len(), 2);
                // Tanpa kredensial pun tetap dipakai: relay anonim sah.
                assert_eq!(servers[1].credential, "");
            }
            other => panic!("harusnya Ready, dapat {other:?}"),
        }
    }

    #[test]
    fn balasan_200_dengan_daftar_kosong_bukan_keberhasilan() {
        assert_eq!(
            parse_response(200, r#"{"iceServers":[],"degraded":true}"#),
            RelayOutcome::Unavailable {
                reason: "providers-failed".into(),
                detail: None
            }
        );
        assert_eq!(
            parse_response(200, r#"{"iceServers":[]}"#),
            RelayOutcome::Unavailable {
                reason: "no-servers".into(),
                detail: None
            }
        );
    }

    #[test]
    fn penolakan_membawa_sebab_rinci_dan_saran() {
        let body = r#"{"error":"turn-forbidden","reason":"token-invalid","hint":"Token signaling tidak valid."}"#;
        assert_eq!(
            parse_response(403, body),
            RelayOutcome::Unavailable {
                reason: "token-invalid".into(),
                detail: Some("Token signaling tidak valid.".into())
            }
        );
    }

    #[test]
    fn server_lama_tanpa_reason_tetap_terbaca() {
        let body = r#"{"error":"turn-not-configured","hint":"Isi TURN_STATIC_URLS."}"#;
        assert_eq!(
            parse_response(503, body),
            RelayOutcome::Unavailable {
                reason: "turn-not-configured".into(),
                detail: Some("Isi TURN_STATIC_URLS.".into())
            }
        );
    }

    #[test]
    fn badan_bukan_json_tidak_dikutip_utuh() {
        // Halaman gateway HTML: yang dicatat statusnya, bukan isinya.
        assert_eq!(
            parse_response(502, "<html>bad gateway</html>"),
            RelayOutcome::Unavailable {
                reason: "http-502".into(),
                detail: None
            }
        );
        assert_eq!(
            parse_response(200, "bukan json"),
            RelayOutcome::Unavailable {
                reason: "bad-response".into(),
                detail: None
            }
        );
    }

    #[test]
    fn telemetry_mengikuti_hasil_terakhir() {
        reset();
        assert_eq!(telemetry()["state"], "unknown");

        record(&RelayOutcome::Ready(vec![RelayServer {
            urls: vec!["turn:relay.example:3478".into()],
            username: "u".into(),
            credential: "c".into(),
        }]));
        let ready = telemetry();
        assert_eq!(ready["state"], "ready");
        assert_eq!(ready["servers"], 1);

        record(&RelayOutcome::Unavailable {
            reason: "token-invalid".into(),
            detail: Some("ambil token baru".into()),
        });
        let down = telemetry();
        assert_eq!(down["state"], "unavailable");
        assert_eq!(down["reason"], "token-invalid");
        assert_eq!(down["label"], reason_label("token-invalid"));
        assert!(down["checkedAtMs"].as_u64().unwrap_or(0) > 0);
        reset();
    }

    #[test]
    fn setiap_kode_sebab_yang_dipakai_worker_punya_label() {
        for reason in [
            "no-credentials",
            "token-invalid",
            "ticket-invalid",
            "ticket-revoked",
            "turn-forbidden",
            "turn-not-configured",
            "turn-auth-unavailable",
            "providers-failed",
            "no-servers",
            "bad-response",
            "network",
        ] {
            assert_ne!(reason_label(reason), "sebab tidak diketahui", "{reason}");
        }
        // Sebab yang belum dikenal tidak pernah ditelan diam-diam.
        assert_eq!(reason_label("hal-baru"), "sebab tidak diketahui");
    }
}
