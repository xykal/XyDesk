//! Bootstrap control API lewat handle pipe warisan, bukan stdout atau file.
//! Launcher memegang ujung baca dan hanya mewariskan ujung tulis ini.
use crate::control::ControlServer;

#[cfg(any(target_os = "windows", test))]
fn frame(server: &ControlServer) -> anyhow::Result<Vec<u8>> {
    let mut data = serde_json::to_vec(&serde_json::json!({
        "protocol": 1, "pid": std::process::id(),
        "url": format!("http://{}", server.addr), "token": server.token,
    }))?;
    data.push(b'\n');
    anyhow::ensure!(data.len() <= 1024, "control IPC frame terlalu besar");
    Ok(data)
}

/// Konsumsi ujung tulis pipe milik launcher; hanya digunakan sekali.
/// Handle standar dan file disk ditolak. Tidak ada fallback ke stdout.
pub fn publish(raw: usize, server: &ControlServer) -> anyhow::Result<()> {
    #[cfg(target_os = "windows")]
    {
        use windows::Win32::Foundation::{CloseHandle, HANDLE};
        use windows::Win32::Storage::FileSystem::{GetFileType, WriteFile, FILE_TYPE_PIPE};
        use windows::Win32::System::Console::{
            GetStdHandle, STD_ERROR_HANDLE, STD_INPUT_HANDLE, STD_OUTPUT_HANDLE,
        };
        use windows::Win32::System::Pipes::{
            GetNamedPipeInfo, SetNamedPipeHandleState, PIPE_NOWAIT,
        };
        let handle = HANDLE(raw as *mut core::ffi::c_void);
        anyhow::ensure!(!handle.is_invalid(), "handle control IPC tidak valid");
        unsafe {
            for standard in [STD_INPUT_HANDLE, STD_OUTPUT_HANDLE, STD_ERROR_HANDLE] {
                anyhow::ensure!(
                    GetStdHandle(standard).ok() != Some(handle),
                    "control IPC tidak boleh memakai stdio"
                );
            }
            anyhow::ensure!(
                GetFileType(handle) == FILE_TYPE_PIPE,
                "control IPC harus berupa pipe, bukan berkas log"
            );
            GetNamedPipeInfo(handle, None, None, None, None)?;
        }
        // Semua error setelah validasi tetap menutup ujung tulis supaya
        // parent melihat EOF. API Win32 menangani handle tidak valid sendiri.
        let result = (|| -> anyhow::Result<()> {
            let data = frame(server)?;
            let mut written = 0;
            unsafe {
                // Parent yang tidak membaca tidak boleh membuat startup hang.
                SetNamedPipeHandleState(handle, Some(&PIPE_NOWAIT), None, None)?;
                WriteFile(handle, Some(&data), Some(&mut written), None)?;
            }
            anyhow::ensure!(
                written as usize == data.len(),
                "control IPC tidak menerima frame utuh"
            );
            Ok(())
        })();
        unsafe {
            let _ = CloseHandle(handle);
        }
        result
    }
    #[cfg(not(target_os = "windows"))]
    {
        let _ = (raw, server);
        anyhow::bail!("--control-info-handle hanya didukung pada Windows");
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn server() -> ControlServer {
        ControlServer {
            addr: "127.0.0.1:43210".parse().unwrap(),
            token: "fixture-only".into(),
        }
    }
    #[test]
    fn frame_bootstrap_kecil_dan_tidak_memuat_password() {
        let data = frame(&server()).unwrap();
        assert!(data.len() <= 1024 && data.ends_with(b"\n"));
        let value: serde_json::Value = serde_json::from_slice(&data).unwrap();
        assert_eq!(value["protocol"], 1);
        assert!(value.get("password").is_none());
        assert!(value.get("token").is_some());
    }
    #[cfg(target_os = "windows")]
    #[test]
    fn pipe_bootstrap_ditulis_sekali_lalu_eof() {
        use std::io::Read;
        use std::os::windows::io::FromRawHandle;
        use windows::Win32::Foundation::HANDLE;
        use windows::Win32::System::Pipes::CreatePipe;
        let mut read = HANDLE::default();
        let mut write = HANDLE::default();
        unsafe {
            CreatePipe(&mut read, &mut write, None, 4096).unwrap();
        }
        let mut reader = unsafe { std::fs::File::from_raw_handle(read.0) };
        publish(write.0 as usize, &server()).unwrap();
        let mut data = Vec::new();
        reader.read_to_end(&mut data).unwrap();
        assert_eq!(data, frame(&server()).unwrap());
    }
    #[cfg(target_os = "windows")]
    #[test]
    fn pipe_penuh_gagal_tanpa_menunggu_parent() {
        use std::os::windows::io::FromRawHandle;
        use windows::Win32::Foundation::HANDLE;
        use windows::Win32::Storage::FileSystem::WriteFile;
        use windows::Win32::System::Pipes::{CreatePipe, SetNamedPipeHandleState, PIPE_NOWAIT};
        let mut read = HANDLE::default();
        let mut write = HANDLE::default();
        unsafe {
            CreatePipe(&mut read, &mut write, None, 4096).unwrap();
        }
        let _reader = unsafe { std::fs::File::from_raw_handle(read.0) };
        unsafe {
            SetNamedPipeHandleState(write, Some(&PIPE_NOWAIT), None, None).unwrap();
            let mut full = false;
            // A large nonblocking write can fail while a small frame still fits.
            // Fill byte by byte so backpressure proves there is no spare byte.
            let mut filled = 0usize;
            for _ in 0..1_048_576 {
                let mut written = 0;
                if WriteFile(write, Some(&[0]), Some(&mut written), None).is_err() || written == 0 {
                    full = true;
                    break;
                }
                filled += written as usize;
            }
            assert!(filled > 0, "fixture pipe tidak pernah menerima data");
            assert!(full, "fixture pipe tidak mencapai backpressure");
        }
        let started = std::time::Instant::now();
        assert!(publish(write.0 as usize, &server()).is_err());
        assert!(started.elapsed() < std::time::Duration::from_secs(2));
    }

    #[cfg(target_os = "windows")]
    #[test]
    fn berkas_log_tidak_boleh_menerima_bearer() {
        use std::os::windows::io::AsRawHandle;
        let path = std::env::temp_dir().join(format!("xydesk-ipc-{}.log", rand::random::<u64>()));
        let file = std::fs::File::create(&path).unwrap();
        assert!(publish(file.as_raw_handle() as usize, &server()).is_err());
        assert_eq!(file.metadata().unwrap().len(), 0);
        drop(file);
        std::fs::remove_file(path).unwrap();
    }
}
