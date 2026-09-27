# Host workspace revision — user rejected sparse layout and modal screens

Work in progress; no build/test claim until Actions completes.

- Web desktop hamburger fix 9ca5f15, deployment 36306087202 success.
- Native seven routes: Beranda, Koneksi, Akses host, Kontrol host, Pengaturan,
  Akun and Bantuan. Account/settings/help are child screens in the main HWND,
  not independent popup windows. One consistent sidebar glyph family.
- Switch runtime painting from layered UpdateLayeredWindow to ordinary clipped
  WM_PAINT, allowing actual native child controls and normal Windows sizing.
  WS_THICKFRAME plus all eight resize hit targets; work-area maximize/restore
  replaces font zoom. Minimum window 900x640 logical units protects form content.
- Session display reads existing authenticated loopback GET /status asynchronously;
  projection contains only reported client ID/name/platform, state and duration.
  Do not log/persist/display the raw status object (it contains a password).
- No change to engine, RTC, adaptive_video, remote_pointer, video_playback,
  session_fullscreen, control_client or control_contract.
- Optional browser model metadata warms in background. Pairing does not wait;
  uses existing display-name field, not a new transport/protocol message.
- Product-photo catalogue starts with exact Redmi Note 12 / 23021RAAEG matches.
  Original Xiaomi product photo (source recorded in DEVICE-PHOTO-SOURCES.txt),
  not generated imagery. No GSM Arena API invented, scraping service or hardware
  identity guarantee. Unmatched/withheld models explicitly have no product photo.
  Colour is illustrative. Model/name is self-reported and not security evidence.
- Workspace test mode never starts engine or reads account credentials; it
  validates all resize hit targets, geometry resizing, maximize/restore, child
  parentage/styles, and takes seven real-window screenshots plus a clearly
  labelled active-device fixture. Physical mouse dragging still needs acceptance.

Unsigned test distribution remains unchanged; no signing certificate provisioned.

## Actions evidence

- 4fcfd82 validation 36306499520 failed: missing enum cases and a malformed
  fixture aggregate. Fixed in cee92c9; 36306686024 then succeeded.
- Visual review of cee92c9 caught CI PrintWindow clipping off-desktop portions
  and C++ line continuations losing paragraph breaks. 9503314 fixes both and
  makes home cards compact with quick actions.
- Final baseline 36307028958 SUCCESS: Linux/Windows/web, including optional
  model metadata fallbacks, bounded display labels, native session projection,
  exact photo-model matches, QR roundtrip, account contracts, and workspace
  geometry/maximize/embedded-page checks.
- Artifact 10928145854: complete client-surface plus actual child WM_PRINT
  rendering, not a synthetic HTML redesign. Corrected home/settings/connection
  evidence visually inspected without the clipped right edge. Active-device
  screenshot is explicitly a fixture, NOT proof of real HP connection.
- Web deploy 36307313642 SUCCESS, source 9503314. Hamburger desktop fix and
  optional display-only browser model metadata, no RTC/input changes.
- Portable 36307312703 in progress at this entry.

Remaining acceptance: physical resize dragging, OS taskbar/snap and monitor DPI
changes, live connected-device display and exact model returned by the user's
browser, Google consent/restore/logout and screen navigation during pending
requests. In particular, the legacy fit-DPI fallback deserves follow-up on small
high-DPI monitors: WM_SIZE currently derives DPI from the OS again and embedded
resource controls have their own OS font metrics. Nominal 96-DPI runtime evidence
must not be presented as exhaustive high-DPI validation.

## Packaging and live bytes

Portable 36307312703 SUCCESS; artifact 10927653672. ZIP 8,463,124 bytes,
SHA256 `2e51c4bff16da269ac5f0136b8ffbb9a582b3d134ebe30cc8d5de2911fc3188f`,
verified against CI checksum. Photo-source notice verified inside the payload.
Engine rebuilt from unchanged source, SHA256
`f0f018f9ff1e465bfdbfe0fe4aae82f2e3d2ec8dd12cd289a6dfeb89bab080f1`.

Live remote assets downloaded and compared byte-for-byte with web artifact
10928260669:
- `/assets/index-5NYVV28V.js`, SHA256
  `7eba89d77517af73592103182f70624d6415145226ee45643647c101cc957e8b`.
- `/assets/index-5FcxrbdG.css`, SHA256
  `a10259115420ea7695e1289f189bbc44c78a8eaf3ec0ed07fe4d5be7894109a1`.

NSIS 36307719511 pending at this entry. Source held at 9503314 for same-commit
portable→installer gate. Screenshot guide contains real native-render evidence
but the Redmi session and account screen are explicitly offline fixtures.

NSIS 36307719511 **SUCCESS**, artifact 10927942483. Installer 5211214 bytes; downloaded SHA256 matched CI: `8edd2c5d633fec7423849b269b08ef5a06a6738df506dc63a8ed56e04837f7c7`. Install/reinstall/uninstall/shortcut and payload integrity checks passed. Still unsigned, hardwareTested false, no stable promotion.
