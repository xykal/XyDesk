@echo off
setlocal EnableExtensions

REM ============================================================================
REM XyDesk Unified Driver Installer (Virtual Display Adapter + Virtual Audio)
REM Hak Cipta (c) 2026 XyVerse Technology Global
REM ============================================================================

set "ROOT_DRV=%~dp0"
if "%ROOT_DRV:~-1%"=="\" set "ROOT_DRV=%ROOT_DRV:~0,-1%"

echo [XyDesk] Memulai instalasi terpadu XyDesk Virtual Display Adapter ^& Virtual Audio...

REM 1. Prioritas utama: driver XyDeskVDD milik sendiri (Root\XyDeskVDD)
if exist "%ROOT_DRV%\XyDeskVDD\install.bat" (
  if exist "%ROOT_DRV%\XyDeskVDD\XyDeskVDD.inf" (
    echo [XyDesk] Memasang driver XyDeskVDD (driver XyDesk sendiri)...
    pushd "%ROOT_DRV%\XyDeskVDD" >nul 2>&1
    call "%ROOT_DRV%\XyDeskVDD\install.bat" /silent
    popd >nul 2>&1
  ) else (
    echo [XyDesk] Paket XyDeskVDD belum berisi biner driver — memakai IddSampleDriver.
  )
)

REM 2. Fallback: driver IddSampleDriver (MTT / ge9) bila XyDeskVDD gagal/tidak ada
if exist "%ROOT_DRV%\IddSampleDriver\install.bat" (
  if not exist "%ROOT_DRV%\XyDeskVDD\XyDeskVDD.inf" (
    pushd "%ROOT_DRV%\IddSampleDriver" >nul 2>&1
    call "%ROOT_DRV%\IddSampleDriver\install.bat" /silent
    popd >nul 2>&1
  )
)

if exist "%ROOT_DRV%\audio\install-audio.bat" (
  pushd "%ROOT_DRV%\audio" >nul 2>&1
  call "%ROOT_DRV%\audio\install-audio.bat" /silent
  popd >nul 2>&1
)

echo [XyDesk] Instalasi terpadu driver selesai.
exit /b 0
