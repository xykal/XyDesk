//! Virtual Mic Driver — biar mic client (HP → PC) kebaca sebagai input di Windows
//! seperti AnyDesk/RustDesk, dan mic host denyut di Control Panel.

#[cfg(target_os = "windows")]
use std::process::Command;

#[cfg(target_os = "windows")]
const VIRTUAL_MIC_KEYWORDS: &[&str] = &[
    "xydesk virtual microphone",
    "xydesk",
    "cable",       // VB-CABLE
    "voicemeeter", // VoiceMeeter
    "virtual",     // generic virtual
    "vb-audio",
    "vac", // Virtual Audio Cable
];

#[cfg(target_os = "windows")]
const VIRTUAL_SPEAKER_KEYWORDS: &[&str] = &[
    "xydesk virtual audio",
    "xydesk",
    "cable input",       // VB-CABLE Input (render)
    "cable in",          // VB-CABLE In 16 Ch
    "voicemeeter input", // VoiceMeeter Input
    "virtual input",
    "vb-audio",
];

/// Status virtual mic
#[derive(Clone, Debug)]
pub struct VirtualMicStatus {
    pub needed: bool,
    pub installed: bool,
    pub has_virtual_input: bool,
    pub has_virtual_output: bool,
    pub render_target: String,
}

#[cfg(target_os = "windows")]
fn kernel_driver_installed() -> bool {
    let sys10 = std::path::Path::new(r"C:\Windows\System32\drivers\vbaudio_cable64_win10.sys");
    let sys7 = std::path::Path::new(r"C:\Windows\System32\drivers\vbaudio_cable64_win7.sys");
    if sys10.is_file() || sys7.is_file() {
        return true;
    }
    if let Ok(out) = Command::new("sc.exe")
        .args(["query", "VBAudioVACMME"])
        .output()
    {
        if out.status.success() {
            return true;
        }
    }
    false
}

#[cfg(target_os = "windows")]
pub fn get_status() -> VirtualMicStatus {
    let outputs = crate::audio::list_outputs_detailed();
    let inputs = crate::audio::list_inputs_detailed();

    let mut has_virtual_input = outputs.iter().any(|(_, name)| {
        let n = name.to_lowercase();
        VIRTUAL_SPEAKER_KEYWORDS.iter().any(|k| n.contains(k))
    });
    let mut has_virtual_output = inputs.iter().any(|(_, name)| {
        let n = name.to_lowercase();
        VIRTUAL_MIC_KEYWORDS.iter().any(|k| n.contains(k))
    });

    let sys_driver = kernel_driver_installed();
    if sys_driver {
        has_virtual_input = true;
        has_virtual_output = true;
    }

    let installed = has_virtual_input || has_virtual_output;

    let render_target = if let Some((id, name)) = outputs.iter().find(|(_, name)| {
        let n = name.to_lowercase();
        n.contains("xydesk virtual audio")
            || n.contains("xydesk")
            || n.contains("cable input")
            || n.contains("cable in")
            || n.contains("voicemeeter input")
            || n.contains("vb-audio")
    }) {
        format!("{} ({})", name, id)
    } else if sys_driver {
        "XyDesk Virtual Microphone (CABLE Input -> CABLE Output)".to_string()
    } else if let Some((id, name)) = outputs.first() {
        format!("{} ({}) [default speaker]", name, id)
    } else {
        "tidak ada output device".to_string()
    };

    let needed = !installed;

    VirtualMicStatus {
        needed,
        installed,
        has_virtual_input,
        has_virtual_output,
        render_target,
    }
}

#[cfg(not(target_os = "windows"))]
pub fn get_status() -> VirtualMicStatus {
    VirtualMicStatus {
        needed: false,
        installed: false,
        has_virtual_input: false,
        has_virtual_output: false,
        render_target: "non-windows".to_string(),
    }
}

#[cfg(target_os = "windows")]
pub fn is_driver_installed() -> bool {
    get_status().installed
}

#[cfg(not(target_os = "windows"))]
pub fn is_driver_installed() -> bool {
    false
}

