@echo off
setlocal EnableExtensions

REM ============================================================================
REM Instalasi driver Virtual Audio & Virtual Mic (VB-CABLE / Virtual Cable)
REM Memungkinkan audio host ditangkap secara jernih dan microphone client (HP/Web)
REM terbaca sebagai perangkat microphone fisik Windows (CABLE Input -> CABLE Output).
REM ============================================================================

REM Jika dijalankan dari proses 32-bit (WOW64), arahkan ulang ke cmd.exe 64-bit (Sysnative)
if exist "%SystemRoot%\Sysnative\cmd.exe" (
  "%SystemRoot%\Sysnative\cmd.exe" /D /C ""%~f0" %*"
  exit /b %ERRORLEVEL%
)

set "DRV_DIR=%~dp0"
if "%DRV_DIR:~-1%"=="\" set "DRV_DIR=%DRV_DIR:~0,-1%"
pushd "%DRV_DIR%" >nul 2>&1

set "SETUP_X64=%DRV_DIR%\VBCABLE_Setup_x64.exe"
set "SETUP_X86=%DRV_DIR%\VBCABLE_Setup.exe"
set "VDD_CTL=%DRV_DIR%\..\IddSampleDriver\xydesk-vdd-ctl.exe"

echo [XyDesk] Memeriksa driver Virtual Audio ^& Mic (VB-CABLE)...

REM 1. Gunakan native SetupAPI controller (xydesk-vdd-ctl.exe install-audio) bila ada
if exist "%VDD_CTL%" (
  "%VDD_CTL%" install-audio --dir "%DRV_DIR%"
  if not errorlevel 1 (
    popd >nul 2>&1
    exit /b 0
  )
)

REM 2. Buka kebijakan audio RDP Windows agar sesi Remote Desktop tidak menyembunyikan VB-CABLE
reg add "HKLM\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp" /v fDisableAudio /t REG_DWORD /d 0 /f >nul 2>&1
reg add "HKLM\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp" /v fDisableAudioCapture /t REG_DWORD /d 0 /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows NT\Terminal Services" /v fDisableAudio /t REG_DWORD /d 0 /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows NT\Terminal Services" /v fDisableAudioCapture /t REG_DWORD /d 0 /f >nul 2>&1

REM 3. Ekstrak & pasang sertifikat Authenticode VB-Audio dari .cat / .exe ke Root & TrustedPublisher
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'SilentlyContinue'; ^
   foreach ($f in @('vbaudio_cable64_win10.cat','vbaudio_cable64_win10.sys','VBCABLE_Setup_x64.exe')) { ^
     $p = Join-Path '%DRV_DIR%' $f; ^
     if (Test-Path $p) { ^
       $sig = Get-AuthenticodeSignature $p; ^
       if ($sig -and $sig.SignerCertificate) { ^
         $cer = Join-Path $env:TEMP 'vbaudio_signer.cer'; ^
         [System.IO.File]::WriteAllBytes($cer, $sig.SignerCertificate.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert)); ^
         certutil -addstore -f 'Root' $cer | Out-Null; ^
         certutil -addstore -f 'TrustedPublisher' $cer | Out-Null; ^
       } ^
     } ^
   }" >nul 2>&1

REM 4. Stage HANYA INF Windows 10/11 64-bit (jangan pernah stage INF xp/2003/vista!)
if exist "%DRV_DIR%\vbMmeCable64_win10.inf" (
  pnputil /add-driver "%DRV_DIR%\vbMmeCable64_win10.inf" /install >nul 2>&1
) else if exist "%DRV_DIR%\vbMmeCable64_win7.inf" (
  pnputil /add-driver "%DRV_DIR%\vbMmeCable64_win7.inf" /install >nul 2>&1
)

REM 5. Jalankan silent installation (-i = install, -h = hide/silent) dari dalam %DRV_DIR%
if exist "%SETUP_X64%" (
  echo [XyDesk] Memasang driver Virtual Audio x64...
  "%SETUP_X64%" -i -h
  sc config AudioEndpointBuilder start= auto >nul 2>&1
  sc config Audiosrv start= auto >nul 2>&1
  net start AudioEndpointBuilder >nul 2>&1
  net start Audiosrv >nul 2>&1
  popd >nul 2>&1
  exit /b 0
)

if exist "%SETUP_X86%" (
  echo [XyDesk] Memasang driver Virtual Audio x86...
  "%SETUP_X86%" -i -h
  net start AudioEndpointBuilder >nul 2>&1
  net start Audiosrv >nul 2>&1
  popd >nul 2>&1
  exit /b 0
)

echo [XyDesk] Berkas installer audio tidak ditemukan di direktori lokal.
popd >nul 2>&1
exit /b 2
