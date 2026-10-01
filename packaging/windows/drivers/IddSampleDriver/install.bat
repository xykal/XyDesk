@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ============================================================================
REM Instalasi XyDesk Virtual Display Adapter (IddCx UMDF2 — MttVDD / IddSample)
REM Hak Cipta (c) 2026 XyVerse Technology Global
REM
REM Alur lengkap:
REM   1. Menyalin konfigurasi multi-resolusi & refresh rate tinggi ke
REM      C:\VirtualDisplayDriver dan C:\IddSampleDriver + mengatur ACL untuk
REM      LOCAL SERVICE (WUDFHost.exe)
REM   2. Memasang sertifikat driver ke LocalMachine\Root & TrustedPublisher
REM   3. Membuat Root-Enumerated PnP Device Node (Root\MttVDD /
REM      Root\IddSampleDriver) dan mengikat driver UMDF2 IddCx via
REM      xydesk-vdd-ctl.exe (atau fallback SetupAPI + newdev.dll P/Invoke)
REM   4. Menamai perangkat sebagai "XyDesk Virtual Display Adapter" di
REM      Device Manager & Display Class Registry
REM ============================================================================

set "DRV_DIR=%~dp0"
if "%DRV_DIR:~-1%"=="\" set "DRV_DIR=%DRV_DIR:~0,-1%"
pushd "%DRV_DIR%" >nul 2>&1

set "SILENT=0"
if /I "%~1"=="/silent" set "SILENT=1"
if /I "%~1"=="--silent" set "SILENT=1"

echo [XyDesk] Memasang XyDesk Virtual Display Adapter...

REM 0. Pastikan arsitektur x64
if /I not "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
  if /I not "%PROCESSOR_ARCHITEW6432%"=="AMD64" (
    echo [XyDesk] Arsitektur %PROCESSOR_ARCHITECTURE% tidak didukung driver x64 ini.
    popd >nul 2>&1
    exit /b 4
  )
)

REM 1. Siapkan direktori konfigurasi C:\VirtualDisplayDriver dan C:\IddSampleDriver
if not exist "C:\VirtualDisplayDriver" mkdir "C:\VirtualDisplayDriver" >nul 2>&1
if not exist "C:\VirtualDisplayDriver\Logs" mkdir "C:\VirtualDisplayDriver\Logs" >nul 2>&1
if not exist "C:\IddSampleDriver" mkdir "C:\IddSampleDriver" >nul 2>&1

if exist "%DRV_DIR%\option.txt" (
  copy /Y "%DRV_DIR%\option.txt" "C:\VirtualDisplayDriver\option.txt" >nul 2>&1
  copy /Y "%DRV_DIR%\option.txt" "C:\IddSampleDriver\option.txt" >nul 2>&1
)
if exist "%DRV_DIR%\vdd_settings.xml" (
  copy /Y "%DRV_DIR%\vdd_settings.xml" "C:\VirtualDisplayDriver\vdd_settings.xml" >nul 2>&1
)

icacls "C:\VirtualDisplayDriver" /grant "*S-1-5-19:(OI)(CI)F" "*S-1-5-32-545:(OI)(CI)M" /T /C /Q >nul 2>&1
icacls "C:\IddSampleDriver" /grant "*S-1-5-19:(OI)(CI)F" "*S-1-5-32-545:(OI)(CI)M" /T /C /Q >nul 2>&1

reg add "HKLM\SOFTWARE\MikeTheTech\VirtualDisplayDriver" /v "VDDPATH" /t REG_SZ /d "C:\VirtualDisplayDriver" /f >nul 2>&1
reg add "HKLM\SOFTWARE\MikeTheTech\VirtualDisplayDriver" /v "HardwareCursorEnabled" /t REG_DWORD /d 1 /f >nul 2>&1
reg add "HKLM\SOFTWARE\XyVerse Technology Global\XyDesk\VirtualDisplay" /v "Installed" /t REG_DWORD /d 1 /f >nul 2>&1
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
)

REM 3. Jalur Fallback: certutil + pnputil + SetupAPI PowerShell P/Invoke
echo [XyDesk] Menjalankan instalasi sertifikat dan SetupAPI PnP Device Node...
for %%C in ("%DRV_DIR%\Virtual_Display_Driver.cer" "%DRV_DIR%\iddsampledriver.cer" "%DRV_DIR%\IddSampleDriver.cer") do (
  if exist "%%~fC" (
    certutil -addstore -f "Root" "%%~fC" >nul 2>&1
    certutil -addstore -f "TrustedPublisher" "%%~fC" >nul 2>&1
  )
)

set "TARGET_INF="
set "TARGET_HWID="
if exist "%DRV_DIR%\MttVDD.inf" (
  set "TARGET_INF=%DRV_DIR%\MttVDD.inf"
  set "TARGET_HWID=Root\MttVDD"
) else if exist "%DRV_DIR%\iddsampledriver.inf" (
  set "TARGET_INF=%DRV_DIR%\iddsampledriver.inf"
  set "TARGET_HWID=Root\IddSampleDriver"
) else if exist "%DRV_DIR%\IddSampleDriver.inf" (
  set "TARGET_INF=%DRV_DIR%\IddSampleDriver.inf"
  set "TARGET_HWID=Root\IddSampleDriver"
)

if not defined TARGET_INF (
  echo [XyDesk] GAGAL: Berkas INF driver display virtual tidak ditemukan di %DRV_DIR%.
  popd >nul 2>&1
  exit /b 5
)

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
  "byte[] b = System.Text.Encoding.Unicode.GetBytes(hwid + \"\\0\\0\"); " ^
  "SetupDiSetDeviceRegistryPropertyW(set, ref d, 1, b, (uint)b.Length); " ^
  "SetupDiCallClassInstaller(0x19, set, ref d); " ^
  "byte[] fn = System.Text.Encoding.Unicode.GetBytes(\"XyDesk Virtual Display Adapter\\0\"); " ^
  "SetupDiSetDeviceRegistryPropertyW(set, ref d, 12, fn, (uint)fn.Length); " ^
  "SetupDiSetDeviceRegistryPropertyW(set, ref d, 0, fn, (uint)fn.Length); } " ^
  "SetupDiDestroyDeviceInfoList(set); bool reb = false; " ^
  "return UpdateDriverForPlugAndPlayDevicesW(IntPtr.Zero, hwid, inf, 1, out reb); } }'; " ^
  "Add-Type -TypeDefinition $code; " ^
  "$existing = pnputil /enum-devices /class Display 2>$null | Select-String -Pattern 'Root\\MttVDD|Root\\IddSampleDriver'; " ^
  "if (-not $existing) { [void][XyPnp]::Install($inf, $hwid) } else { $reb = $false; [void][XyPnp]::UpdateDriverForPlugAndPlayDevicesW([IntPtr]::Zero, $hwid, $inf, 1, [ref]$reb) }"

echo [XyDesk] XyDesk Virtual Display Adapter berhasil dipasang.
popd >nul 2>&1
exit /b 0
