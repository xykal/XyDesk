//! Canvas encode yang memuat SELURUH desktop — tanpa stretch, tanpa crop.
//!
//! Keputusan operator 2026-09-20 (menggantikan kebijakan crop 2026-09-19):
//! frame tidak boleh menarik atau memotong desktop. Sejak 2026-09-26 mode HD
//! tidak lagi memasang bar letterbox untuk sumber lebar (kasus lapangan VPS
//! RDP 1948x900 yang dikeluhkan pemilik): canvas mengikuti rasio sumber dengan
//! tinggi 720 selama budget macroblock level memungkinkan, di bawah itu
//! mengecil proporsional — rasio selalu aman. Sumber sempit (lebih tinggi dari
//! 16:9) tetap memakai canvas 1280x720 dengan letterbox internal karena batas
//! macroblock 3600 tidak memuat kotak minimum yang lebih tinggi pada rasio
//! itu; input memakai contentRect sehingga area hitam tidak menerima klik.
//! Host meminta mode desktop 16:9 yang didukung secara otomatis (lihat
//! desktop_mode.rs); bila desktop tetap bukan 16:9, aturan di atas berlaku.
#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize)]
pub struct VideoLayout {
    pub canvas: [usize; 2],
    /// Area frame ter-encode yang memuat desktop. Pada sumber sempit area ini
    /// dipusatkan di canvas 1280x720 agar rasio sumber tidak tertarik.
    pub content: [usize; 4],
    /// Area desktop sumber dalam piksel desktop: selalu seluruh desktop,
    /// karena tidak ada crop yang boleh terjadi. left, top, width, height.
    pub crop: [usize; 4],
}

fn macroblock(dims: [usize; 2]) -> usize {
    dims[0].div_ceil(16) * dims[1].div_ceil(16)
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
        let aspect = width as f64 / height as f64;
        // Mode HD: sumber lebar (>= 16:9) mendapat canvas seurutan rasio —
        // tanpa bar letterbox. Tinggi mulai 720; bila budget macroblock
        // (3600, kontrak level HD) tidak cukup, mengecil proporsional.
        let hd_lebar = mode == 0 && aspect >= 16.0 / 9.0;
        let mut canvas = if hd_lebar {
            let mut h = 720.0f64;
            loop {
                let w = h * aspect;
                let dims = [(w as usize & !1).max(2), (h as usize & !1).max(2)];
                if macroblock(dims) <= 3600 {
                    break dims;
                }
                h -= 2.0;
            }
        } else if mode == 0 {
            [mw, mh]
        } else {
            let scale = (mw as f64 / width as f64).min(mh as f64 / height as f64);
            let scale = if mode == 0 { scale } else { scale.min(1.0) };
            [
                (((width as f64 * scale).round() as usize) & !1).max(2),
                (((height as f64 * scale).round() as usize) & !1).max(2),
            ]
        };
        if mode != 0 && macroblock(canvas) > 3600 && level < 40 {
            canvas = [mw, mh];
        }
        let content = if hd_lebar || mode != 0 {
            [0, 0, canvas[0], canvas[1]]
        } else {
            let scale = (mw as f64 / width as f64).min(mh as f64 / height as f64);
            let cw = (((width as f64 * scale).round() as usize) & !1)
                .max(2)
                .min(mw);
            let ch = (((height as f64 * scale).round() as usize) & !1)
                .max(2)
                .min(mh);
            [(mw - cw) / 2, (mh - ch) / 2, cw, ch]
        };
        Ok(Self {
            canvas,
            content,
            crop: [0, 0, width, height],
        })
    }
    /// Petakan koordinat desktop ternormalisasi ke koordinat desktop.
    /// `contentRect` ditangani client sebelum koordinat ini dikirim; sumber
    /// selalu seluruh desktop tanpa crop.
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
    fn hd_lebar_tanpa_bar_tanpa_stretch() {
        // VPS RDP lebar (kasus lapangan 1948x900): canvas mengikuti rasio,
        // seluruh frame adalah konten — tidak ada bar letterbox.
        let r = VideoLayout::new(1948, 900, 0, 31).unwrap();
        assert_eq!(r.content, [0, 0, r.canvas[0], r.canvas[1]]);
        assert!(macroblock(r.canvas) <= 3600);
        let aspek_canvas = r.canvas[0] as f64 / r.canvas[1] as f64;
        assert!((aspek_canvas - 1948.0 / 900.0).abs() < 0.01);
        // Selama budget macroblock cukup, tinggi HD 720 dipertahankan.
        assert_eq!(
            VideoLayout::new(1920, 1080, 0, 31).unwrap().canvas,
            [1280, 720]
        );
        // 16:9 murni tetap pas tanpa perubahan bentuk.
        assert_eq!(
            VideoLayout::new(940, 529, 0, 31).unwrap().canvas,
            [1280, 720]
        );
    }
    #[test]
    fn hd_sempit_letterbox_internal() {
        // Desktop tinggi: canvas HD 1280x720, konten dipusatkan tanpa stretch.
        let r = VideoLayout::new(1080, 1920, 0, 31).unwrap();
        assert_eq!(r.canvas, [1280, 720]);
        assert_eq!(r.content[2] % 2, 0);
        assert_eq!(r.content[3] % 2, 0);
        assert!(r.content[2] < 1280 || r.content[3] < 720);
    }
    #[test]
    fn mode_asli_tanpa_stretch_tanpa_crop() {
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
        // Mode 1 jujur untuk sumber kecil; tidak mengarang detail.
        assert_eq!(
            VideoLayout::new(640, 360, 1, 51).unwrap(),
            VideoLayout {
                canvas: [640, 360],
                content: [0, 0, 640, 360],
                crop: [0, 0, 640, 360]
            }
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
