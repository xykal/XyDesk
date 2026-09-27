# Reference-style host workspace — 27 September 2026

## Scope and implementation
Operator approved only shape/layout inspiration from supplied dashboard image, retaining XyDesk palette, logo and icon family. Source candidate `655ef359098087d69d6b57654244869a38bfd5d1`.

- Narrower detached sidebar island, rounded workspace island, 24px shell / 20px card / 12px control radii, internal shallow card shadows. No new external window shadow or ornamental animation.
- Page heading/description hierarchy for all seven existing destinations. No fitness content, dummy metrics, charts, duplicate utility navigation or new product claims.
- Settings/Account/Help remain header-only and embedded. Compact ID/password cards preserved; Home descriptions omitted at the smallest height instead of overlapping its actions.
- Native form controls reflow from measured template geometry into the available card. Actual edit/combo/check controls retained; push buttons use rounded owner painting while preserving command IDs and handlers. Redundant groupbox outlines become section labels.
- Sidebar collapse reflows embedded forms. Real resize/caption geometry preserved. Connection annotations no longer overlap footer at minimum size.

## Frozen boundaries
`git diff 5d0f722 655ef35 -- host web packaging/native-host/account_auth.h packaging/native-host/control_client.h` is empty. No engine/reconnect, bitrate, encoder, input queue, adaptive or Windows-account coordination changes. Web live remains b589421. Engine rebuilt by existing workflow from unchanged source; do not claim binary byte identity.

## Evidence
Prepare Host Windows [36320913038](https://github.com/xykal/XyDesk/actions/runs/36320913038): SUCCESS. Linux geometry/parser/bootstrap + Rust regression gate, Windows build/native private-control integration/account contracts, package/library/worker checks. Native screenshot artifact **10932333405**, portable payload **10932547749**.

Geometry tests check detached surfaces, heading/content separation, compact Home action placement, hit targets, collapsed navigation and multiple DPI calculations. Windows evidence checks resize edges/corners, maximize/restore, seven actual pages at 1100×720 AND 900×640, visible child bounds at minimum size. Screenshots composed from real native window/child control painting, not screen-clipped mockups. Reviewed Home, Access, Settings, Host Control, compact Home/Settings/Help and fixture Redmi connection. Screenshot fixture is offline; no capture or account login performed.

NSIS [36321438591](https://github.com/xykal/XyDesk/actions/runs/36321438591): SUCCESS/PASS. Artifact **10931623773**. Install to spaces path, default per-user installation, executable hash/help, pinned driver payload, unrelated directory rejection, reinstall registration safety and identity-preserving uninstall checks passed.

## Delivered files
Workspace `deliverables/XyDesk-rounded-655ef35/`:
- Installer `XyDesk-Host-Test-Setup-x64.exe`, **5,213,779 bytes**, SHA256 `d36b14b0f267f0da6bb2c07908072cbb2b10135237680bbf9de66aedbec0a262`.
- Portable `XyDesk-Host-Test-x64-655ef35.zip`, **8,469,838 bytes**, SHA256 `42a0711100755281561e02dcfe34009ca14a7d771929c96bb0a060ef1ada1687`.
- Engine SHA256 `a64e99e5dd15f5f0b08f92b0d92d8b5c7e6310b747ac3166eec9e342fb3c3d2e`.
- SHA256SUMS, NSIS-VALIDATION, PAYLOAD-MANIFEST, BACA-DULU and self-contained PRATINJAU.html (seven routes, two viewport sizes).
- Original native screenshots: `deliverables/host-rounded-655ef35/`.

## Acceptance limits
All project build/tests ran in Actions, not locally. Unsigned test candidate; no stable/latest promotion. Physical PC/HP/RDP and Google OAuth acceptance not claimed. Template reflow and compact bounds are improved/tested; physical high-DPI acceptance remains open, not declared universally fixed. Existing product-photo catalogue/attribution limits unchanged.
