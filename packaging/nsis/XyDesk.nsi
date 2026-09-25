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
!define COMPANY "XySpace Tech"
!define UNKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\XyDesk"
!define INSTALLKEY "Software\XyDesk"

Name "${PRODUCT}"
OutFile "${OutputDir}\XyDesk-${Arch}.exe"
InstallDir "$LOCALAPPDATA\Programs\XyDesk"
InstallDirRegKey HKCU "${INSTALLKEY}" "InstallDir"
RequestExecutionLevel user
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
!define MUI_WELCOMEPAGE_TEXT "XyDesk gives you a quiet native Windows control panel and a Rust streaming engine.\r\n\r\nThe installer keeps the engine, native UI, supporting DLLs, third-party notices, and English license text together."
!define MUI_FINISHPAGE_TITLE "XyDesk is ready"
!define MUI_FINISHPAGE_TEXT "XyDesk Control Panel is installed and verified. Run it now, or start it later from the Desktop or Start Menu shortcut.$\r$\n$\r$\nTip: run XyDesk inside the Windows session you actually use (your RDP user), so screen capture sees that session."
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

  DetailPrint "Registering uninstall entry..."
  WriteRegStr HKCU "${INSTALLKEY}" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "${UNKEY}" "DisplayName" "${PRODUCT}"
  WriteRegStr HKCU "${UNKEY}" "DisplayVersion" "${Version}"
  WriteRegStr HKCU "${UNKEY}" "Publisher" "${COMPANY}"
  WriteRegStr HKCU "${UNKEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNKEY}" "UninstallString" '"$INSTDIR\Uninstall-XyDesk.exe"'
  WriteRegStr HKCU "${UNKEY}" "DisplayIcon" "$INSTDIR\xydesk.ico"
  WriteRegDWORD HKCU "${UNKEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNKEY}" "NoRepair" 1

  DetailPrint "Creating Desktop and Start Menu shortcuts..."
  CreateDirectory "$SMPROGRAMS\${PRODUCT}"
  CreateShortCut "$DESKTOP\XyDesk Control Panel.lnk" "$INSTDIR\XyDesk.exe" "" "$INSTDIR\xydesk.ico"
  CreateShortCut "$SMPROGRAMS\${PRODUCT}\Control Panel.lnk" "$INSTDIR\XyDesk.exe" "" "$INSTDIR\xydesk.ico"
  CreateShortCut "$SMPROGRAMS\${PRODUCT}\License and Notices.lnk" "$INSTDIR\LICENSE-XyDesk.txt"
  CreateShortCut "$SMPROGRAMS\${PRODUCT}\Uninstall.lnk" "$INSTDIR\Uninstall-XyDesk.exe"

  WriteUninstaller "$INSTDIR\Uninstall-XyDesk.exe"
  DetailPrint "Done. XyDesk ${Version} is installed in $INSTDIR."
SectionEnd

Section "Uninstall"
  SetShellVarContext current
  ; Do not kill a live host or delete the user's identity/log directory.
  ; The installer leaves personal diagnostics under %LOCALAPPDATA%\XyDesk.
  Delete "$DESKTOP\XyDesk Control Panel.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\Control Panel.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\License and Notices.lnk"
  Delete "$SMPROGRAMS\${PRODUCT}\Uninstall.lnk"
  RMDir "$SMPROGRAMS\${PRODUCT}"

  DeleteRegKey HKCU "${UNKEY}"
  DeleteRegKey HKCU "${INSTALLKEY}"
  Delete "$INSTDIR\Uninstall-XyDesk.exe"
  RMDir /r "$INSTDIR"
SectionEnd
