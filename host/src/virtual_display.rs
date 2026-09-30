//! XyDesk Virtual Display Adapter & Monitor Controller (IddCx UMDF2).
//!
//! Mendeteksi dan mengelola XyDesk Virtual Display Adapter secara presisi
//! melalui identitas hardware PnP (`Root\XyDeskVDD`, `Root\MttVDD`,
//! `Root\IddSampleDriver`) dan nama adapter (`XyDesk Virtual Display Adapter`),
//! lengkap dengan matriks multi-resolusi (720p..8K, 16:9/16:10/21:9/32:9/Tablet),
//! refresh rate tinggi (30Hz..240Hz), dan EDID 1.4 + CEA-861 HDR kustom.

pub const ADAPTER_FRIENDLY_NAME: &str = "XyDesk Virtual Display Adapter";
pub const MONITOR_FRIENDLY_NAME: &str = "XyDesk Virtual Display";

/// Preset resolusi resmi XyDesk Virtual Display Adapter.
#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VirtualModePreset {
    pub width: u32,
    pub height: u32,
    pub aspect: &'static str,
    pub label: &'static str,
}

/// Matriks resolusi resmi (setara StarDesk Virtual Display).
pub const SUPPORTED_RESOLUTIONS: &[VirtualModePreset] = &[
    // 16:9 Standar, QHD, 4K UHD, 5K, 8K
    VirtualModePreset {
        width: 1280,
        height: 720,
        aspect: "16:9",
        label: "HD 720p",
    },
    VirtualModePreset {
        width: 1366,
        height: 768,
        aspect: "16:9",
        label: "WXGA",
    },
    VirtualModePreset {
        width: 1600,
        height: 900,
        aspect: "16:9",
        label: "HD+ 900p",
    },
    VirtualModePreset {
        width: 1920,
        height: 1080,
        aspect: "16:9",
        label: "FHD 1080p",
    },
    VirtualModePreset {
        width: 2560,
        height: 1440,
        aspect: "16:9",
        label: "QHD 1440p",
    },
    VirtualModePreset {
        width: 3200,
        height: 1800,
        aspect: "16:9",
        label: "QHD+ 1800p",
    },
    VirtualModePreset {
        width: 3840,
        height: 2160,
        aspect: "16:9",
        label: "4K UHD",
    },
    VirtualModePreset {
        width: 5120,
        height: 2880,
        aspect: "16:9",
        label: "5K UHD+",
    },
    VirtualModePreset {
        width: 7680,
        height: 4320,
        aspect: "16:9",
        label: "8K FUHD",
    },
    // 16:10 Produktivitas / Laptop / Tablet
    VirtualModePreset {
        width: 1280,
        height: 800,
        aspect: "16:10",
        label: "WXGA 800p",
    },
    VirtualModePreset {
        width: 1440,
        height: 900,
        aspect: "16:10",
        label: "WXGA+ 900p",
    },
    VirtualModePreset {
        width: 1680,
        height: 1050,
        aspect: "16:10",
        label: "WSXGA+",
    },
    VirtualModePreset {
        width: 1920,
        height: 1200,
        aspect: "16:10",
        label: "WUXGA 1200p",
    },
    VirtualModePreset {
        width: 2560,
        height: 1600,
        aspect: "16:10",
        label: "WQXGA 1600p",
    },
    VirtualModePreset {
        width: 2880,
        height: 1800,
        aspect: "16:10",
        label: "Retina 1800p",
    },
    VirtualModePreset {
        width: 3840,
        height: 2400,
        aspect: "16:10",
        label: "WQUXGA 2400p",
    },
    // 21:9 & 32:9 Ultrawide
    VirtualModePreset {
        width: 2560,
        height: 1080,
        aspect: "21:9",
        label: "UW-FHD",
    },
    VirtualModePreset {
        width: 3440,
        height: 1440,
        aspect: "21:9",
        label: "UW-QHD",
    },
    VirtualModePreset {
        width: 3840,
        height: 1600,
        aspect: "21:9",
        label: "UW-QHD+",
    },
    VirtualModePreset {
        width: 5120,
        height: 1440,
        aspect: "32:9",
        label: "DQHD Super Ultrawide",
    },
    // 4:3 / 3:2 / Tablet / Mobile
    VirtualModePreset {
        width: 1024,
        height: 768,
        aspect: "4:3",
        label: "XGA",
    },
    VirtualModePreset {
        width: 2048,
        height: 1536,
        aspect: "4:3",
        label: "QXGA iPad",
    },
    VirtualModePreset {
        width: 2160,
        height: 1440,
        aspect: "3:2",
        label: "Surface 3:2",
    },
    VirtualModePreset {
        width: 2256,
        height: 1504,
        aspect: "3:2",
        label: "Surface Pro",
    },
    VirtualModePreset {
        width: 2400,
        height: 1080,
        aspect: "20:9",
        label: "Mobile FHD+",
    },
    VirtualModePreset {
        width: 2732,
        height: 2048,
        aspect: "4:3",
        label: "iPad Pro 12.9",
    },
];

