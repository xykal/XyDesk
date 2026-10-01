@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ============================================================================
REM Pembersihan XyDesk Virtual Display Adapter (MttVDD / IddSampleDriver)
REM Hak Cipta (c) 2026 XyVerse Technology Global
REM ============================================================================

set "DRV_DIR=%~dp0"
if "%DRV_DIR:~-1%"=="\" set "DRV_DIR=%DRV_DIR:~0,-1%"
pushd "%DRV_DIR%" >nul 2>&1

set "SILENT=0"
if /I "%~1"=="/silent" set "SILENT=1"
if /I "%~1"=="--silent" set "SILENT=1"

echo [XyDesk] Menghapus XyDesk Virtual Display Adapter...

set "CTL_EXE="
if exist "%DRV_DIR%\xydesk-vdd-ctl.exe" set "CTL_EXE=%DRV_DIR%\xydesk-vdd-ctl.exe"
if not defined CTL_EXE if exist "%DRV_DIR%\..\..\xydesk-vdd-ctl.exe" set "CTL_EXE=%DRV_DIR%\..\..\xydesk-vdd-ctl.exe"

if defined CTL_EXE (
  "%CTL_EXE%" uninstall >nul 2>&1
)

REM 1. Hapus device node Root\MttVDD dan Root\IddSampleDriver
for /f "tokens=2 delims=:" %%I in (
  'pnputil /enum-devices /class Display 2^>nul ^| findstr /I "Root\\MttVDD Root\\IddSampleDriver Root\\XyDeskVDD"'
) do (
  set "DEV=%%I"
  set "DEV=!DEV: =!"
  if not "!DEV!"=="" (
    pnputil /remove-device "!DEV!" >nul 2>&1
  )
)

REM 2. Hapus INF dari DriverStore
for %%F in ("MttVDD.inf" "iddsampledriver.inf") do (
  if exist "%DRV_DIR%\%%~F" (
    pnputil /delete-driver "%DRV_DIR%\%%~F" /uninstall /force >nul 2>&1
  )
)

REM 3. Hapus sertifikat berdasarkan thumbprint rilis MttVDD 24.10.27 & IddSampleDriver 0.0.1.4
for %%T in ("a4e3f7ee69cf1724a138e0e6f32a5f2d761c1b63" "26a26e11f7d812826aa9a862568c4d3dfb1da065") do (
  certutil -delstore "Root" "%%~T" >nul 2>&1
  certutil -delstore "TrustedPublisher" "%%~T" >nul 2>&1
)

popd >nul 2>&1
echo [XyDesk] Selesai.
exit /b 0
