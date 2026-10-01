@echo off
setlocal EnableExtensions

REM ============================================================================
REM XyDesk Unified Driver Installer (Virtual Display Adapter + Virtual Audio)
REM Hak Cipta (c) 2026 XyVerse Technology Global
REM ============================================================================

set "ROOT_DRV=%~dp0"
if "%ROOT_DRV:~-1%"=="\" set "ROOT_DRV=%ROOT_DRV:~0,-1%"

echo [XyDesk] Memulai instalasi terpadu XyDesk Virtual Display Adapter ^& Virtual Audio...

if exist "%ROOT_DRV%\IddSampleDriver\install.bat" (
  pushd "%ROOT_DRV%\IddSampleDriver" >nul 2>&1
  call "%ROOT_DRV%\IddSampleDriver\install.bat" /silent
  popd >nul 2>&1
)

if exist "%ROOT_DRV%\audio\install-audio.bat" (
  pushd "%ROOT_DRV%\audio" >nul 2>&1
  call "%ROOT_DRV%\audio\install-audio.bat" /silent
  popd >nul 2>&1
)

echo [XyDesk] Instalasi terpadu driver selesai.
exit /b 0
