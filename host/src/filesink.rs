//! Menulis berkas masuk ke disk dengan aman.
//!
//! Aturannya satu: **tidak ada berkas setengah jadi yang pernah terlihat
//! dengan nama aslinya.** Potongan ditulis ke berkas sementara berakhiran
//! `.xypart`, dan baru setelah [`crate::filetransfer::Receiver`] menyatakan
//! SHA-256-nya cocok, berkas itu dipindahkan ke nama finalnya. Transfer yang
//! putus di tengah meninggalkan `.xypart` yang jelas sampahnya, bukan
//! `laporan.pdf` rusak yang dibuka pengguna besok pagi dan disangka korup.
//!
//! Dua hal lagi yang ditangani di sini karena keduanya hanya muncul saat
//! menyentuh disk sungguhan:
//!
//! 1. **Tabrakan nama.** Kalau `laporan.pdf` sudah ada, berkas baru menjadi
//!    `laporan (2).pdf`, bukan menimpanya. Menimpa berkas pengguna karena
//!    nama kebetulan sama adalah kehilangan data, bukan transfer.
//! 2. **Nama yang sudah dibersihkan tetap bisa menunjuk ke luar.** Jalur
//!    final diperiksa ulang harus benar-benar berada di dalam folder tujuan.

use std::fs::{self, File};
use std::io::Write;
use std::path::{Path, PathBuf};

/// Akhiran berkas sementara selama transfer berlangsung.
pub const PART_SUFFIX: &str = ".xypart";

/// Batas percobaan penomoran saat nama bertabrakan.
const MAX_DEDUP: u32 = 999;

/// Kesalahan penulisan. Sengaja kasar: sisi protokol hanya perlu tahu bahwa
/// disk menolak, bukan detail errno.
#[derive(Debug)]
pub enum SinkError {
    /// Folder tujuan tidak bisa dibuat atau tidak bisa ditulisi.
    Directory(std::io::Error),
    /// Gagal membuat atau menulis berkas sementara.
    Write(std::io::Error),
    /// Gagal memindahkan berkas sementara ke nama finalnya.
    Commit(std::io::Error),
    /// Nama final akan keluar dari folder tujuan — transfer dihentikan.
    Escapes,
    /// Nama sudah dipakai sampai batas penomoran.
    NoFreeName,
}

impl std::fmt::Display for SinkError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            SinkError::Directory(e) => write!(f, "folder tujuan tidak bisa dipakai: {e}"),
            SinkError::Write(e) => write!(f, "gagal menulis berkas sementara: {e}"),
            SinkError::Commit(e) => write!(f, "gagal menyimpan berkas: {e}"),
            SinkError::Escapes => write!(f, "nama berkas menunjuk ke luar folder tujuan"),
            SinkError::NoFreeName => write!(f, "nama berkas sudah dipakai"),
        }
    }
}

/// Berkas sementara yang sedang ditulis.
///
/// Kalau nilai ini jatuh tanpa [`Sink::commit`], berkas sementaranya dihapus.
/// Itu disengaja: pembatalan, sesi putus, dan panik semuanya berakhir tanpa
/// meninggalkan sampah di folder unduhan pengguna.
pub struct Sink {
    part: PathBuf,
    final_name: String,
    dir: PathBuf,
    file: Option<File>,
    written: u64,
}

impl Sink {
    /// Menyiapkan berkas sementara di `dir` untuk nama yang **sudah**
    /// dibersihkan oleh [`crate::filetransfer::sanitize_name`].
    pub fn create(dir: &Path, safe_name: &str) -> Result<Sink, SinkError> {
        fs::create_dir_all(dir).map_err(SinkError::Directory)?;
        // Sabuk kedua setelah sanitize_name: kalau nama yang sampai ke sini
        // masih mengandung pemisah jalur atau membuat hasilnya keluar dari
        // folder tujuan, berhenti. Satu lapis pemeriksaan untuk sesuatu yang
        // menulis ke disk orang lain tidak cukup.
        if safe_name.contains('/') || safe_name.contains('\\') || safe_name.contains("..") {
            return Err(SinkError::Escapes);
        }
        let target = dir.join(safe_name);
        if target.parent() != Some(dir) {
            return Err(SinkError::Escapes);
        }
        let part = dir.join(format!("{safe_name}{PART_SUFFIX}"));
        let file = File::create(&part).map_err(SinkError::Write)?;
        Ok(Sink {
            part,
            final_name: safe_name.to_string(),
            dir: dir.to_path_buf(),
            file: Some(file),
            written: 0,
        })
    }

