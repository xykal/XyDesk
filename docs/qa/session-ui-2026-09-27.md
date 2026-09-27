# Web session UI — 27 September 2026

Source: `96b3bfdc7da70a34425cf167585017c48f25a43e`.
Actions [36310218985](https://github.com/xykal/XyDesk/actions/runs/36310218985): success, unit/build/Chromium smoke/deploy. Browser evidence artifact 10928711140; bundle artifact 10928353092. Ten browser scenarios, zero page errors. Live entry JS/CSS independently fetched using curl and matched byte hashes against CI artifact.

## Scope
- Transparent white-outline mapped buttons/sticks; improved outline mouse icon.
- Rail and mapped-control visibility independent; navigation above mapped controls, below keyboard/drawer.
- Full-height right settings drawer on portrait/landscape/desktop. Video, Kontrol, Audio, Statistik, Sesi; only active category renders.
- Short opening/category transitions, reduced-motion support; neutral focus and isolated settings keydown. Keyup still reaches existing key-release handling.
- Mobile full-viewport menu, without browser Fullscreen API. Desktop direct navigation, no hamburger duplication.
- Display guidance explicitly opens Video. Existing audio/mic handler behavior retained when relocating controls.

## Evidence and corrections
Automated browser checks cover synthetic canvas MediaStream attachment/playback, custom dialog under fullscreen, mapping editor persistence, WASD recentering/unsupported analog status, mobile inspector, menu viewport, drawer geometry at 390x844/844x390/1366x900, five categories, independent visibility, transparent controls, arrow-key tabs, reduced motion, keyboard composer, ID-only connection route.

Earlier failures were not deployed: menu bounds measured during animation, real mapping/rail pointer interception, then invalid comparison across separately saved portrait/landscape mapping counts. Fixed without force-clicking or hiding mapped controls. Final screenshots finish finite animations first; reviewed landscape drawer, portrait controls and full-viewport menu.

## Live hashes
- `/assets/index-DpWeXEn4.js`: `77429ab75145c6b8e54e7967c2fd830048eb91eb13881b5ee4fcf79b9943b304`
- `/assets/index-BAs3KR1U.css`: `bba49865e83c775fa8f492120a49f1e9fe58bef269baa3bf5a81563e0e5287ba`

## Boundaries
All project tests/builds ran in GitHub Actions, not locally. Browser media and session panels are fixtures, not real host/RDP/HP/OAuth acceptance or latency measurements. Streaming engine, transport/input queues, adaptive bitrate and host coordination unchanged. Native unsigned candidate remains 9503314; no new native package, signing, stable promotion or resolution of its disclosed high-DPI risk.

Workspace evidence: `deliverables/session-ui-96b3bfd/` including self-contained PRATINJAU.html, screenshots, results.json and live-verification.json. Stack remains React/TypeScript/Vite; no framework migration.
