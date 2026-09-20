//! Encoded canvas and the desktop crop it carries — 16:9 tanpa pita.
//!
//! Keputusan operator 2026-09-19: desktop yang tidak 16:9 TIDAK boleh lagi
//! dikirim dengan bar hitam (letterbox). Sebagai gantinya frame memuat crop
//! 16:9 dari desktop: center horizontal untuk desktop lebar, anchor bawah
//! untuk desktop tinggi supaya taskbar tetap terlihat. Tidak ada upscale:
//! desktop kecil mempertahankan crop natifnya sebagai canvas.
#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize)]
pub struct VideoLayout {
    pub canvas: [usize; 2],
    /// Area frame ter-encode yang memuat desktop: selalu seluruh canvas,
    /// karena tidak ada pita yang boleh dikirim.
    pub content: [usize; 4],
    /// Crop desktop sumber dalam piksel desktop: left, top, width, height.
    pub crop: [usize; 4],
}
impl VideoLayout {
    pub fn new(width: usize, height: usize, mode: u8, level: u8) -> Result<Self, String> {
        if width < 2 || height < 2 {
            return Err("capture lebih kecil dari 2x2".into());
        }
        let (crw, crh, crx, cry) = if width * 9 > height * 16 {
            let w = (height * 16 / 9) & !1;
            (w, height, ((width - w) / 2) & !1, 0)
        } else if width * 9 < height * 16 {
            let h = (width * 9 / 16) & !1;
            (width, h, 0, (height - h) & !1)
        } else {
            (width, height, 0, 0)
        };
        if crw < 2 || crh < 2 {
            return Err("crop 16:9 lebih kecil dari 2x2".into());
        }
        let base = if mode == 2 && level >= 51 {
            (crw, crh)
        } else if mode == 0 || level < 40 {
            (1280, 720)
        } else {
            (1920, 1080)
        };
        let (cw, ch) = if crw < base.0 { (crw, crh) } else { base };
        Ok(Self {
            canvas: [cw, ch],
            content: [0, 0, cw, ch],
            crop: [crx, cry, crw, crh],
        })
    }
    /// Petakan koordinat frame ternormalisasi ke koordinat desktop crop.
    /// Seluruh frame adalah input karena tidak ada pita.
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
    fn crop_169_menggantikan_pita_dan_tanpa_upscale() {
        assert_eq!(
            VideoLayout::new(2336, 1080, 0, 51).unwrap(),
            VideoLayout {
                canvas: [1280, 720],
                content: [0, 0, 1280, 720],
                crop: [208, 0, 1920, 1080]
            }
        );
        assert_eq!(
            VideoLayout::new(2336, 1080, 1, 51).unwrap().canvas,
            [1920, 1080]
        );
        // Desktop tinggi: crop anchor bawah, taskbar tetap masuk frame.
        assert_eq!(
            VideoLayout::new(1920, 1200, 1, 40).unwrap().crop,
            [0, 120, 1920, 1080]
        );
        // Desktop kecil: canvas = crop natif, bukan upscale ke 720p.
        assert_eq!(
            VideoLayout::new(640, 360, 1, 51).unwrap(),
            VideoLayout {
                canvas: [640, 360],
                content: [0, 0, 640, 360],
                crop: [0, 0, 640, 360]
            }
        );
        assert_eq!(
            VideoLayout::new(2336, 1080, 2, 51).unwrap().canvas,
            [1920, 1080]
        );
        assert_eq!(
            VideoLayout::new(1920, 1080, 1, 31).unwrap().canvas,
            [1280, 720]
        );
    }
    #[test]
    fn seluruh_frame_input_dan_petakan_ke_crop() {
        let r = VideoLayout::new(2336, 1080, 1, 51).unwrap();
        assert_eq!(r.desktop_point(0, 0), Some((208, 0)));
        assert_eq!(r.desktop_point(65535, 65535), Some((2127, 1079)));
        let (x, y) = r.desktop_point(32768, 32768).unwrap();
        assert!((x as i32 - 1168).abs() <= 1 && (y as i32 - 540).abs() <= 1);
        // Round trip: koordinat desktop crop -> frame -> desktop lagi.
        for &(dx, dy) in &[(208, 0), (2127, 1079), (1168, 540)] {
            let fx = (((dx - 208) as f64 / 1919.0) * 65535.0).round() as u16;
            let fy = ((dy as f64 / 1079.0) * 65535.0).round() as u16;
            assert_eq!(r.desktop_point(fx, fy), Some((dx, dy)));
        }
    }
}
