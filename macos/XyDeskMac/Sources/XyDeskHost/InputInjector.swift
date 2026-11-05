import Foundation
import ApplicationServices
import AppKit
import XyDeskCore

/// Injeksi input ke sistem: paket StreamXy (dari klien) → CGEvent.
/// CATATAN protokol: tombol keyboard memakai virtual-key WINDOWS (lihat
/// StreamXy.key); peta VK → CGKeyCode ada di `keyCode`. Butuh izin
/// Accessibility (lihat `ensureTrust`).
enum InputInjector {
    private static let source = CGEventSource(stateID: .hidSystemState)

    static func ensureTrust() -> Bool {
        let opts = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
        return AXIsProcessTrustedWithOptions(opts)
    }

    /// Layar utama dalam koordinat global (untuk moveAbs ternormalisasi).
    private static var screenSize: CGSize {
        CGDisplayBounds(CGMainDisplayID()).size
    }

    static func handle(_ event: StreamXy.InputEvent) {
        switch event {
        case .moveRel(let dx, let dy):
            guard let loc = CGEvent(source: nil)?.location else { return }
            post(.mouseMoved, at: CGPoint(x: loc.x + CGFloat(dx), y: loc.y + CGFloat(dy)))

        case .moveAbs(let nx, let ny):
            let s = screenSize
            post(.mouseMoved, at: CGPoint(x: s.width * CGFloat(nx), y: s.height * CGFloat(ny)))

        case .button(let button, let down):
            let types: [UInt8: (CGEventType, CGMouseButton)] = [
                1: (down ? .leftMouseDown : .leftMouseUp, .left),
                2: (down ? .rightMouseDown : .rightMouseUp, .right),
                3: (down ? .otherMouseDown : .otherMouseUp, .center),
            ]
            guard let (type, btn) = types[button] else { return }
            post(type, button: btn)

        case .scroll(let dx, let dy):
            if let ev = CGEvent(scrollWheelEvent2Source: source, units: .pixel,
                                wheelCount: 2, wheel1: Int32(-dy), wheel2: Int32(dx), wheel3: 0) {
                ev.post(tap: .cghidEventTap)
            }

        case .key(let vk, let down):
            guard let code = keyCode(vk) else { return }
            if let ev = CGEvent(keyboardEventSource: source, virtualKey: code, keyDown: down) {
                ev.post(tap: .cghidEventTap)
            }

        case .text(let s):
            // Teks lewat unicode string — aman untuk IME/non-ASCII.
            let utf16 = Array(s.utf16)
            var i = 0
            while i < utf16.count {
                let chunk = Array(utf16[i..<min(i + 20, utf16.count)])
                if let ev = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: true) {
                    ev.keyboardSetUnicodeString(stringLength: chunk.count, unicodeString: chunk)
                    ev.post(tap: .cghidEventTap)
                }
                if let ev = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: false) {
                    ev.keyboardSetUnicodeString(stringLength: chunk.count, unicodeString: chunk)
                    ev.post(tap: .cghidEventTap)
                }
                i += 20
            }

        case .clipboardSet(let s):
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(s, forType: .string)

        case .clipboardReq:
            let s = NSPasteboard.general.string(forType: .string) ?? ""
            HostSession.shared?.sendClipboard(s)

        case .displaySelect(let index):
            CaptureEngineCoordinator.shared?.switchDisplay(Int(index))

        case .resolution(let mode), .quality(let mode):
            // Mode video ditangani AdaptiveVideo host; dicatat untuk HUD.
            HostSession.shared?.noteVideoHint("mode=\(mode)")

        case .bitrate(let mbps):
            HostSession.shared?.noteVideoHint("bitrate=\(mbps)Mbps")

        case .fps(let f):
            HostSession.shared?.noteVideoHint("fps=\(f)")

        case .virtualDisplay(let w, let h, let hz, let count):
            // Super screen macOS: poin integrasi CGVirtualDisplay (private) —
            // lihat README bagian "Layar virtual". Sementara dicatat.
            HostSession.shared?.noteVideoHint("virtual-display \(w)x\(h)@\(hz) x\(count)")

        case .gamepad:
            break // Gamepad = integrasi IOKit berikutnya; diterima tapi diabaikan.
        }
    }

    private static func post(_ type: CGEventType, at p: CGPoint) {
        if let ev = CGEvent(mouseEventSource: source, mouseType: type,
                            mouseCursorPosition: p, mouseButton: .left) {
            ev.post(tap: .cghidEventTap)
        }
    }

    private static func post(_ type: CGEventType, button: CGMouseButton) {
        guard let loc = CGEvent(source: nil)?.location else { return }
        if let ev = CGEvent(mouseEventSource: source, mouseType: type,
                            mouseCursorPosition: loc, mouseButton: button) {
            ev.post(tap: .cghidEventTap)
        }
    }

    /// Peta virtual-key Windows → CGKeyCode macOS (ANSI AS).
    /// Huruf/angka/F-keys/panah/modifier; sisanya nil (dibuang) sampai peta
    /// lengkap — protokol memang berbasis VK karena induknya host Windows.
    static func keyCode(_ vk: UInt16) -> CGKeyCode? {
        let map: [UInt16: CGKeyCode] = [
            0x08: 51, 0x09: 48, 0x0D: 36, 0x10: 56, 0x11: 59, 0x12: 58, 0x13: 57,
            0x14: 57, 0x1B: 53, 0x20: 49, 0x21: 126, 0x22: 124, 0x23: 125, 0x24: 123,
            0x25: 123, 0x26: 126, 0x27: 124, 0x28: 125,
            0x2C: 105, 0x2D: 118, 0x2E: 117, 0x5B: 55, 0x5C: 54, 0x5D: 123,
            0x70: 122, 0x71: 120, 0x72: 99, 0x73: 118, 0x74: 96, 0x75: 97,
            0x76: 98, 0x77: 100, 0x78: 101, 0x79: 109, 0x7A: 103, 0x7B: 111,
        ]
        if let c = map[vk] { return c }
        // A-Z (VK 0x41..0x5A) → kode ANSI 0..25 sesuai urutan QWERTY.
        if (0x41...0x5A).contains(vk) {
            let ansi: [UInt16] = [0, 13, 8, 2, 14, 3, 5, 4, 34, 38, 40, 37, 46, 45, 31, 35, 12, 15, 1, 17, 32, 9, 11, 7, 16, 6]
            return CGKeyCode(ansi[Int(vk - 0x41)])
        }
        // 0-9 (VK 0x30..0x39) → baris angka.
        if (0x30...0x39).contains(vk) {
            let ansi: [UInt16] = [29, 18, 19, 20, 21, 23, 22, 26, 28, 25]
            return CGKeyCode(ansi[Int(vk - 0x30)])
        }
        return nil
    }
}

/// Koordinasi display capture yang bisa dibangun dari sesi.
protocol CaptureEngineCoordinatorDelegate: AnyObject {
    func switchDisplay(_ index: Int)
}

final class CaptureEngineCoordinator {
    static weak var shared: CaptureEngineCoordinator?
    weak var delegate: CaptureEngineCoordinatorDelegate?
    func switchDisplay(_ index: Int) { delegate?.switchDisplay(index) }
}