/// Refresh rate yang didukung oleh XyDesk Virtual Display Adapter (Hz).
pub const SUPPORTED_REFRESH_RATES: &[u32] = &[30, 60, 75, 90, 120, 144, 165, 240];

#[derive(Clone, Debug, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Adapter {
    pub device: String,
    pub description: String,
    pub hardware_id: String,
    pub attached: bool,
    pub known_virtual: bool,
}

pub fn known_virtual(description: &str, hardware_id: &str) -> bool {
    let d = description.trim().to_ascii_lowercase();
    let id = hardware_id.trim().to_ascii_lowercase();
    if d.contains("remote") || d.contains("rdp") {
        return false;
    }
    matches!(
        d.as_str(),
        "xydesk virtual display adapter"
            | "xydesk virtual display"
            | "xydesk virtual display driver"
            | "stardesk virtual display"
            | "stardesk virtual display adapter"
            | "virtual display driver"
            | "iddsampledriver"
            | "iddsampledriver device"
            | "mtt virtual display"
            | "mikethetech virtual display"
    ) || id.starts_with("root\\xydeskvdd")
        || id.starts_with("root\\mttvdd")
        || id.starts_with("root\\iddsampledriver")
        || id == "mttvdd"
}

/// Bangun blok EDID 256-byte (EDID 1.4 + CEA-861 HDR extension) dengan
/// Manufacturer ID `XYD` (`0x63, 0x24`) dan Monitor Name `XyDesk VDD`.
#[rustfmt::skip]
pub fn build_xydesk_edid() -> [u8; 256] {
    let mut edid: [u8; 256] = [
        0x00, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00, 0x63, 0x24, 0x58, 0x59, 0x01, 0x09, 0x06,
        0x00, 0x26, 0x24, 0x01, 0x04, 0xa5, 0x3c, 0x22, 0x78, 0x3a, 0xee, 0x95, 0xa3, 0x54, 0x4c,
        0x99, 0x26, 0x0f, 0x50, 0x54, 0x21, 0x08, 0x00, 0xd1, 0xc0, 0xa9, 0xc0, 0x81, 0xc0, 0x01,
        0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x02, 0x3a, 0x80, 0x18, 0x71, 0x38,
        0x2d, 0x40, 0x58, 0x2c, 0x45, 0x00, 0x58, 0x54, 0x21, 0x00, 0x00, 0x1e, 0x00, 0x00, 0x00,
        0xfd, 0x00, 0x18, 0xf0, 0x0f, 0xff, 0x78, 0x00, 0x0a, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
        0x00, 0x00, 0x00, 0xff, 0x00, b'X', b'Y', b'D', b'E', b'S', b'K', b'-', b'V', b'D', b'D',
        0x0a, 0x20, 0x20, 0x00, 0x00, 0x00, 0xfc, 0x00, b'X', b'y', b'D', b'e', b's', b'k', b' ',
        b'V', b'D', b'D', 0x0a, 0x20, 0x20, 0x01, 0x00, 0x02, 0x03, 0x20, 0x40, 0xe6, 0x06, 0x0d,
        0x01, 0xa2, 0xa2, 0x10, 0xe3, 0x05, 0xd8, 0x00, 0x67, 0xd8, 0x5d, 0xc4, 0x01, 0x6e, 0x80,
        0x00, 0x68, 0x03, 0x0c, 0x00, 0x10, 0x00, 0x30, 0x00, 0x0b, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00,
    ];
    let sum0: u32 = edid[..127].iter().map(|&b| u32::from(b)).sum();
    edid[127] = ((256 - (sum0 % 256)) % 256) as u8;
    let sum1: u32 = edid[128..255].iter().map(|&b| u32::from(b)).sum();
    edid[255] = ((256 - (sum1 % 256)) % 256) as u8;
    edid
}

