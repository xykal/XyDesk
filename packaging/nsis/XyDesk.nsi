Unicode True
!include "MUI2.nsh"
!include "x64.nsh"
!include "LogicLib.nsh"

!ifndef SourceDir
  !error "SourceDir is required"
!endif
!ifndef OutputDir
  !error "OutputDir is required"
!endif
!ifndef Version
  !error "Version is required"
!endif
!ifndef Arch
  !define Arch "x64"
!endif

!define PRODUCT "XyDesk"
!define COMPANY "XyVerse Technology Global"
!define UNKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\XyDesk"
!define INSTALLKEY "Software\XyDesk"

Name "${PRODUCT}"
OutFile "${OutputDir}\XyDesk-${Arch}.exe"
InstallDir "$PROGRAMFILES64\XyDesk"
InstallDirRegKey HKLM "${INSTALLKEY}" "InstallDir"
RequestExecutionLevel admin
SetCompressor /SOLID lzma
SetCompressorDictSize 32
ShowInstDetails show
ShowUninstDetails show

VIProductVersion "${Version}.0"
VIAddVersionKey /LANG=1033 "ProductName" "${PRODUCT}"
VIAddVersionKey /LANG=1033 "FileDescription" "XyDesk remote desktop"
VIAddVersionKey /LANG=1033 "FileVersion" "${Version}"
VIAddVersionKey /LANG=1033 "LegalCopyright" "Copyright 2026 ${COMPANY}"
VIAddVersionKey /LANG=1033 "CompanyName" "${COMPANY}"

