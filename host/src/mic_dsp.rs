//! XyDesk Virtual Microphone DSP Engine — pemrosesan suara mikrofon real-time
//! (High-Pass 85 Hz, Noise Suppression & Noise Gate adaptif, Mic Gain Boost,
//! dan Soft-Knee Peak Limiter/AGC) sebelum di-render ke `XyDesk Virtual Microphone`.

use std::sync::Mutex;

#[derive(Clone, Copy, Debug, PartialEq, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MicDspConfig {
    pub noise_suppression: bool,
    pub high_pass: bool,
    pub agc_limiter: bool,
    pub noise_gate_db: f32,
    pub gain_db: f32,
}

impl Default for MicDspConfig {
    fn default() -> Self {
        Self {
            noise_suppression: true,
            high_pass: true,
            agc_limiter: true,
            noise_gate_db: -42.0,
            gain_db: 6.0,
        }
    }
}

pub struct MicDspState {
    pub config: MicDspConfig,
    hp_prev_in: [f32; 2],
    hp_prev_out: [f32; 2],
    envelope: f32,
    gate_gain: f32,
    last_peak: f32,
}

impl Default for MicDspState {
    fn default() -> Self {
        Self {
            config: MicDspConfig::default(),
            hp_prev_in: [0.0; 2],
            hp_prev_out: [0.0; 2],
            envelope: 0.0,
            gate_gain: 0.0,
            last_peak: 0.0,
        }
    }
}

impl MicDspState {
    pub fn process_i16(&mut self, samples: &mut [i16], channels: usize) {
        if channels == 0 || samples.is_empty() {
            return;
        }
        let cfg = self.config;
        let linear_gain = 10.0_f32.powf(cfg.gain_db.clamp(-12.0, 24.0) / 20.0);
        let gate_thresh = 10.0_f32.powf(cfg.noise_gate_db.clamp(-70.0, -15.0) / 20.0);
        // 85 Hz 1st-order high-pass @ 48 kHz: alpha ≈ 0.989
        const HP_ALPHA: f32 = 0.989;
        let mut frame_peak = 0.0_f32;

        for frame in samples.chunks_exact_mut(channels) {
            for (ch, sample_ref) in frame.iter_mut().enumerate() {
                let c = ch.min(1);
                let mut x = f32::from(*sample_ref) / 32_768.0;

                if cfg.high_pass {
                    let y = HP_ALPHA * (self.hp_prev_out[c] + x - self.hp_prev_in[c]);
                    self.hp_prev_in[c] = x;
                    self.hp_prev_out[c] = y;
                    x = y;
                }

                let abs_x = x.abs();
                if abs_x > self.envelope {
                    self.envelope = self.envelope * 0.85 + abs_x * 0.15;
                } else {
                    self.envelope = self.envelope * 0.997 + abs_x * 0.003;
                }

                if cfg.noise_suppression {
                    let target_gate = if self.envelope < gate_thresh {
                        let ratio = (self.envelope / gate_thresh.max(1e-6)).clamp(0.0, 1.0);
                        ratio * ratio
                    } else {
                        1.0
                    };
                    if target_gate > self.gate_gain {
                        self.gate_gain = self.gate_gain * 0.80 + target_gate * 0.20;
                    } else {
                        self.gate_gain = self.gate_gain * 0.992 + target_gate * 0.008;
                    }
                    x *= self.gate_gain;
                }

                x *= linear_gain;

                if cfg.agc_limiter {
                    // Soft-knee saturation di atas 0.75 (-2.5 dBFS) agar suara kencang tidak pecah
                    let ax = x.abs();
                    if ax > 0.75 {
                        let excess = ax - 0.75;
                        let compressed = 0.75 + 0.24 * (excess / (0.24 + excess));
                        x = compressed.copysign(x);
                    }
                }

                x = x.clamp(-1.0, 1.0);
                if x.abs() > frame_peak {
                    frame_peak = x.abs();
                }
                *sample_ref = (x * 32_767.0) as i16;
            }
        }
        self.last_peak = frame_peak;
    }
}

static STATE: Mutex<Option<MicDspState>> = Mutex::new(None);

pub fn get_config() -> MicDspConfig {
    let mut guard = STATE.lock().unwrap_or_else(|e| e.into_inner());
    guard.get_or_insert_with(MicDspState::default).config
}

pub fn set_config(cfg: MicDspConfig) -> MicDspConfig {
    let mut guard = STATE.lock().unwrap_or_else(|e| e.into_inner());
    let st = guard.get_or_insert_with(MicDspState::default);
    st.config = MicDspConfig {
        noise_suppression: cfg.noise_suppression,
        high_pass: cfg.high_pass,
        agc_limiter: cfg.agc_limiter,
        noise_gate_db: cfg.noise_gate_db.clamp(-70.0, -15.0),
        gain_db: cfg.gain_db.clamp(-12.0, 24.0),
    };
    st.config
}

pub fn process_frame(samples: &mut [i16], channels: usize) {
    let mut guard = STATE.lock().unwrap_or_else(|e| e.into_inner());
    let st = guard.get_or_insert_with(MicDspState::default);
    st.process_i16(samples, channels);
}

pub fn telemetry() -> serde_json::Value {
    let mut guard = STATE.lock().unwrap_or_else(|e| e.into_inner());
    let st = guard.get_or_insert_with(MicDspState::default);
    serde_json::json!({
        "noiseSuppression": st.config.noise_suppression,
        "highPass": st.config.high_pass,
        "agcLimiter": st.config.agc_limiter,
        "noiseGateDb": st.config.noise_gate_db,
        "gainDb": st.config.gain_db,
        "peakLevel": st.last_peak,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn desis_hening_diredam_oleh_noise_gate() {
        let mut dsp = MicDspState::default();
        let mut hiss = vec![12i16; 960 * 2];
        dsp.process_i16(&mut hiss, 2);
        let max_after = hiss.iter().map(|s| s.unsigned_abs()).max().unwrap_or(0);
        assert!(max_after < 12, "desis pelan harus diredam: {max_after}");
    }

    #[test]
    fn suara_vokal_diperkuat_dan_tidak_clip() {
        let mut dsp = MicDspState::default();
        let mut voice: Vec<i16> = (0..960 * 2)
            .map(|i| ((i as f32 * 0.1).sin() * 14_000.0) as i16)
            .collect();
        dsp.process_i16(&mut voice, 2);
        let peak = voice.iter().map(|s| s.unsigned_abs()).max().unwrap_or(0);
        assert!(peak > 16_000, "suara vokal harus mendapat gain boost");
        assert!(peak <= 32_767);
    }
}