/// Bangun isi `option.txt` kanonik untuk `IddSampleDriver` / `MttVDD`.
/// Baris pertama WAJIB berupa bilangan bulat jumlah monitor tanpa komentar.
pub fn build_option_txt(
    preferred_width: u32,
    preferred_height: u32,
    preferred_hz: u32,
    monitor_count: u32,
) -> String {
    let count = monitor_count.clamp(1, 4);
    let w = preferred_width.clamp(640, 7680);
    let h = preferred_height.clamp(480, 4320);
    let hz = preferred_hz.clamp(24, 240);

    let mut out = format!(
        "{count}\r\n\
         # XyDesk Virtual Display Adapter — Matriks Resolusi & Refresh Rate\r\n\
         # Format: width, height, refresh_rate_hz\r\n\
         {w}, {h}, {hz}\r\n"
    );
    for preset in SUPPORTED_RESOLUTIONS {
        for &rate in SUPPORTED_REFRESH_RATES {
            if preset.width == w && preset.height == h && rate == hz {
                continue;
            }
            let line = format!("{}, {}, {}\r\n", preset.width, preset.height, rate);
            out.push_str(&line);
        }
    }
    out
}

/// Bangun isi `vdd_settings.xml` untuk `MttVDD` (`C:\VirtualDisplayDriver\vdd_settings.xml`).
pub fn build_vdd_settings_xml(
    preferred_width: u32,
    preferred_height: u32,
    preferred_hz: u32,
    monitor_count: u32,
) -> String {
    let count = monitor_count.clamp(1, 4);
    let w = preferred_width.clamp(640, 7680);
    let h = preferred_height.clamp(480, 4320);
    let hz = preferred_hz.clamp(24, 240);

    let mut pairs: Vec<(u32, u32)> = vec![(w, h)];
    for p in SUPPORTED_RESOLUTIONS {
        if p.width != w || p.height != h {
            pairs.push((p.width, p.height));
        }
    }

    let mut xml = format!(
        "<?xml version='1.0' encoding='utf-8'?>\r\n\
         <vdd_settings>\r\n\
         \x20   <monitors>\r\n\
         \x20       <count>{count}</count>\r\n\
         \x20   </monitors>\r\n\
         \x20   <gpu>\r\n\
         \x20       <friendlyname>default</friendlyname>\r\n\
         \x20   </gpu>\r\n\
         \x20   <resolutions>\r\n"
    );
    for (rw, rh) in pairs {
        xml.push_str(&format!(
            "        <resolution>\r\n            <width>{rw}</width>\r\n            <height>{rh}</height>\r\n"
        ));
        let mut has_pref = false;
        for &r in SUPPORTED_REFRESH_RATES {
            if r == hz {
                has_pref = true;
            }
            xml.push_str(&format!("            <refresh_rate>{r}</refresh_rate>\r\n"));
        }
        if !has_pref {
            xml.push_str(&format!("            <refresh_rate>{hz}</refresh_rate>\r\n"));
        }
        xml.push_str("        </resolution>\r\n");
    }
    xml.push_str(
        "    </resolutions>\r\n\
         \x20   <options>\r\n\
         \x20       <CustomEdid>true</CustomEdid>\r\n\
         \x20       <PreventSpoof>true</PreventSpoof>\r\n\
         \x20       <EdidCeaOverride>true</EdidCeaOverride>\r\n\
         \x20       <HardwareCursor>true</HardwareCursor>\r\n\
         \x20       <SDR10bit>false</SDR10bit>\r\n\
         \x20       <HDRPlus>false</HDRPlus>\r\n\
         \x20       <logging>false</logging>\r\n\
         \x20       <debuglogging>false</debuglogging>\r\n\
         \x20   </options>\r\n\
         </vdd_settings>\r\n",
    );
    xml
}

pub fn adapters() -> Vec<Adapter> {
    #[cfg(target_os = "windows")]
    {
        use windows::Win32::Graphics::Gdi::{
            EnumDisplayDevicesW, DISPLAY_DEVICEW, DISPLAY_DEVICE_ATTACHED_TO_DESKTOP,
        };
        let text = |v: &[u16]| {
            String::from_utf16_lossy(&v[..v.iter().position(|x| *x == 0).unwrap_or(v.len())])
        };
        let mut out = Vec::new();
        for i in 0..64 {
            let mut d = DISPLAY_DEVICEW {
                cb: std::mem::size_of::<DISPLAY_DEVICEW>() as u32,
                ..Default::default()
            };
            if !unsafe { EnumDisplayDevicesW(None, i, &mut d, 0) }.as_bool() {
                break;
            }
            let description = text(&d.DeviceString);
            let hardware_id = text(&d.DeviceID);
            out.push(Adapter {
                device: text(&d.DeviceName),
                attached: d.StateFlags.0 & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP.0 != 0,
                known_virtual: known_virtual(&description, &hardware_id),
                description,
                hardware_id,
            });
        }
        out
    }
    #[cfg(not(target_os = "windows"))]
    {
        Vec::new()
    }
}

