# UI follow-up — 27 September 2026

## Delivered
- Native source `d8fc018d6ead3dc573cd9dbfa0ec096f887febda` (immutable test tag `host-ui-d8fc018`). Utility pages are header-only: Settings, Profile/Account, Help. Removed duplicate sidebar painting, hit areas and focus entries; no phantom sidebar selection on utility pages. Selected header action highlighted. Embedded pages retained.
- Web source `c49c95c6e87c1df46e9b104fa333d6783fdd460d`: settings drawer restored to original XyDesk dark ink palette. White outlines remain confined to mapped controls. White keyboard gains persisted 0–90% background transparency for shell and key surfaces, without fading text. Rail hidden while keyboard covers it (restored when keyboard closes); no navigation showing through typing surface.
- Loading state uses one continuously resizing SVG outline, phone → monitor → tablet → phone, with static reduced-motion fallback. No device ID printed in connection status. ID-only deep links/password recovery preserved. No new animation dependency or React frame timer.

## Actions and evidence
- Prepare Host Windows [36311476611](https://github.com/xykal/XyDesk/actions/runs/36311476611): success, Linux geometry/parser/bootstrap + Rust tests, Windows build/library/worker lifetime/package gates. Native evidence artifact 10929153493; payload 10928579835.
- NSIS [36311903771](https://github.com/xykal/XyDesk/actions/runs/36311903771): success, validation PASS, artifact 10929655481. Install/reinstall/uninstall, spaces path, pinned driver payload, unrelated-folder rejection and identity retention tested.
- Web [36311787247](https://github.com/xykal/XyDesk/actions/runs/36311787247): success, npm test/build, 11 browser scenarios, no page errors, deploy. Browser evidence 10929192418; bundle 10929067506.
- First web follow-up run failed only in a new test's direct optimized React import; replaced with normal Vite TSX fixture module resolution. Earlier UI checks including keyboard alpha passed. Subsequent runs passed; final correction hides background rail exposed by translucent keyboard.
- Final web JS and CSS independently fetched via curl; both hashes match CI. `/assets/index-B2qiwi0O.js`: `4ff973e4a3512c867f661a5ae293735837365b2236e88aee5cf870a7e5d8184f`; `/assets/index-0L_w7Y_W.css`: `57b09de208c9a5db0c1b6f69c8394286dfcc0e9fa3ef0cd2ca9b77a825b6f351`.
- Native Home and Settings screenshots reviewed: four sidebar links, header utilities only, no stale sidebar highlight. Web dark drawer, translucent keyboard and monitor morph frame reviewed.

## Delivery files and limits
Workspace `deliverables/XyDesk-host-ui-d8fc018/`: installer, portable ZIP, checksums, NSIS report and guide. Installer 5,210,826 bytes, SHA256 `fcd3c7e1fc13a91097098290b77bee5f2dc5d2e64763dcfc0863abe9bf702655`. Engine built from unchanged source, SHA256 `0495eb068085ac12842735e7e939656a68abf82952af212ffce9fa9cb9fa45db`.

Screenshots/animated preview: `deliverables/ui-followup-c49c95c/PRATINJAU.html`; native shots `deliverables/host-top-nav-d8fc018/`. Preview animation reuses actual component SVG geometry; screenshots are CI fixtures, not a live host session.

No local project builds/tests. Source diff against 9503314 for host/src, host Cargo manifests, rtc.ts and video_playback.ts is empty. Streaming, input queues, adaptive bitrate and account/session coordination not modified. Native remains unsigned test candidate, no stable promotion. Known high-DPI issue and physical HP/RDP/OAuth acceptance remain outside this batch. Do not claim hardware performance measured.