    /// Menulis satu potongan.
    pub fn write(&mut self, data: &[u8]) -> Result<(), SinkError> {
        let file = self.file.as_mut().ok_or(SinkError::Escapes)?;
        file.write_all(data).map_err(SinkError::Write)?;
        self.written += data.len() as u64;
        Ok(())
    }

    /// Byte yang sudah benar-benar sampai di disk.
    pub fn written(&self) -> u64 {
        self.written
    }

    /// Jalur berkas sementara — untuk pesan log, bukan untuk ditampilkan ke
    /// pengguna.
    pub fn part_path(&self) -> &Path {
        &self.part
    }

    /// Menutup berkas dan memindahkannya ke nama final. Dipanggil **hanya**
    /// setelah hash terverifikasi. Mengembalikan jalur final.
    pub fn commit(mut self) -> Result<PathBuf, SinkError> {
        if let Some(file) = self.file.take() {
            // Sinkron ke disk sebelum rename: rename yang menang balapan
            // dengan cache tulis akan memberi nama final pada isi yang belum
            // seluruhnya turun.
            file.sync_all().map_err(SinkError::Commit)?;
            drop(file);
        }
        let target = unique_path(&self.dir, &self.final_name)?;
        fs::rename(&self.part, &target).map_err(SinkError::Commit)?;
        Ok(target)
    }

    /// Membuang berkas sementara secara eksplisit.
    pub fn discard(self) {}
}

impl Drop for Sink {
    fn drop(&mut self) {
        if self.file.is_some() {
            let _ = fs::remove_file(&self.part);
        }
    }
}