pub fn visible_virtual_displays() -> Vec<crate::screen::DisplayInfo> {
    let known = adapters();
    crate::screen::list_displays()
        .into_iter()
        .filter(|d| {
            known
                .iter()
                .any(|a| a.attached && a.known_virtual && a.device == d.name)
        })
        .collect()
}

pub fn needs_virtual_display() -> bool {
    crate::screen::is_rdp_session() || crate::screen::list_displays().is_empty()
}

/// Detected display adapter, not a guessed file/driver-store installation.
pub fn is_driver_installed() -> bool {
    adapters().iter().any(|a| a.known_virtual)
}

pub fn find_virtual_display() -> Option<crate::screen::DisplayInfo> {
    visible_virtual_displays().into_iter().next()
}

pub fn virtual_display_index() -> Option<usize> {
    find_virtual_display().map(|d| d.index)
}

/// Nama adapter virtual aktif (default `XyDesk Virtual Display Adapter`).
pub fn active_adapter_name() -> String {
    adapters()
        .into_iter()
        .find(|a| a.known_virtual)
        .map(|a| {
            if a.description.trim().is_empty() {
                ADAPTER_FRIENDLY_NAME.to_string()
            } else {
                a.description
            }
        })
        .unwrap_or_else(|| ADAPTER_FRIENDLY_NAME.to_string())
}

/// Cek apakah proses jalan sebagai admin — butuh untuk install driver.
#[cfg(target_os = "windows")]
pub fn is_admin() -> bool {
    unsafe {
        use windows::Win32::Foundation::HANDLE;
        use windows::Win32::Security::{
            GetTokenInformation, TokenElevation, TOKEN_ELEVATION, TOKEN_QUERY,
        };
        use windows::Win32::System::Threading::{GetCurrentProcess, OpenProcessToken};

        let mut token = HANDLE::default();
        if OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &mut token).is_err() {
            return false;
        }
        let mut elevation = TOKEN_ELEVATION { TokenIsElevated: 0 };
        let mut size = 0u32;
        let res = GetTokenInformation(
            token,
            TokenElevation,
            Some(&mut elevation as *mut _ as *mut _),
            std::mem::size_of::<TOKEN_ELEVATION>() as u32,
            &mut size,
        );
        let _ = windows::Win32::Foundation::CloseHandle(token);
        res.is_ok() && elevation.TokenIsElevated != 0
    }
}

