@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ============================================================================
REM Instalasi XyDesk Virtual Display Adapter — XyDeskVDD (driver IddCx UMDF2
REM milik XyDesk sendiri, fork IddSampleDriver MIT; HWID Root\XyDeskVDD).
REM Hak Cipta (c) 2026 XyVerse Technology Global
REM
REM Alur lengkap:
REM   1. Menyalin konfigurasi multi-resolusi & refresh rate tinggi ke
REM      C:\XyDeskVDD (utama), C:\VirtualDisplayDriver, dan C:\IddSampleDriver
REM      + mengatur ACL untuk LOCAL SERVICE (WUDFHost.exe)
REM   2. Memasang sertifikat driver ke LocalMachine\Root & TrustedPublisher
REM   3. Membuat Root-Enumerated PnP Device Node (Root\XyDeskVDD) dan
REM      mengikat driver UMDF2 IddCx via xydesk-vdd-ctl.exe (atau fallback
REM      SetupAPI + newdev.dll P/Invoke)
REM   4. Menamai perangkat sebagai "XyDesk Virtual Display Adapter" di
REM      Device Manager & Display Class Registry
REM ============================================================================

set "DRV_DIR=%~dp0"
if "%DRV_DIR:~-1%"=="\" set "DRV_DIR=%DRV_DIR:~0,-1%"
pushd "%DRV_DIR%" >nul 2>&1

set "SILENT=0"
if /I "%~1"=="/silent" set "SILENT=1"
if /I "%~1"=="--silent" set "SILENT=1"

echo [XyDesk] Memasang XyDesk Virtual Display Adapter (XyDeskVDD)...

REM 0. Pastikan arsitektur x64
if /I not "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
  if /I not "%PROCESSOR_ARCHITEW6432%"=="AMD64" (
    echo [XyDesk] Arsitektur %PROCESSOR_ARCHITECTURE% tidak didukung driver x64 ini.
    popd >nul 2>&1
    exit /b 4
  )
)

if not exist "%DRV_DIR%\XyDeskVDD.inf" (
  echo [XyDesk] GAGAL: XyDeskVDD.inf tidak ditemukan di %DRV_DIR%.
  echo [XyDesk] Pastikan isi paket driver (XyDeskVDD.inf, XyDeskVDD.dll,
  echo [XyDesk] XyDeskVDD.cat, XyDeskVDD.cer) berada dalam satu folder dengan skrip ini.
  popd >nul 2>&1
  exit /b 5
)

REM 1. Siapkan direktori konfigurasi
if not exist "C:\XyDeskVDD" mkdir "C:\XyDeskVDD" >nul 2>&1
if not exist "C:\VirtualDisplayDriver" mkdir "C:\VirtualDisplayDriver" >nul 2>&1
if not exist "C:\VirtualDisplayDriver\Logs" mkdir "C:\VirtualDisplayDriver\Logs" >nul 2>&1
if not exist "C:\IddSampleDriver" mkdir "C:\IddSampleDriver" >nul 2>&1

if exist "%DRV_DIR%\option.txt" (
  copy /Y "%DRV_DIR%\option.txt" "C:\XyDeskVDD\option.txt" >nul 2>&1
  copy /Y "%DRV_DIR%\option.txt" "C:\VirtualDisplayDriver\option.txt" >nul 2>&1
  copy /Y "%DRV_DIR%\option.txt" "C:\IddSampleDriver\option.txt" >nul 2>&1
)
if exist "%DRV_DIR%\vdd_settings.xml" (
  copy /Y "%DRV_DIR%\vdd_settings.xml" "C:\VirtualDisplayDriver\vdd_settings.xml" >nul 2>&1
)

icacls "C:\XyDeskVDD" /grant "*S-1-5-19:(OI)(CI)F" "*S-1-5-32-545:(OI)(CI)M" /T /C /Q >nul 2>&1
icacls "C:\VirtualDisplayDriver" /grant "*S-1-5-19:(OI)(CI)F" "*S-1-5-32-545:(OI)(CI)M" /T /C /Q >nul 2>&1
icacls "C:\IddSampleDriver" /grant "*S-1-5-19:(OI)(CI)F" "*S-1-5-32-545:(OI)(CI)M" /T /C /Q >nul 2>&1

reg add "HKLM\SOFTWARE\XyVerse Technology Global\XyDesk\VirtualDisplay" /v "Installed" /t REG_DWORD /d 1 /f >nul 2>&1
reg add "HKLM\SOFTWARE\XyVerse Technology Global\XyDesk\VirtualDisplay" /v "DriverFlavor" /t REG_SZ /d "XyDeskVDD" /f >nul 2>&1
reg add "HKLM\SOFTWARE\XyVerse Technology Global\XyDesk\VirtualDisplay" /v "AdapterName" /t REG_SZ /d "XyDesk Virtual Display Adapter" /f >nul 2>&1
reg add "HKLM\SOFTWARE\XyVerse Technology Global\XyDesk\VirtualDisplay" /v "MonitorName" /t REG_SZ /d "XyDesk Virtual Display" /f >nul 2>&1

REM 2. Jalur Utama: xydesk-vdd-ctl.exe (native Win32 SetupAPI + newdev.dll)
set "CTL_EXE="
if exist "%DRV_DIR%\xydesk-vdd-ctl.exe" set "CTL_EXE=%DRV_DIR%\xydesk-vdd-ctl.exe"
if not defined CTL_EXE if exist "%DRV_DIR%\..\..\xydesk-vdd-ctl.exe" set "CTL_EXE=%DRV_DIR%\..\..\xydesk-vdd-ctl.exe"

