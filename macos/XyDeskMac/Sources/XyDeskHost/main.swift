import AppKit
import XyDeskCore

/// XyDesk Mac Host — aplikasi menu-bar (tanpa dock icon, LSUIElement).
/// Menu: kode perangkat (salin), password pairing, status sesi, keluar.
/// (Tanpa @main: file ini top-level `main.swift`.)
final class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private var session: HostSession?
    private var statusLine = "Siap"

    func applicationDidFinishLaunching(_ notification: Notification) {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        statusItem.button?.image = NSImage(systemSymbolName: "desktopcomputer", accessibilityDescription: "XyDesk Host")
        rebuildMenu()

        let code = UserDefaults.standard.string(forKey: "deviceCode") ?? ""
        if code.count == 9 {
            startSession(code: code)
        } else {
            promptDeviceCode()
        }
    }

    private func startSession(code: String) {
        let host = HostSession(deviceId: code)
        host.onStatus = { [weak self] s in
            DispatchQueue.main.async {
                self?.statusLine = s
                self?.rebuildMenu()
            }
        }
        session = host
        host.start()
    }

    private func rebuildMenu() {
        let menu = NSMenu()
        let code = UserDefaults.standard.string(forKey: "deviceCode") ?? "belum diatur"
        let codeItem = NSMenuItem(title: "Kode perangkat: \(code)", action: nil, keyEquivalent: "")
        menu.addItem(codeItem)
        let copy = NSMenuItem(title: "Salin kode perangkat", action: #selector(copyCode), keyEquivalent: "c")
        copy.target = self
        menu.addItem(copy)
        menu.addItem(.separator())
        let st = NSMenuItem(title: statusLine, action: nil, keyEquivalent: "")
        st.isEnabled = false
        menu.addItem(st)
        let setCode = NSMenuItem(title: "Atur kode perangkat…", action: #selector(promptDeviceCode), keyEquivalent: "")
        setCode.target = self
        menu.addItem(setCode)
        let setPin = NSMenuItem(title: "Atur password pairing…", action: #selector(promptPin), keyEquivalent: "")
        setPin.target = self
        menu.addItem(setPin)
        menu.addItem(.separator())
        let quit = NSMenuItem(title: "Keluar XyDesk Host", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        menu.addItem(quit)
        statusItem.menu = menu
    }

    @objc private func copyCode() {
        let code = UserDefaults.standard.string(forKey: "deviceCode") ?? ""
        if !code.isEmpty {
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(code, forType: .string)
        }
    }

    @objc private func promptDeviceCode() {
        let alert = NSAlert()
        alert.messageText = "Kode perangkat"
        alert.informativeText = "Masukkan kode 9 digit dari akun XyDesk Anda (halaman Perangkat di web)."
        let field = NSTextField(frame: NSRect(x: 0, y: 0, width: 220, height: 24))
        field.stringValue = UserDefaults.standard.string(forKey: "deviceCode") ?? ""
        alert.accessoryView = field
        alert.addButton(withTitle: "Simpan")
        alert.addButton(withTitle: "Batal")
        if alert.runModal() == .alertFirstButtonReturn {
            let digits = field.stringValue.filter(\.isNumber)
            if digits.count == 9 {
                UserDefaults.standard.set(digits, forKey: "deviceCode")
                startSession(code: digits)
                rebuildMenu()
            }
        }
    }

    @objc private func promptPin() {
        let alert = NSAlert()
        alert.messageText = "Password pairing"
        alert.informativeText = "Kosongkan untuk menerima semua permintaan. Klien harus memasukkan password yang sama."
        let field = NSSecureTextField(frame: NSRect(x: 0, y: 0, width: 220, height: 24))
        field.stringValue = UserDefaults.standard.string(forKey: "pairingPassword") ?? ""
        alert.accessoryView = field
        alert.addButton(withTitle: "Simpan")
        alert.addButton(withTitle: "Batal")
        if alert.runModal() == .alertFirstButtonReturn {
            UserDefaults.standard.set(field.stringValue, forKey: "pairingPassword")
        }
    }
}

// Entry point top-level (file main.swift).
let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.run()