/// Mencari nama yang belum dipakai: `laporan.pdf` → `laporan (2).pdf` → …
///
/// Penomoran disisipkan sebelum ekstensi, bukan ditempel di belakang, supaya
/// `laporan.pdf (2)` tidak kehilangan kaitannya dengan aplikasi pembuka.
pub fn unique_path(dir: &Path, name: &str) -> Result<PathBuf, SinkError> {
    let first = dir.join(name);
    if !first.exists() {
        return Ok(first);
    }
    let (stem, ext) = match name.rsplit_once('.') {
        // Nama seperti ".gitignore" tidak punya stem — titik di depan bukan
        // pemisah ekstensi.
        Some((stem, ext)) if !stem.is_empty() => (stem.to_string(), format!(".{ext}")),
        _ => (name.to_string(), String::new()),
    };
    for n in 2..=MAX_DEDUP {
        let candidate = dir.join(format!("{stem} ({n}){ext}"));
        if !candidate.exists() {
            return Ok(candidate);
        }
    }
    Err(SinkError::NoFreeName)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn folder_uji(nama: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "xydesk-filesink-{nama}-{}",
            std::process::id() as u64 * 1_000_000 + nama.len() as u64
        ));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn berkas_final_baru_muncul_setelah_commit() {
        let dir = folder_uji("commit");
        let mut sink = Sink::create(&dir, "catatan.txt").unwrap();
        sink.write(b"halo ").unwrap();
        sink.write(b"dunia").unwrap();
        // Sebelum commit, yang ada hanya .xypart.
        assert!(!dir.join("catatan.txt").exists());
        assert!(dir.join("catatan.txt.xypart").exists());
        let hasil = sink.commit().unwrap();
        assert_eq!(hasil, dir.join("catatan.txt"));
        assert_eq!(fs::read(&hasil).unwrap(), b"halo dunia");
        assert!(!dir.join("catatan.txt.xypart").exists());
        fs::remove_dir_all(&dir).unwrap();
    }

    #[test]
    fn transfer_yang_dibuang_tidak_meninggalkan_apa_pun() {
        let dir = folder_uji("buang");
        {
            let mut sink = Sink::create(&dir, "setengah.bin").unwrap();
            sink.write(&[0u8; 1024]).unwrap();
            sink.discard();
        }
        assert_eq!(fs::read_dir(&dir).unwrap().count(), 0);
        fs::remove_dir_all(&dir).unwrap();
    }

    #[test]
    fn sink_yang_jatuh_tanpa_commit_membersihkan_dirinya() {
        let dir = folder_uji("drop");
        {
            let mut sink = Sink::create(&dir, "putus.bin").unwrap();
            sink.write(b"sebagian").unwrap();
            // Jatuh di sini, seperti saat sesi putus di tengah transfer.
        }
        assert_eq!(fs::read_dir(&dir).unwrap().count(), 0);
        fs::remove_dir_all(&dir).unwrap();
    }

    #[test]
    fn nama_yang_bertabrakan_dinomori_bukan_ditimpa() {
        let dir = folder_uji("tabrakan");
        fs::write(dir.join("laporan.pdf"), b"punya pengguna").unwrap();
        let mut sink = Sink::create(&dir, "laporan.pdf").unwrap();
        sink.write(b"kiriman").unwrap();
        let hasil = sink.commit().unwrap();
        assert_eq!(hasil, dir.join("laporan (2).pdf"));
        // Berkas lama tetap utuh — ini syarat, bukan detail.
        assert_eq!(
            fs::read(dir.join("laporan.pdf")).unwrap(),
            b"punya pengguna"
        );
        fs::remove_dir_all(&dir).unwrap();
    }

    #[test]
    fn penomoran_naik_terus_selama_masih_bertabrakan() {
        let dir = folder_uji("naik");
        fs::write(dir.join("a.txt"), b"1").unwrap();
        fs::write(dir.join("a (2).txt"), b"2").unwrap();
        fs::write(dir.join("a (3).txt"), b"3").unwrap();
        assert_eq!(unique_path(&dir, "a.txt").unwrap(), dir.join("a (4).txt"));
        fs::remove_dir_all(&dir).unwrap();
    }

    #[test]
    fn nomor_disisipkan_sebelum_ekstensi() {
        let dir = folder_uji("ekstensi");
        fs::write(dir.join("arsip.tar.gz"), b"x").unwrap();
        assert_eq!(
            unique_path(&dir, "arsip.tar.gz").unwrap(),
            dir.join("arsip.tar (2).gz")
        );
        fs::write(dir.join(".gitignore"), b"x").unwrap();
        assert_eq!(
            unique_path(&dir, ".gitignore").unwrap(),
            dir.join(".gitignore (2)")
        );
        fs::remove_dir_all(&dir).unwrap();
    }

    #[test]
    fn nama_dengan_pemisah_jalur_ditolak_walau_lolos_ke_sini() {
        let dir = folder_uji("lolos");
        assert!(matches!(
            Sink::create(&dir, "../keluar.txt"),
            Err(SinkError::Escapes)
        ));
        assert!(matches!(
            Sink::create(&dir, "sub/dalam.txt"),
            Err(SinkError::Escapes)
        ));
        assert!(matches!(
            Sink::create(&dir, "sub\\dalam.txt"),
            Err(SinkError::Escapes)
        ));
        assert_eq!(fs::read_dir(&dir).unwrap().count(), 0);
        fs::remove_dir_all(&dir).unwrap();
    }

    #[test]
    fn folder_tujuan_dibuat_bila_belum_ada() {
        let dir = folder_uji("buatfolder").join("unduhan").join("xydesk");
        let mut sink = Sink::create(&dir, "baru.txt").unwrap();
        sink.write(b"isi").unwrap();
        let hasil = sink.commit().unwrap();
        assert!(hasil.exists());
        fs::remove_dir_all(dir.parent().unwrap().parent().unwrap()).unwrap();
    }

    #[test]
    fn jumlah_byte_tertulis_dihitung() {
        let dir = folder_uji("hitung");
        let mut sink = Sink::create(&dir, "angka.bin").unwrap();
        assert_eq!(sink.written(), 0);
        sink.write(&[1, 2, 3]).unwrap();
        sink.write(&[4, 5]).unwrap();
        assert_eq!(sink.written(), 5);
        assert!(sink.part_path().ends_with("angka.bin.xypart"));
        sink.commit().unwrap();
        fs::remove_dir_all(&dir).unwrap();
    }
}