/// Coba cari device ID untuk render client mic — prioritas virtual cable
#[cfg(target_os = "windows")]
pub fn get_render_device_id() -> Option<String> {
    let outputs = crate::audio::list_outputs_detailed();

    for (id, name) in &outputs {
        let n = name.to_lowercase();
        if n.contains("xydesk virtual audio") || n.contains("cable input") || n.contains("cable in")
        {
            return Some(id.clone());
        }
    }
    for (id, name) in &outputs {
        if name.to_lowercase().contains("voicemeeter input") {
            return Some(id.clone());
        }
    }
    for (id, name) in &outputs {
        let n = name.to_lowercase();
        if (n.contains("virtual") && n.contains("input"))
            || n.contains("vb-audio")
            || n.contains("xydesk")
        {
            return Some(id.clone());
        }
    }
    None
}

#[cfg(not(target_os = "windows"))]
pub fn get_render_device_id() -> Option<String> {
    None
}

#[cfg(target_os = "windows")]
fn locate_vdd_ctl() -> Option<std::path::PathBuf> {
    let exe = std::env::current_exe().ok()?;
    let dir = exe.parent()?;
    let candidates = [
        dir.join("drivers")
            .join("IddSampleDriver")
            .join("xydesk-vdd-ctl.exe"),
        dir.join("xydesk-vdd-ctl.exe"),
        std::path::PathBuf::from(
            r"C:\Program Files\XyDesk\drivers\IddSampleDriver\xydesk-vdd-ctl.exe",
        ),
    ];
    candidates.into_iter().find(|p| p.is_file())
}

#[cfg(target_os = "windows")]
fn locate_audio_dir() -> Option<std::path::PathBuf> {
    let mut dirs = Vec::new();
    if let Ok(exe) = std::env::current_exe() {
        if let Some(parent) = exe.parent() {
            dirs.push(parent.join("drivers").join("audio"));
            dirs.push(parent.join("driver"));
        }
    }
    if let Ok(local) = std::env::var("LOCALAPPDATA") {
        dirs.push(
            std::path::PathBuf::from(local)
                .join("Programs")
                .join("XyDesk")
                .join("drivers")
                .join("audio"),
        );
    }
    dirs.push(std::path::PathBuf::from(
        r"C:\Program Files\XyDesk\drivers\audio",
    ));
    dirs.push(std::path::PathBuf::from(r"./drivers/audio"));
    dirs.push(std::path::PathBuf::from(r"../drivers/audio"));
    dirs.push(std::path::PathBuf::from(r"C:\Program Files\VB\CABLE"));
    dirs.push(std::path::PathBuf::from(r"C:\VBCABLE"));

    dirs.into_iter().find(|d| {
        d.join("VBCABLE_Setup_x64.exe").is_file()
            || d.join("vbMmeCable64_win10.inf").is_file()
            || d.join("install-audio.bat").is_file()
    })
}

