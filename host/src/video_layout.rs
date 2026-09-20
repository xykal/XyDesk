//! Encoded canvas that carries the WHOLE desktop — tanpa pita, tanpa crop.
//!
//! Keputusan operator 2026-09-20 (menggantikan kebijakan crop 2026-09-19):
//! frame tidak boleh memuat pita hitam dan tidak boleh memotong desktop.
//! Host meminta mode desktop 16:9 yang didukung secara otomatis (lihat
//! desktop_mode.rs); bila desktop tetap bukan 16:9, frame dikirim pada rasio
//! asli desktop, diperkecil proporsional ke batas mode/level. Tidak ada
//! upscale: desktop kecil dikirim pada ukuran natifnya.
#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize)]
pub struct VideoLayout {
    pub canvas: [usize; 2],
    /// Area frame ter-encode yang memuat desktop: selalu seluruh canvas,
    /// karena tidak ada pita yang boleh dikirim.
    pub content: [usize; 4],
    /// Area desktop sumber dalam piksel desktop: selalu seluruh desktop,
    /// karena tidak ada crop yang boleh terjadi. left, top, width, height.
    pub crop: [usize; 4],
}
impl VideoLayout {
    pub fn new(width: usize, height: usize, mode: u8, level: u8) -> Result<Self, String> {
        if width < 2 || height < 2 {
            return Err("capture lebih kecil dari 2x2".into());
        }
        let (mw, mh) = if mode == 0 || level < 40 {
            (1280usize, 720usize)
        } else if mode == 2 && level >= 51 {
            (4096, 2160)
        } else {
            (1920, 1080)
        };
        let scale = (mw as f64 / width as f64)
            .min(mh as f64 / height as f64)
            .min(1.0);
        let cw = (((width as f64 * scale).round() as usize) & !1).max(2);
        let ch = (((height as f64 * scale).round() as usize) & !1).max(2);
        Ok(Self {
            canvas: [cw, ch],
            content: [0, 0, cw, ch],
            crop: [0, 0, width, height],
        })
    }
    /// Petakan koordinat frame ternormalisasi ke koordinat desktop.
    /// Seluruh frame adalah seluruh desktop: tanpa pita, tanpa crop.
    pub fn desktop_point(self, x: u16, y: u16) -> Option<(u16, u16)> {
        let axis = |n: u16, start: usize, size: usize| -> u16 {
            (start as f64 + (n as f64 / 65535.0) * (size - 1) as f64).round() as u16
        };
        Some((
            axis(x, self.crop[0], self.crop[2]),
            axis(y, self.crop[1], self.crop[3]),
        ))
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rasio_asli_tanpa_pita_tanpa_crop_tanpa_upscale() {
        // Desktop lebar aneh: diperkecil proporsional, TIDAK dipotong.
        assert_eq!(
            VideoLayout::new(2336, 1080, 0, 51).unwrap(),
            VideoLayout {
                canvas: [1280, 592],
                content: [0, 0, 1280, 592],
                crop: [0, 0, 2336, 1080]
            }
        );
        assert_eq!(
            VideoLayout::new(2336, 1080, 1, 51).unwrap().canvas,
            [1920, 888]
        );
        // Mode asli: dimensi natif dipertahankan penuh.
        assert_eq!(
            VideoLayout::new(2336, 1080, 2, 51).unwrap().canvas,
            [2336, 1080]
        );
        // Desktop tinggi: seluruh tinggi ikut, tidak ada anchor/crop.
        assert_eq!(
            VideoLayout::new(1920, 1200, 1, 40).unwrap(),
            VideoLayout {
                canvas: [1728, 1080],
                content: [0, 0, 1728, 1080],
                crop: [0, 0, 1920, 1200]
            }
        );
        // Desktop kecil: canvas natif, bukan upscale ke 720p.
        assert_eq!(
            VideoLayout::new(640, 360, 1, 51).unwrap(),
            VideoLayout {
                canvas: [640, 360],
                content: [0, 0, 640, 360],
                crop: [0, 0, 640, 360]
            }
        );
        // Desktop 16:9 murni tetap pas tanpa perubahan bentuk.
        assert_eq!(
            VideoLayout::new(1920, 1080, 1, 31).unwrap().canvas,
            [1280, 720]
        );
        assert_eq!(
            VideoLayout::new(1920, 1080, 1, 40).unwrap().canvas,
            [1920, 1080]
        );
    }
    #[test]
    fn batas_macroblock_level_terjaga() {
        for level in [31u8, 40, 51] {
            for mode in 0..=2u8 {
                for (w, h) in [(3840, 2160), (2336, 1080), (2160, 3840), (7680, 4320)] {
                    let r = VideoLayout::new(w, h, mode, level).unwrap();
                    let [cw, ch] = r.canvas;
                    assert!(cw % 2 == 0 && ch % 2 == 0);
                    assert!(
                        cw.div_ceil(16) * ch.div_ceil(16)
                            <= if mode == 0 || level < 40 {
                                3600
                            } else if mode == 2 && level >= 51 {
                                36864
                            } else {
                                8192
                            }
                    );
                }
            }
        }
    }
    #[test]
    fn seluruh_frame_input_dan_petakan_ke_seluruh_desktop() {
        let r = VideoLayout::new(2336, 1080, 1, 51).unwrap();
        assert_eq!(r.desktop_point(0, 0), Some((0, 0)));
        assert_eq!(r.desktop_point(65535, 65535), Some((2335, 1079)));
        let (x, y) = r.desktop_point(32768, 32768).unwrap();
        assert!((x as i32 - 1168).abs() <= 1 && (y as i32 - 540).abs() <= 1);
        // Round trip: koordinat desktop -> frame -> desktop lagi.
        for &(dx, dy) in &[(0, 0), (2335, 1079), (1168, 540)] {
            let fx = ((dx as f64 / 2335.0) * 65535.0).round() as u16;
            let fy = ((dy as f64 / 1079.0) * 65535.0).round() as u16;
            assert_eq!(r.desktop_point(fx, fy), Some((dx, dy)));
        }
    }
}