if defined CTL_EXE (
  echo [XyDesk] Menjalankan controller native: "%CTL_EXE%" install --dir "%DRV_DIR%"
  "%CTL_EXE%" install --dir "%DRV_DIR%"
  if not errorlevel 1 (
    echo [XyDesk] XyDesk Virtual Display Adapter berhasil dipasang via controller native.
    popd >nul 2>&1
    exit /b 0
  )
  echo [XyDesk] Controller native gagal, mencoba jalur fallback...
)

REM 3. Jalur Fallback: certutil + pnputil + SetupAPI PowerShell P/Invoke
echo [XyDesk] Menjalankan instalasi sertifikat dan SetupAPI PnP Device Node...
if exist "%DRV_DIR%\XyDeskVDD.cer" (
  certutil -addstore -f "Root" "%DRV_DIR%\XyDeskVDD.cer" >nul 2>&1
  certutil -addstore -f "TrustedPublisher" "%DRV_DIR%\XyDeskVDD.cer" >nul 2>&1
)

set "TARGET_INF=%DRV_DIR%\XyDeskVDD.inf"
set "TARGET_HWID=Root\XyDeskVDD"

pnputil /add-driver "%TARGET_INF%" /install >nul 2>&1

powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command ^
  "$inf = '%TARGET_INF%'; $hwid = '%TARGET_HWID%'; " ^
  "$code = 'using System; using System.Runtime.InteropServices; public class XyPnp { " ^
  "[StructLayout(LayoutKind.Sequential)] public struct SP_DEVINFO_DATA { public uint cbSize; public Guid ClassGuid; public uint DevInst; public IntPtr Reserved; } " ^
  "[DllImport(\"setupapi.dll\", CharSet=CharSet.Unicode, SetLastError=true)] public static extern IntPtr SetupDiCreateDeviceInfoList(ref Guid ClassGuid, IntPtr hwndParent); " ^
  "[DllImport(\"setupapi.dll\", CharSet=CharSet.Unicode, SetLastError=true)] public static extern bool SetupDiCreateDeviceInfoW(IntPtr DeviceInfoSet, string DeviceName, ref Guid ClassGuid, string DeviceDescription, IntPtr hwndParent, uint CreationFlags, ref SP_DEVINFO_DATA DeviceInfoData); " ^
  "[DllImport(\"setupapi.dll\", CharSet=CharSet.Unicode, SetLastError=true)] public static extern bool SetupDiSetDeviceRegistryPropertyW(IntPtr DeviceInfoSet, ref SP_DEVINFO_DATA DeviceInfoData, uint Property, byte[] PropertyBuffer, uint PropertyBufferSize); " ^
  "[DllImport(\"setupapi.dll\", SetLastError=true)] public static extern bool SetupDiCallClassInstaller(uint InstallFunction, IntPtr DeviceInfoSet, ref SP_DEVINFO_DATA DeviceInfoData); " ^
  "[DllImport(\"setupapi.dll\", SetLastError=true)] public static extern bool SetupDiDestroyDeviceInfoList(IntPtr DeviceInfoSet); " ^
  "[DllImport(\"newdev.dll\", CharSet=CharSet.Unicode, SetLastError=true)] public static extern bool UpdateDriverForPlugAndPlayDevicesW(IntPtr hwndParent, string HardwareId, string FullInfPath, uint InstallFlags, out bool bRebootRequired); " ^
  "public static bool Install(string inf, string hwid) { " ^
  "Guid g = new Guid(\"4D36E968-E325-11CE-BFC1-08002BE10318\"); " ^
  "IntPtr set = SetupDiCreateDeviceInfoList(ref g, IntPtr.Zero); if (set == new IntPtr(-1)) return false; " ^
  "SP_DEVINFO_DATA d = new SP_DEVINFO_DATA(); d.cbSize = (uint)Marshal.SizeOf(typeof(SP_DEVINFO_DATA)); " ^
  "if (SetupDiCreateDeviceInfoW(set, \"XyDeskVDD\", ref g, \"XyDesk Virtual Display Adapter\", IntPtr.Zero, 1, ref d)) { " ^
  "byte[] b = System.Text.Encoding.Unicode.GetBytes(hwid + \"\0\0\"); " ^
  "SetupDiSetDeviceRegistryPropertyW(set, ref d, 1, b, (uint)b.Length); " ^
  "SetupDiCallClassInstaller(0x19, set, ref d); " ^
  "byte[] fn = System.Text.Encoding.Unicode.GetBytes(\"XyDesk Virtual Display Adapter\0\"); " ^
  "SetupDiSetDeviceRegistryPropertyW(set, ref d, 12, fn, (uint)fn.Length); " ^
  "SetupDiSetDeviceRegistryPropertyW(set, ref d, 0, fn, (uint)fn.Length); } " ^
  "SetupDiDestroyDeviceInfoList(set); bool reb = false; " ^
  "return UpdateDriverForPlugAndPlayDevicesW(IntPtr.Zero, hwid, inf, 1, out reb); } }'; " ^
  "Add-Type -TypeDefinition $code; " ^
  "$existing = pnputil /enum-devices /class Display 2>$null | Select-String -Pattern 'Root\\\\XyDeskVDD'; " ^
  "if (-not $existing) { [void][XyPnp]::Install($inf, $hwid) } else { $reb = $false; [void][XyPnp]::UpdateDriverForPlugAndPlayDevicesW([IntPtr]::Zero, $hwid, $inf, 1, [ref]$reb) }"

if errorlevel 1 (
  echo [XyDesk] GAGAL memasang XyDeskVDD. Jalankan ulang dari prompt Administrator.
  popd >nul 2>&1
  exit /b 6
)

echo [XyDesk] XyDesk Virtual Display Adapter (XyDeskVDD) berhasil dipasang.
popd >nul 2>&1
exit /b 0