/// Install VB-CABLE via xydesk-vdd-ctl.exe / install-audio.bat / VBCABLE_Setup_x64.exe
#[cfg(target_os = "windows")]
#[rustfmt::skip]
pub fn try_install_driver() -> Result<String, String> {
    let audio_dir = locate_audio_dir();
    let audio_dir_str = audio_dir
        .as_ref()
        .map(|p| p.to_string_lossy().to_string())
        .unwrap_or_default();

    if crate::virtual_display::is_admin() {
        if let Some(ctl) = locate_vdd_ctl() {
            let mut cmd = Command::new(&ctl);
            cmd.arg("install-audio");
            if !audio_dir_str.is_empty() {
                cmd.args(["--dir", &audio_dir_str]);
            }
            if let Ok(out) = cmd.output() {
                if out.status.success() {
                    return Ok("VB-CABLE terpasang dan layanan audio aktif.".into());
                }
            }
        }
        if let Some(dir) = &audio_dir {
            let bat = dir.join("install-audio.bat");
            if bat.is_file() {
                if let Ok(out) = Command::new("cmd.exe")
                    .current_dir(dir)
                    .args(["/D", "/C", &bat.to_string_lossy(), "/silent"])
                    .output()
                {
                    if out.status.success() {
                        return Ok("VB-CABLE terpasang via install-audio.bat.".into());
                    }
                }
            }
            let exe = dir.join("VBCABLE_Setup_x64.exe");
            if exe.is_file() {
                let output = Command::new(&exe)
                    .current_dir(dir)
                    .args(["-i", "-h"])
                    .output()
                    .map_err(|e| format!("gagal jalankan {}: {e}", exe.display()))?;
                if output.status.success() {
                    return Ok(format!(
                        "VB-CABLE terpasang dari {} — layanan audio di-refresh",
                        exe.display()
                    ));
                }
            }
        }
    } else if let Some(ctl) = locate_vdd_ctl() {
        let ps_cmd = format!(
            "$p = Start-Process -FilePath '{}' -ArgumentList 'install-audio','--dir','{}' -Verb RunAs -WindowStyle Hidden -Wait -PassThru; exit $p.ExitCode",
            ctl.to_string_lossy().replace('\'', "''"),
            audio_dir_str.replace('\'', "''")
        );
        if let Ok(out) = Command::new("powershell.exe")
            .args(["-NoProfile", "-NonInteractive", "-Command", &ps_cmd])
            .output()
        {
            if out.status.success() {
                return Ok("VB-CABLE terpasang (UAC).".into());
            }
        }
    } else if let Some(dir) = &audio_dir {
        let bat = dir.join("install-audio.bat");
        if bat.is_file() {
            let ps_cmd = format!(
                "$p = Start-Process -FilePath 'cmd.exe' -ArgumentList '/D','/C','\"{}\" /silent' -Verb RunAs -WindowStyle Hidden -Wait -PassThru; exit $p.ExitCode",
                bat.to_string_lossy().replace('\'', "''")
            );
            if let Ok(out) = Command::new("powershell.exe")
                .args(["-NoProfile", "-NonInteractive", "-Command", &ps_cmd])
                .output()
            {
                if out.status.success() {
                    return Ok("VB-CABLE terpasang via install-audio.bat (UAC).".into());
                }
            }
        }
    }

    Err("VB-CABLE belum terpasang. Jalankan Install Virtual Audio Driver dari Start Menu -> XyDesk sebagai Administrator.".to_string())
}

#[cfg(not(target_os = "windows"))]
pub fn try_install_driver() -> Result<String, String> {
    Err("hanya Windows".to_string())
}

/// Alasan mic virtual belum bisa dipakai, untuk `meta.micInput.reason` di client.
/// `None` berarti siap. Nilai: `no-driver` (VB-CABLE belum terpasang),
/// `no-endpoint` (driver ada tapi endpoint "CABLE Input" nonaktif/dicabut).
pub fn unavailable_reason() -> Option<&'static str> {
    if get_render_device_id().is_some() {
        return None;
    }
    #[cfg(target_os = "windows")]
    {
        if kernel_driver_installed() {
            return Some("no-endpoint");
        }
    }
    Some("no-driver")
}

/// Penanda "sudah pernah mencoba pasang lewat UAC" supaya prompt tidak muncul
/// tiap host start; dihapus otomatis saat versi host berubah.
#[cfg(target_os = "windows")]
fn uac_attempt_marker() -> std::path::PathBuf {
    crate::identity::config_dir().join(format!("vbcable-uac-{}", env!("CARGO_PKG_VERSION")))
}

#[cfg(target_os = "windows")]
pub fn ensure_virtual_mic() {
    let mut status = get_status();
    if !status.installed {
        let admin = crate::virtual_display::is_admin();
        let marker = uac_attempt_marker();
        let first_uac = !admin && !marker.exists();
        if admin || first_uac {
            if !admin {
                let _ =
                    std::fs::create_dir_all(marker.parent().unwrap_or(std::path::Path::new(".")));
                let _ = std::fs::write(&marker, b"1");
            }
            match try_install_driver() {
                Ok(msg) => {
                    eprintln!("[xydesk-host] auto-provision VB-CABLE: {msg}");
                    status = get_status();
                }
                Err(e) => eprintln!("[xydesk-host] auto-provision VB-CABLE gagal: {e}"),
            }
        }
    }
    eprintln!(
        "[xydesk-host] Virtual Mic: needed={}, installed={}, virtual_input={}, virtual_output={}, render_target={}",
        status.needed, status.installed, status.has_virtual_input, status.has_virtual_output, status.render_target
    );

    if status.installed {
        println!(
            "[xydesk-host] virtual audio driver ada — client mic akan di-render ke {}",
            status.render_target
        );
    }
}

#[cfg(not(target_os = "windows"))]
pub fn ensure_virtual_mic() {}
