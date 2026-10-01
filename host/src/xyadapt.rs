//! libxyadapt sisi host: menghaluskan permintaan bitrate dari client.
//!
//! Client (libxyadapt Android) mengirim anak tangga bitrate saat jaringan
//! berubah. Lompatan besar ke atas (3 → 18 Mbps) membuat encoder mengeluarkan
//! burst yang justru memicu drop; turun harus langsung supaya gambar tidak
//! macet. Governor ini: turun seketika, naik bertahap maksimal ×1.5 per
//! langkah dengan jeda minimal antar kenaikan, dan mengabaikan perubahan
//! kecil (<10 %) agar encoder tidak dikonfigurasi ulang tiap detik.

/// Penghalus bitrate; murni logika, tanpa I/O, mudah diuji.
#[derive(Debug, Clone)]
pub struct BitrateGovernor {
    current_bps: u32,
    last_up_ms: u64,
    min_up_gap_ms: u64,
    max_up_ratio: f32,
    min_bps: u32,
    max_bps: u32,
}

impl BitrateGovernor {
    pub fn new(current_bps: u32) -> Self {
        Self {
            current_bps,
            last_up_ms: 0,
            min_up_gap_ms: 1_000,
            max_up_ratio: 1.5,
            min_bps: 1_000_000,
            max_bps: 50_000_000,
        }
    }

    pub fn current_bps(&self) -> u32 {
        self.current_bps
    }

    /// Terapkan permintaan `wanted_bps` pada waktu `now_ms`.
    /// Mengembalikan bitrate baru bila encoder perlu dikonfigurasi ulang.
    pub fn propose(&mut self, now_ms: u64, wanted_bps: u32) -> Option<u32> {
        let wanted = wanted_bps.clamp(self.min_bps, self.max_bps);
        let cur = self.current_bps;
        if wanted < cur {
            self.current_bps = wanted;
            return Some(wanted);
        }
        if wanted == cur || Self::within_deadband(cur, wanted) {
            return None;
        }
        if now_ms.saturating_sub(self.last_up_ms) < self.min_up_gap_ms {
            return None;
        }
        let ceiling = (cur as f32 * self.max_up_ratio) as u32;
        let next = wanted.min(ceiling.max(cur + self.min_bps));
        self.current_bps = next;
        self.last_up_ms = now_ms;
        Some(next)
    }

    /// Apakah masih ada sisa kenaikan yang belum tercapai menuju `wanted_bps`.
    pub fn pending(&self, wanted_bps: u32) -> bool {
        wanted_bps.clamp(self.min_bps, self.max_bps) > self.current_bps
    }

    fn within_deadband(cur: u32, wanted: u32) -> bool {
        let diff = wanted.abs_diff(cur) as f32;
        let base = cur.max(1) as f32;
        diff / base < 0.10
    }
}

static GOVERNOR: std::sync::Mutex<Option<(BitrateGovernor, u32)>> = std::sync::Mutex::new(None);

fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

/// Permintaan bitrate dari client (bps). Mengembalikan nilai yang harus
/// diterapkan sekarang; sisa kenaikan dilanjutkan lewat [`step`].
pub fn request(current_bps: u32, wanted_bps: u32) -> Option<u32> {
    let mut slot = GOVERNOR.lock().unwrap_or_else(|e| e.into_inner());
    let (gov, wanted) =
        slot.get_or_insert_with(|| (BitrateGovernor::new(current_bps), current_bps));
    *wanted = wanted_bps;
    gov.propose(now_ms(), wanted_bps)
}

/// Sinkronkan governor ke bitrate tetap (mis. saat client memilih preset manual
/// Sedang/Tinggi/Ultra atau kembali ke default), agar [`step`] tidak menimpa
/// pilihan manual dengan sisa tangga otomatis lama.
pub fn sync(bps: u32) {
    let mut slot = GOVERNOR.lock().unwrap_or_else(|e| e.into_inner());
    let clamped = bps.clamp(1_000_000, 50_000_000);
    *slot = Some((BitrateGovernor::new(clamped), clamped));
}

/// Dipanggil berkala (≈1 detik): lanjutkan kenaikan bertahap bila masih ada.
pub fn step() -> Option<u32> {
    let mut slot = GOVERNOR.lock().unwrap_or_else(|e| e.into_inner());
    let (gov, wanted) = slot.as_mut()?;
    if !gov.pending(*wanted) {
        return None;
    }
    gov.propose(now_ms(), *wanted)
}

#[cfg(test)]
mod tests {
    use super::BitrateGovernor;

    #[test]
    fn turun_langsung() {
        let mut g = BitrateGovernor::new(12_000_000);
        assert_eq!(g.propose(0, 3_000_000), Some(3_000_000));
        assert_eq!(g.current_bps(), 3_000_000);
    }

    #[test]
    fn naik_bertahap_dengan_jeda() {
        let mut g = BitrateGovernor::new(4_000_000);
        assert_eq!(g.propose(1_000, 18_000_000), Some(6_000_000));
        assert_eq!(g.propose(1_500, 18_000_000), None);
        assert_eq!(g.propose(2_100, 18_000_000), Some(9_000_000));
        assert!(g.pending(18_000_000));
        assert_eq!(g.propose(3_200, 18_000_000), Some(13_500_000));
        assert_eq!(g.propose(4_300, 18_000_000), Some(18_000_000));
        assert!(!g.pending(18_000_000));
    }

    #[test]
    fn perubahan_kecil_diabaikan() {
        let mut g = BitrateGovernor::new(10_000_000);
        assert_eq!(g.propose(5_000, 10_500_000), None);
        assert_eq!(g.propose(5_000, 9_800_000), Some(9_800_000));
    }

    #[test]
    fn dibatasi_rentang() {
        let mut g = BitrateGovernor::new(40_000_000);
        assert_eq!(g.propose(9_000, 90_000_000), Some(50_000_000));
        assert_eq!(g.propose(9_000, 0), Some(1_000_000));
    }

    #[test]
    fn sync_menghapus_sisa_tangga_lama() {
        let _ = super::request(4_000_000, 24_000_000);
        super::sync(15_000_000);
        assert_eq!(super::step(), None);
    }
}
