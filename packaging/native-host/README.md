# Native C++ Control Panel

The Windows user entrypoint is a small Win32 C++ application. It starts and stops
`xydesk-host.exe` directly with `CreateProcessW`, uses a Windows Job Object for
child lifetime, and never opens PowerShell or a console window.

The Rust engine remains responsible for signaling, capture, encoding, identity,
audio and input. The panel is deliberately quiet: Start, Stop, Device ID, pairing
code, Open Web, Restart, and host diagnostics. Engine stdout/stderr is written to
`%LOCALAPPDATA%\XyDesk\host.log`; an engine exit is shown with its exit code
instead of the generic status alone.

Production packaging is generated in two formats from the same verified bundle:
WiX MSI for managed Windows deployment and NSIS EXE for consumer installation.
The bundle carries the English EULA, third-party notices, dependency manifest,
and any explicitly verified support DLLs.