#[cfg(not(target_os = "windows"))]
pub fn is_admin() -> bool {
    false
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
fn locate_install_bat() -> Option<std::path::PathBuf> {
    let exe = std::env::current_exe().ok()?;
    let dir = exe.parent()?;
    let candidates = [
        dir.join("drivers")
            .join("IddSampleDriver")
            .join("install.bat"),
        dir.join("drivers").join("install-all-drivers.bat"),
        std::path::PathBuf::from(r"C:\Program Files\XyDesk\drivers\IddSampleDriver\install.bat"),
    ];
    candidates.into_iter().find(|p| p.is_file())
}

#[cfg(target_os = "windows")]
fn sync_host_driver_configs(width: u32, height: u32, hz: u32, count: u32) {
    let vdd_dir = std::path::Path::new(r"C:\VirtualDisplayDriver");
    let idd_dir = std::path::Path::new(r"C:\IddSampleDriver");
    let _ = std::fs::create_dir_all(vdd_dir);
    let _ = std::fs::create_dir_all(idd_dir);
    let _ = std::fs::write(vdd_dir.join("user_edid.bin"), build_xydesk_edid());
    let _ = std::fs::write(
        vdd_dir.join("vdd_settings.xml"),
        build_vdd_settings_xml(width, height, hz, count),
    );
    let opt = build_option_txt(width, height, hz, count);
    let _ = std::fs::write(vdd_dir.join("option.txt"), &opt);
    let _ = std::fs::write(idd_dir.join("option.txt"), &opt);
}

#[rustfmt::skip]
pub fn try_install_driver() -> Result<String, String> {
    #[cfg(target_os = "windows")]
    {
        sync_host_driver_configs(1920, 1080, 60, 1);

        if is_admin() {
            if let Some(ctl) = locate_vdd_ctl() {
                let drv_dir = ctl
                    .parent()
                    .map(|p| p.to_string_lossy().to_string())
                    .unwrap_or_default();
                let out = std::process::Command::new(&ctl)
                    .args(["install", "--dir", &drv_dir])
                    .output()
                    .map_err(|e| format!("Gagal menjalankan xydesk-vdd-ctl.exe: {e}"))?;
                let stdout = String::from_utf8_lossy(&out.stdout).trim().to_string();
                if out.status.success() {
                    return Ok(if stdout.is_empty() {
                        "XyDesk Virtual Display Adapter berhasil dipasang.".into()
                    } else {
                        stdout
                    });
                }
            }
            if let Some(bat) = locate_install_bat() {
                let out = std::process::Command::new("cmd.exe")
                    .args(["/D", "/C", &bat.to_string_lossy(), "/silent"])
                    .output()
                    .map_err(|e| format!("Gagal menjalankan install.bat: {e}"))?;
                let stdout = String::from_utf8_lossy(&out.stdout).trim().to_string();
                if out.status.success() {
                    return Ok(if stdout.is_empty() {
                        "XyDesk Virtual Display Adapter berhasil dipasang.".into()
                    } else {
                        stdout
                    });
                }
                return Err(format!(
                    "install.bat keluar dengan kode {:?}: {}",
                    out.status.code(),
                    stdout
                ));
            }
        } else if let Some(ctl) = locate_vdd_ctl() {
            let drv_dir = ctl
                .parent()
                .map(|p| p.to_string_lossy().to_string())
                .unwrap_or_default();
            let ps_cmd = format!(
                "$p = Start-Process -FilePath '{}' -ArgumentList 'install','--dir','{}' -Verb RunAs -WindowStyle Hidden -Wait -PassThru; exit $p.ExitCode",
                ctl.to_string_lossy().replace('\'', "''"),
                drv_dir.replace('\'', "''")
            );
            let out = std::process::Command::new("powershell.exe")
                .args(["-NoProfile", "-NonInteractive", "-Command", &ps_cmd])
                .output()
                .map_err(|e| format!("Gagal meminta elevasi UAC untuk xydesk-vdd-ctl: {e}"))?;
            if out.status.success() {
                return Ok("XyDesk Virtual Display Adapter terpasang (UAC).".into());
            }
        } else if let Some(bat) = locate_install_bat() {
            let ps_cmd = format!(
                "$p = Start-Process -FilePath 'cmd.exe' -ArgumentList '/D','/C','\"{}\" /silent' -Verb RunAs -WindowStyle Hidden -Wait -PassThru; exit $p.ExitCode",
                bat.to_string_lossy().replace('\'', "''")
            );
            let out = std::process::Command::new("powershell.exe")
                .args(["-NoProfile", "-NonInteractive", "-Command", &ps_cmd])
                .output()
                .map_err(|e| format!("Gagal meminta elevasi UAC untuk install.bat: {e}"))?;
            if out.status.success() {
                return Ok("XyDesk Virtual Display Adapter terpasang (UAC).".into());
            }
        }
    }
    Err("Jalankan drivers\\IddSampleDriver\\install.bat atau Setup-VirtualDisplay.ps1 -Install secara eksplisit sebagai Administrator.".into())
}

pub fn ensure_virtual_display_created() -> bool {
    if find_virtual_display().is_some() {
        return true;
    }
    #[cfg(target_os = "windows")]
    {
        if !crate::screen::is_rdp_session() {
            if let Some(ctl) = locate_vdd_ctl() {
                let _ = std::process::Command::new(ctl).arg("ensure").output();
            }
        }
    }
    find_virtual_display().is_some()
}

#[rustfmt::skip]
pub fn ensure_display() {
    if needs_virtual_display() && find_virtual_display().is_none() {
        #[cfg(target_os = "windows")]
        if !crate::screen::is_rdp_session() {
            let _ = ensure_virtual_display_created();
            if find_virtual_display().is_some() {
                return;
            }
        }
        eprintln!("[xydesk-host] virtual display belum terlihat pada sesi ini. Driver console tidak selalu terlihat dari RDP; tidak ada restart, install, atau tscon otomatis.");
    }
}

#[rustfmt::skip]
pub fn set_virtual_display_mode(
    width: u32,
    height: u32,
    refresh_hz: u32,
    count: u32,
) -> Result<String, String> {
    if !(1..=4).contains(&count) {
        return Err("Jumlah monitor virtual harus antara 1 sampai 4.".into());
    }
    if !(640..=7680).contains(&width) || !(480..=4320).contains(&height) {
        return Err("Resolusi virtual display harus antara 640x480 hingga 7680x4320.".into());
    }
    let hz = refresh_hz.clamp(24, 240);

    #[cfg(target_os = "windows")]
    {
        sync_host_driver_configs(width, height, hz, count);
        if let Some(ctl) = locate_vdd_ctl() {
            let _ = std::process::Command::new(ctl)
                .args([
                    "set-mode",
                    &width.to_string(),
                    &height.to_string(),
                    &hz.to_string(),
                    &count.to_string(),
                ])
                .output();
        }
        if let Some(vd) = find_virtual_display() {
            let report = crate::desktop_mode::request_exact(vd.name, [width, height]);
            if matches!(report.status, "applied" | "already") {
                return Ok(format!(
                    "XyDesk Virtual Display aktif pada {width}x{height} @ {hz}Hz"
                ));
            }
        }
    }

    if visible_virtual_displays()
        .iter()
        .any(|d| d.width == width && d.height == height)
    {
        Ok(format!("Virtual display aktif teramati {width}x{height}"))
    } else {
        Err("Display dengan ukuran yang diminta belum terlihat. Gunakan setup driver eksplisit lalu periksa --display-probe.".into())
    }
}

#[rustfmt::skip]
pub fn create_virtual_display(width: u32, height: u32, count: u32) -> Result<String, String> {
    if count != 1 {
        return Err("Konfigurasi jumlah monitor dilakukan melalui driver; tidak ada perintah manager tebakan.".into());
    }
    set_virtual_display_mode(width, height, 60, count)
}

#[cfg(test)]
mod tests {
    #[test]
    fn identity_not_shape() {
        assert!(super::known_virtual(
            "Virtual Display Driver",
            "ROOT\\DISPLAY\\0001"
        ));
        assert!(super::known_virtual(
            "XyDesk Virtual Display Adapter",
            "ROOT\\XYDESKVDD\\0000"
        ));
        assert!(super::known_virtual("IddSampleDriver", ""));
        assert!(super::known_virtual("IddSampleDriver Device", ""));
        for d in [
            "NVIDIA RTX",
            "Microsoft Remote Display Adapter",
            "RDP Virtual Display Driver",
            "Generic PnP Monitor",
            "DISPLAY2",
            "Some Virtual Device",
        ] {
            assert!(!super::known_virtual(d, ""), "{d}");
        }
    }

    #[test]
    fn hardware_id_and_remote_exclusion() {
        assert!(super::known_virtual("Adapter", "ROOT\\MTTVDD\\0000"));
        assert!(super::known_virtual("Adapter", "ROOT\\XYDESKVDD\\0000"));
        assert!(!super::known_virtual(
            "Remote adapter",
            "ROOT\\MTTVDD\\0000"
        ));
        assert!(!super::known_virtual("", "PCI\\VEN_10DE"));
    }

    #[test]
    fn edid_checksum_and_xydesk_branding_valid() {
        let edid = super::build_xydesk_edid();
        let hdr = [0x00, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00];
        assert_eq!(&edid[0..8], &hdr);
        let sum0: u32 = edid[..128].iter().map(|&b| u32::from(b)).sum();
        assert_eq!(sum0 % 256, 0, "base EDID block checksum must be 0 mod 256");
        let sum1: u32 = edid[128..256].iter().map(|&b| u32::from(b)).sum();
        assert_eq!(sum1 % 256, 0, "CEA-861 extension checksum must be 0 mod 256");
        let name_slice = &edid[113..123];
        assert_eq!(name_slice, b"XyDesk VDD");
    }

    #[test]
    fn option_txt_first_line_is_monitor_count_integer() {
        let txt = super::build_option_txt(2560, 1440, 144, 1);
        let mut lines = txt.lines();
        assert_eq!(lines.next(), Some("1"));
        assert!(txt.contains("2560, 1440, 144"));
        assert!(txt.contains("1920, 1080, 60"));
        assert!(txt.contains("3840, 2160, 120"));
    }
}
