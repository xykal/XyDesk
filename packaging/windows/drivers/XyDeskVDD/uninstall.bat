@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ============================================================================
REM Pembersihan XyDesk Virtual Display Adapter — XyDeskVDD (Root\XyDeskVDD)
REM Hak Cipta (c) 2026 XyVerse Technology Global
REM ============================================================================

set "DRV_DIR=%~dp0"
if "%DRV_DIR:~-1%"=="\" set "DRV_DIR=%DRV_DIR:~0,-1""
pushd "%DRV_DIR%" >nul 2>&1

echo [XyDesk] Menghapus XyDesk Virtual Display Adapter (XyDeskVDD)...

set "CTL_EXE="
if exist "%DRV_DIR%\xydesk-vdd-ctl.exe" set "CTL_EXE=%DRV_DIR%\xydesk-vdd-ctl.exe"
if not defined CTL_EXE if exist "%DRV_DIR%\..\..\xydesk-vdd-ctl.exe" set "CTL_EXE=%DRV_DIR%\..\..\xydesk-vdd-ctl.exe"

if defined CTL_EXE (
  "%CTL_EXE%" uninstall >nul 2>&1
)

REM 1. Hapus device node Root\XyDeskVDD
for /f "tokens=2 delims=:" %%I in (
  'pnputil /enum-devices /class Display 2^>nul ^| findstr /I "Root\\XyDeskVDD"'
) do (
  set "DEV=%%I"
  set "DEV=!DEV: =!"
  if not "!DEV!"=="" (
    pnputil /remove-device "!DEV!" >nul 2>&1
  )
)

REM 2. Hapus INF dari DriverStore
if exist "%DRV_DIR%\XyDeskVDD.inf" (
  pnputil /delete-driver "%DRV_DIR%\XyDeskVDD.inf" /uninstall /force >nul 2>&1
)
for /f "tokens=2 delims=: " %%D in (
  'pnputil /enum-drivers 2^>nul ^| findstr /I "xydeskvdd.inf"'
) do (
  pnputil /delete-driver "%%D" /uninstall /force >nul 2>&1
)

REM 3. Hapus sertifikat self-signed XyDeskVDD dari store (best-effort, by subject)
for %%S in ("Root" "TrustedPublisher") do (
  certutil -delstore %%S "XyDeskVDD Code Signing" >nul 2>&1
)

reg delete "HKLM\SOFTWARE\XyVerse Technology Global\XyDesk\VirtualDisplay" /v "DriverFlavor" /f >nul 2>&1

popd >nul 2>&1
echo [XyDesk] Selesai.
exit /b 0
