# Reconnect capture ownership and independent floating rail — 27 September 2026

## User report and scope
User reports remote picture freezing after one/two disconnect-reconnect cycles while web menus remain responsive. User clarified the problematic panel is the floating RIGHT control rail, not the settings sheet. Hide must affect the rail only, never keyboard, mapping or editor access. Keyboard transparency must reveal the remote image.

## Actual race found and patched
Native source `154eb0488d90f13e20cc4bd9da235784cefe172f`, test tag `reconnect-host-154eb04`.
- `FrameSource::drop` unconditionally called global `disarm_capture()`. An old source's late destruction could disable capture newly armed by a new connected pump.
- The old pump's closed/failed branch also unconditionally disarmed capture. Old and new asynchronous pump lifetimes can overlap.
- Blocking source forwarding used `recv()` indefinitely; a silent old source could remain alive after its async consumer closed.
- Fix: per-pump RAII capture permission; acquire/release serialized only at session boundaries, last owner disarms capture. Source drop stops only its own worker. Forwarder uses a 100ms cancellation poll, not a frame delay; queue capacity and full-queue keyframe behavior unchanged.

This is a narrow lifecycle exception to the prior streaming freeze, responding to the reported reconnect fault. Bitrate/adaptive policy, codec parameters, RTP timing, input queues and Windows account coordination remain unchanged. Browser playback implementation was not changed speculatively. The race is established in source/regression tests; physical reproduction of the user's exact failure is not claimed.

## Web
Web source `b589421a57e31a94ae793a7328e08cfe512dc924`.
- Keyboard launcher rendered independently of collapsed rail (removed early return that hid it).
- Mapping/editor no longer unmounted merely because keyboard opens. Control toggle, keyboard and rail states independent; sheet toggles no longer close keyboard.
- Right rail explicit dark layout, portrait one column / short viewport two columns, bounded scroll and non-overlapping buttons. With keyboard open rail occupies available area above it, rather than disappearing or showing through keyboard. Short-screen keyboard launcher moved clear of rail.
- Background transparency 0–100%. Removed stacked white backplate behind alpha-painted keys; gaps clear, opaque text/borders retained. Browser preference remains local.

## Verified Actions
- Web [36317194424](https://github.com/xykal/XyDesk/actions/runs/36317194424) SUCCESS: tests/build, 12 browser scenarios, zero page errors, deploy. Artifact evidence 10931515249, bundle 10931102294. Tests include three fresh MediaStream reconnect attachments, stale cleanup, collapsed keyboard launcher, hide with keyboard+mapping/editor still mounted, portrait/landscape rail bounds/no overlaps, alpha 0.35 and alpha 0 at 100% transparency.
- Prepare Host Windows [36317145146](https://github.com/xykal/XyDesk/actions/runs/36317145146) SUCCESS: Linux regression/geometry/bootstrap + Windows package gates. Payload 10931312051; native evidence 10930938765. Added four overlapping ownership/late-source-cleanup cycles, silent bridge cancellation, and three real peer loopback connect/receive/disconnect cycles requiring fresh RTP frame pairs.
- Initial native run 36317002494 passed 201 library tests but failed the extended repeat test because a cached rescue IDR preceded the new frame pair. Test now distinguishes the live pair by its actual 120ms RTP timestamp gap; rescue behavior and RTP clock untouched. Final run passed.
- NSIS [36317585187](https://github.com/xykal/XyDesk/actions/runs/36317585187) SUCCESS/PASS, artifact 10930773526. Install/reinstall/uninstall, unrelated-directory protection, default per-user path, pinned driver payload and identity retention checks.

## Live bytes / packages
Independent curl fetch of entry HTML plus JS/CSS matched CI:
- `/assets/index-BlbTx-OA.js`: `c0a99f4f1ed8a3dfaa72a0c4660f715c972b0324d3762669e1acb9cffec783e6`
- `/assets/index-ZHUE1Luj.css`: `e8d9e32386cfc750724b5af67b06239e8d518808e4cf66d8a3aad1e1d2538f68`

Installer 5,216,620 bytes, SHA256 `f599cc232e01a9a90ead8c238ee88f4533d8489d69d1ceaf7d27b2a2d2948030`. Engine SHA256 `36668982035cc5d6e6e525c79e432ffddd96035a02d72f43edc8a7ee787d61f9`.
Workspace `deliverables/XyDesk-reconnect-154eb04/`: installer, portable ZIP, hashes, NSIS report, guide. Web screenshots/results/live hashes and preview at `deliverables/rail-b589421/`.

## Acceptance limits
All project builds/tests in Actions, none locally. Native unsigned candidate, not stable promotion. Existing installation must be updated and host restarted to use the reconnect fix. Tests use synthetic media/peer loopback, not real RDP/HP capture. High-DPI caveat, physical OAuth/device acceptance unchanged. Do not promise every freeze cause eliminated or claim measured latency.
