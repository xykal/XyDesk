//! Aturan kecocokan perangkat (device quirks) untuk pemilihan encoder.
//!
//! Idenya meniru `streamer-config.json` kompetitor: keputusan berbasis data
//! (JSON) yang bisa diperbarui tanpa merilis ulang. Sumber utama
//! `config/device-quirks.json` yang ditanam saat build; bila ada salinan di
//! `%USERPROFILE%\.xydesk\device-quirks.json`, salinan itu yang dipakai.
//!
//! Pola dicocokkan sebagai substring tanpa memperhatikan huruf besar/kecil
//! (bukan regex penuh) — cukup untuk kelas GPU seperti "GTX 7"/"GT 9" tanpa
//! menambah dependensi.

use serde::Deserialize;
use std::sync::OnceLock;

#[derive(Debug, Deserialize, Clone)]
pub struct Rule {
    #[serde(default = "default_true")]
    pub enable: bool,
    #[serde(default)]
    pub gpu_contains: Vec<String>,
    #[serde(default)]
    pub reason: String,
}

fn default_true() -> bool {
    true
}

#[derive(Debug, Deserialize, Default, Clone)]
pub struct DeviceQuirks {
    #[serde(default)]
    pub prefer_software_encode: Vec<Rule>,
    #[serde(default)]
    pub disable_hevc: Vec<Rule>,
}

fn normalize(s: &str) -> String {
    s.to_lowercase().replace([' ', '\t'], "")
}

impl DeviceQuirks {
    fn matched<'a>(rules: &'a [Rule], gpu: &str) -> Option<&'a Rule> {
        let g = normalize(gpu);
        rules.iter().find(|r| {
            r.enable
                && r.gpu_contains
                    .iter()
                    .any(|p| !p.is_empty() && g.contains(&normalize(p)))
        })
    }

    pub fn prefer_software_encode(&self, gpu: &str) -> bool {
        Self::matched(&self.prefer_software_encode, gpu).is_some()
    }

    pub fn disable_hevc(&self, gpu: &str) -> bool {
        Self::matched(&self.disable_hevc, gpu).is_some()
    }

    /// Alasan aturan yang cocok (untuk log/telemetri), bila ada.
    pub fn note(&self, gpu: &str) -> Option<String> {
        Self::matched(&self.prefer_software_encode, gpu)
            .or_else(|| Self::matched(&self.disable_hevc, gpu))
            .map(|r| r.reason.clone())
    }
}

fn parse(text: &str) -> DeviceQuirks {
    serde_json::from_str(text).unwrap_or_default()
}

/// Aturan aktif: salinan pengguna bila ada, kalau tidak yang ditanam build.
pub fn quirks() -> &'static DeviceQuirks {
    static Q: OnceLock<DeviceQuirks> = OnceLock::new();
    Q.get_or_init(|| {
        if let Ok(profile) = std::env::var("USERPROFILE") {
            let p = std::path::Path::new(&profile)
                .join(".xydesk")
                .join("device-quirks.json");
            if let Ok(text) = std::fs::read_to_string(p) {
                let q = parse(&text);
                println!("[xydesk-host] quirks: memakai salinan pengguna");
                return q;
            }
        }
        parse(include_str!("../../config/device-quirks.json"))
    })
}

pub fn prefer_software_encode(gpu: &str) -> bool {
    quirks().prefer_software_encode(gpu)
}

pub fn disable_hevc(gpu: &str) -> bool {
    quirks().disable_hevc(gpu)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn embedded_rules_parse() {
        let q = parse(include_str!("../../config/device-quirks.json"));
        assert!(!q.prefer_software_encode.is_empty());
    }

    #[test]
    fn gpu_matching_is_case_and_space_insensitive() {
        let q = parse(include_str!("../../config/device-quirks.json"));
        assert!(q.prefer_software_encode("NVIDIA GeForce GTX 750 Ti"));
        assert!(q.prefer_software_encode("NVIDIA GeForceGTX960"));
        assert!(!q.prefer_software_encode("NVIDIA GeForce RTX 4060"));
    }

    #[test]
    fn disabled_rules_are_ignored() {
        let q = parse(r#"{"prefer_software_encode":[{"enable":false,"gpu_contains":["RTX"]}]}"#);
        assert!(!q.prefer_software_encode("RTX 4090"));
    }
}
