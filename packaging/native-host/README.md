# Native C++ Control Panel

The Windows user entrypoint is a small Win32 C++ application. It starts and stops
`xydesk-host.exe` directly with `CreateProcessW`, uses a Windows Job Object for
child lifetime, and never opens PowerShell or a console window.

The Rust engine remains responsible for signaling, capture, encoding, identity,
audio and input. The panel is deliberately quiet: Start, Stop, Device ID, pairing
code, and Open Web.
