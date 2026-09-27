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
