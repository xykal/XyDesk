@echo off
setlocal EnableExtensions

REM ============================================================================
REM Instalasi driver Virtual Audio & Virtual Mic (VB-CABLE / Virtual Cable)
REM Memungkinkan audio host ditangkap secara jernih dan microphone client (HP/Web)
REM terbaca sebagai perangkat microphone fisik Windows (CABLE Input -> CABLE Output).
REM
REM PENTING: VBCABLE_Setup_x64.exe mencari berkas INF/CAT di working directory
REM aktif (%CD%), sehingga wajib pushd ke direktori driver terlebih dahulu!
REM ============================================================================

set "DRV_DIR=%~dp0"
if "%DRV_DIR:~-1%"=="\" set "DRV_DIR=%DRV_DIR:~0,-1%"
pushd "%DRV_DIR%" >nul 2>&1

set "SETUP_X64=%DRV_DIR%\VBCABLE_Setup_x64.exe"
set "SETUP_X86=%DRV_DIR%\VBCABLE_Setup.exe"

echo [XyDesk] Memeriksa driver Virtual Audio ^& Mic...

REM 1. Cek apakah VB-CABLE sudah terpasang dan aktif di sistem
pnputil /enum-devices /class Media 2>nul | findstr /I "VB-Audio Virtual Cable VBCABLE" >nul 2>&1
if not errorlevel 1 (
  echo [XyDesk] Driver Virtual Audio sudah terpasang dan aktif, skip.
  popd >nul 2>&1
  exit /b 0
)

REM 2. Pasang sertifikat atau stage INF VB-CABLE terlebih dahulu agar silent setup
REM    tidak tertahan dialog Windows Security.
for %%C in ("%DRV_DIR%\*.cer") do (
  if exist "%%~fC" (
    certutil -addstore -f "Root" "%%~fC" >nul 2>&1
    certutil -addstore -f "TrustedPublisher" "%%~fC" >nul 2>&1
  )
)
for %%I in ("%DRV_DIR%\vbMmeCable64*.inf" "%DRV_DIR%\*.inf") do (
  if exist "%%~fI" (
    pnputil /add-driver "%%~fI" /install >nul 2>&1
  )
)

REM 3. Jalankan silent installation (-i = install, -h = hide/silent) dari dalam %DRV_DIR%
if exist "%SETUP_X64%" (
  echo [XyDesk] Memasang driver Virtual Audio x64...
  "%SETUP_X64%" -i -h
  popd >nul 2>&1
  exit /b 0
)

if exist "%SETUP_X86%" (
  echo [XyDesk] Memasang driver Virtual Audio x86...
  "%SETUP_X86%" -i -h
  popd >nul 2>&1
  exit /b 0
)

echo [XyDesk] Berkas installer audio tidak ditemukan di direktori lokal.
popd >nul 2>&1
exit /b 2