!define MUI_ICON "${SourceDir}\xydesk.ico"
!define MUI_UNICON "${SourceDir}\xydesk.ico"
!define MUI_ABORTWARNING
; Wizard berjenama: header di tiap halaman + panel samping di welcome/finish.
; BMP dibuat dari palet brand (latar #0E1016, aksen #7D69EE), 24-bit.
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "banner-header.bmp"
!define MUI_HEADERIMAGE_BITMAP_NOSTRETCH
!define MUI_HEADERIMAGE_UNBITMAP "banner-header.bmp"
!define MUI_HEADERIMAGE_UNBITMAP_NOSTRETCH
!define MUI_WELCOMEFINISHPAGE_BITMAP "banner-welcome.bmp"
!define MUI_WELCOMEFINISHPAGE_BITMAP_NOSTRETCH
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "banner-welcome.bmp"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP_NOSTRETCH
!define MUI_WELCOMEPAGE_TITLE "Welcome to XyDesk"
!define MUI_WELCOMEPAGE_TEXT "XyDesk gives you a quiet native Windows control panel, a Rust streaming engine, and the dedicated XyDesk Virtual Display Adapter & Virtual Audio drivers.\r\n\r\nThe installer keeps the engine, native UI, virtual drivers, third-party notices, and English license text together."
!define MUI_FINISHPAGE_TITLE "XyDesk is ready"
!define MUI_FINISHPAGE_TEXT "XyDesk Control Panel and XyDesk Virtual Display Adapter are installed and verified. Run it now, or start it later from the Desktop or Start Menu shortcut.$\r$\n$\r$\nTip: run XyDesk inside the Windows session you actually use (your RDP user), so screen capture sees that session."
; Panel langsung terbuka setelah install supaya hasil pemasangan terlihat.
!define MUI_FINISHPAGE_RUN "$INSTDIR\XyDesk.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Run XyDesk Control Panel now"
!define MUI_FINISHPAGE_RUN_CHECKED
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${SourceDir}\LICENSE-XyDesk.txt"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH
!insertmacro MUI_LANGUAGE "English"

Section "XyDesk"
  SectionIn RO
  ${If} ${RunningX64}
    ${DisableX64FSRedirection}
    SetRegView 64
  ${EndIf}
  SetShellVarContext current
  SetOutPath "$INSTDIR"
  ; The bundle is produced by the verified Windows build. Keep every EXE,
  ; DLL, manifest, license, and support file in the same relative layout.
  DetailPrint "Copying XyDesk engine, control panel, and support files..."
  File /r "${SourceDir}\*"

  ; Verifikasi nyata: berkas inti harus benar-benar ada di disk sebelum
  ; registry dan shortcut ditulis. Kalau hilang, install dibatalkan.
  DetailPrint "Verifying installed files..."
  IfFileExists "$INSTDIR\XyDesk.exe" +3
    MessageBox MB_ICONSTOP|MB_OK "XyDesk.exe is missing after copy. Installation aborted." /SD IDOK
    Abort
  IfFileExists "$INSTDIR\xydesk-host.exe" +3
    MessageBox MB_ICONSTOP|MB_OK "xydesk-host.exe is missing after copy. Installation aborted." /SD IDOK
    Abort
  IfFileExists "$INSTDIR\LICENSE-XyDesk.txt" +3
    MessageBox MB_ICONSTOP|MB_OK "LICENSE-XyDesk.txt is missing after copy. Installation aborted." /SD IDOK
    Abort

  DetailPrint "Registering uninstall entry & all-user autostart..."
  WriteRegStr HKLM "${INSTALLKEY}" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "${INSTALLKEY}" "InstallDir" "$INSTDIR"
  WriteRegStr HKLM "${UNKEY}" "DisplayName" "${PRODUCT}"
  WriteRegStr HKLM "${UNKEY}" "DisplayVersion" "${Version}"
  WriteRegStr HKLM "${UNKEY}" "Publisher" "${COMPANY}"
  WriteRegStr HKLM "${UNKEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKLM "${UNKEY}" "UninstallString" '"$INSTDIR\Uninstall-XyDesk.exe"'
  WriteRegStr HKLM "${UNKEY}" "DisplayIcon" "$INSTDIR\xydesk.ico"
  WriteRegDWORD HKLM "${UNKEY}" "NoModify" 1
  WriteRegDWORD HKLM "${UNKEY}" "NoRepair" 1
  # Startup host di setiap login interaktif SEMUA user (baik sesi admin/runner maupun user RDP):
  # kepemimpinan lintas sesi (leadership.rs) otomatis memilih sesi yang sedang aktif.
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Run" "XyDeskHost" '"$INSTDIR\xydesk-host.exe" --managed-auth --autostart'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "XyDeskHost" '"$INSTDIR\xydesk-host.exe" --managed-auth --autostart'

  DetailPrint "Creating Desktop and Start Menu shortcuts for all sessions (Admin & RDP User)..."
  SetShellVarContext all
  CreateDirectory "$SMPROGRAMS\${PRODUCT}"
  CreateShortCut "$DESKTOP\XyDesk Control Panel.lnk" "$INSTDIR\XyDesk.exe" "" "$INSTDIR\xydesk.ico"
  CreateShortCut "$SMPROGRAMS\${PRODUCT}\Control Panel.lnk" "$INSTDIR\XyDesk.exe" "" "$INSTDIR\xydesk.ico"
  CreateShortCut "$SMPROGRAMS\${PRODUCT}\License and Notices.lnk" "$INSTDIR\LICENSE-XyDesk.txt"
  Delete "$SMPROGRAMS\${PRODUCT}\Uninstall.lnk"
  CreateShortCut "$SMPROGRAMS\${PRODUCT}\Uninstall.lnk" "$INSTDIR\Uninstall-XyDesk.exe"
  IfFileExists "$INSTDIR\drivers\IddSampleDriver\install.bat" 0 +2
    CreateShortCut "$SMPROGRAMS\${PRODUCT}\Install Virtual Display Driver.lnk" "$INSTDIR\drivers\IddSampleDriver\install.bat" "/silent" "$INSTDIR\xydesk.ico"
  IfFileExists "$INSTDIR\drivers\audio\install-audio.bat" 0 +2
    CreateShortCut "$SMPROGRAMS\${PRODUCT}\Install Virtual Audio Driver.lnk" "$INSTDIR\drivers\audio\install-audio.bat" "" "$INSTDIR\xydesk.ico"
  SetShellVarContext current

  # Pasang XyDesk Virtual Display Adapter (IddCx UMDF2 + PnP Device Node + Custom EDID)
  # dan driver Virtual Audio & Mic (VB-CABLE) secara langsung saat instalasi.
  IfFileExists "$INSTDIR\drivers\IddSampleDriver\xydesk-vdd-ctl.exe" 0 skip_vdd_ctl
    DetailPrint "Installing XyDesk Virtual Display Adapter & Virtual Audio (native SetupAPI controller)..."
    nsExec::ExecToLog '"$INSTDIR\drivers\IddSampleDriver\xydesk-vdd-ctl.exe" install --dir "$INSTDIR\drivers\IddSampleDriver"'
    Pop $0
    nsExec::ExecToLog '"$INSTDIR\drivers\IddSampleDriver\xydesk-vdd-ctl.exe" install-audio --dir "$INSTDIR\drivers\audio"'
    Pop $0
  skip_vdd_ctl:

  IfFileExists "$INSTDIR\drivers\install-all-drivers.bat" 0 fallback_individual_drivers
    DetailPrint "Configuring XyDesk Virtual Display & Virtual Audio drivers..."
    nsExec::ExecToLog '"$SYSDIR\cmd.exe" /D /C "call "$INSTDIR\drivers\install-all-drivers.bat" /silent"'
    Pop $0
    Goto done_drivers

  fallback_individual_drivers:
  IfFileExists "$INSTDIR\drivers\IddSampleDriver\install.bat" 0 skip_vdd_bat
    DetailPrint "Running XyDesk Virtual Display driver setup..."
    nsExec::ExecToLog '"$SYSDIR\cmd.exe" /D /C "call "$INSTDIR\drivers\IddSampleDriver\install.bat" /silent"'
    Pop $0
  skip_vdd_bat:
  IfFileExists "$INSTDIR\drivers\audio\install-audio.bat" 0 done_drivers
    DetailPrint "Running XyDesk Virtual Audio & Mic driver setup..."
    nsExec::ExecToLog '"$SYSDIR\cmd.exe" /D /C "call "$INSTDIR\drivers\audio\install-audio.bat" /silent"'
    Pop $0
  done_drivers:

  WriteUninstaller "$INSTDIR\Uninstall-XyDesk.exe"
  DetailPrint "Done. XyDesk ${Version} is installed in $INSTDIR."
SectionEnd

Section "Uninstall"
  ${If} ${RunningX64}
    ${DisableX64FSRedirection}
    SetRegView 64
  ${EndIf}
  SetShellVarContext all
  IfFileExists "$INSTDIR\drivers\IddSampleDriver\xydesk-vdd-ctl.exe" 0 +3
    nsExec::ExecToLog '"$INSTDIR\drivers\IddSampleDriver\xydesk-vdd-ctl.exe" uninstall'
    Pop $0
  ; Do not kill a live host or delete the user's identity/log directory.
  ; The installer leaves personal diagnostics under %LOCALAPPDATA%\XyDesk.
  Delete "$DESKTOP\XyDesk Control Panel.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\Control Panel.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\License and Notices.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\Install Virtual Display Driver.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\Install Virtual Audio Driver.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\Uninstall.lnk"
  RMDir "$SMPROGRAMS\${PRODUCT}"
  SetShellVarContext current
  Delete "$DESKTOP\XyDesk Control Panel.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\Control Panel.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\License and Notices.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\Install Virtual Display Driver.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\Install Virtual Audio Driver.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\Uninstall.lnk"
  RMDir "$SMPROGRAMS\${PRODUCT}"

  DeleteRegKey HKLM "${UNKEY}"
  DeleteRegValue HKLM "Software\Microsoft\Windows\CurrentVersion\Run" "XyDeskHost"
  DeleteRegKey HKLM "${INSTALLKEY}"
  DeleteRegKey HKCU "${UNKEY}"
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "XyDeskHost"
  DeleteRegKey HKCU "${INSTALLKEY}"
  Delete "$INSTDIR\Uninstall-XyDesk.exe"
  RMDir /r "$INSTDIR"
SectionEnd
