import Foundation

/// Protokol input biner XyDesk — SATU sumber kebenaran di sisi Swift.
/// Tata letak paket identik dengan `host/src/input.rs` (Windows) dan builder
/// `android-native/.../cpp/streamxy/input.cpp`: byte pertama = tag, angka
/// little-endian. Klien memBANGUN paket, host MENDODEKANYA menjadi aksi.
public enum StreamXy {
    // Tag tipe pesan (byte pertama) — cermin `mod tag` di host/src/input.rs.
    public static let moveRel: UInt8 = 0x01
    public static let moveAbs: UInt8 = 0x02
    public static let button: UInt8 = 0x03
    public static let scroll: UInt8 = 0x04
    public static let key: UInt8 = 0x05
    public static let text: UInt8 = 0x06
    public static let displaySelect: UInt8 = 0x07
    public static let clipboardSet: UInt8 = 0x08
    public static let clipboardReq: UInt8 = 0x09
    public static let quality: UInt8 = 0x0A
    public static let bitrate: UInt8 = 0x0B
    public static let resolution: UInt8 = 0x0C
    public static let gamepad: UInt8 = 0x0E
    public static let fps: UInt8 = 0x0F
    public static let virtualDisplay: UInt8 = 0x10

    // ---- Pembangun paket (sisi klien) ----

    public static func moveRel(dx: Int16, dy: Int16) -> Data {
        var d = Data([moveRel])
        d.appendLE(dx); d.appendLE(dy)
        return d
    }

    /// Posisi ternormalisasi 0..1 (dikalikan 65535 di kabel, sama dengan
    /// `sx_input_move_abs` pada C++ Android).
    public static func moveAbs(x: Double, y: Double) -> Data {
        var d = Data([moveAbs])
        d.appendLE(UInt16(max(0, min(1, x)) * 65535))
        d.appendLE(UInt16(max(0, min(1, y)) * 65535))
        return d
    }

    /// `button`: 1=kiri, 2=kanan, 3=tengah (sama dengan host/src/input.rs).
    public static func button(_ button: UInt8, down: Bool) -> Data {
        Data([self.button, button, down ? 1 : 0])
    }

    public static func scroll(dx: Int16, dy: Int16) -> Data {
        var d = Data([scroll])
        d.appendLE(dx); d.appendLE(dy)
        return d
    }

    /// `vk` = kode virtual-key WINDOWS (protokol dipakai lintas platform;
    /// host Swift memetakan VK → CGKeyCode di InputInjector).
    public static func key(vk: UInt16, down: Bool) -> Data {
        var d = Data([key])
        d.appendLE(vk)
        d.append(down ? 1 : 0)
        return d
    }

    public static func text(_ s: String) -> Data {
        var d = Data([text])
        d.append(Data(s.utf8))
        return d
    }

    public static func displaySelect(_ index: UInt8) -> Data {
        Data([displaySelect, index])
    }

    public static func clipboardSet(_ s: String) -> Data {
        var d = Data([clipboardSet])
        d.append(Data(s.utf8))
        return d
    }

    /// Paket minta clipboard (padanan `StreamXy.clipboardReq()` di Android).
    public static let clipboardReqData = Data([clipboardReq])

    public static func quality(_ preset: UInt8) -> Data {
        Data([quality, preset])
    }

    public static func bitrate(mbps: UInt16) -> Data {
        var d = Data([bitrate])
        d.appendLE(mbps)
        return d
    }

    public static func resolution(_ mode: UInt8) -> Data {
        Data([resolution, mode])
    }

    public static func fps(_ target: UInt8) -> Data {
        Data([fps, target])
    }

    public static func virtualDisplay(width: UInt16, height: UInt16, refreshHz: UInt8, count: UInt8) -> Data {
        var d = Data([virtualDisplay])
        d.appendLE(width); d.appendLE(height)
        d.append(refreshHz); d.append(count)
        return d
    }

    public static func gamepad(buttons: UInt16, lt: UInt8, rt: UInt8,
                               lx: Int16, ly: Int16, rx: Int16, ry: Int16) -> Data {
        var d = Data([gamepad])
        d.appendLE(buttons); d.append(lt); d.append(rt)
        d.appendLE(lx); d.appendLE(ly); d.appendLE(rx); d.appendLE(ry)
        return d
    }

    // ---- Dekoder paket (sisi host) ----

    /// Hasil dekode — cermin `enum InputEvent` di host/src/input.rs.
    /// `dx/dy` relatif dalam piksel; `nx/ny` ternormalisasi 0..1.
    public enum InputEvent: Equatable {
        case moveRel(dx: Int16, dy: Int16)
        case moveAbs(nx: Double, ny: Double)
        case button(button: UInt8, down: Bool)
        case scroll(dx: Int16, dy: Int16)
        case key(vk: UInt16, down: Bool)
        case text(String)
        case displaySelect(UInt8)
        case clipboardSet(String)
        case clipboardReq
        case quality(UInt8)
        case bitrate(UInt16)
        case resolution(UInt8)
        case gamepad(buttons: UInt16, lt: UInt8, rt: UInt8, lx: Int16, ly: Int16, rx: Int16, ry: Int16)
        case fps(UInt8)
        case virtualDisplay(width: UInt16, height: UInt16, refreshHz: UInt8, count: UInt8)
    }

    /// Dekode satu paket; `nil` bila rusak/tak dikenal (dibuang diam-diam,
    /// sama dengan `decode()` Rust yang mengembalikan `Option`).
    public static func decode(_ d: Data) -> InputEvent? {
        guard let tag = d.first else { return nil }
        switch tag {
        case moveRel where d.count >= 5:
            return .moveRel(dx: d.le16(1), dy: d.le16(3))
        case moveAbs where d.count >= 5:
            return .moveAbs(nx: Double(d.le16(1)) / 65535, ny: Double(d.le16(3)) / 65535)
        case button where d.count >= 3:
            return .button(button: d[1], down: d[2] != 0)
        case scroll where d.count >= 5:
            return .scroll(dx: d.le16(1), dy: d.le16(3))
        case key where d.count >= 4:
            return .key(vk: UInt16(bitPattern: d.le16(1)), down: d[3] != 0)
        case text where d.count >= 2:
            return .text(String(data: d.dropFirst(), encoding: .utf8) ?? "")
        case displaySelect where d.count >= 2:
            return .displaySelect(d[1])
        case clipboardSet where d.count >= 2:
            return .clipboardSet(String(data: d.dropFirst(), encoding: .utf8) ?? "")
        case clipboardReq:
            return .clipboardReq
        case quality where d.count >= 2:
            return .quality(d[1])
        case bitrate where d.count >= 3:
            return .bitrate(UInt16(bitPattern: d.le16(1)))
        case resolution where d.count >= 2:
            return .resolution(d[1])
        case gamepad where d.count >= 13:
            return .gamepad(buttons: UInt16(bitPattern: d.le16(1)), lt: d[3], rt: d[4],
                            lx: d.le16(5), ly: d.le16(7), rx: d.le16(9), ry: d.le16(11))
        case fps where d.count >= 2:
            return .fps(d[1])
        case virtualDisplay where d.count >= 7:
            return .virtualDisplay(width: UInt16(bitPattern: d.le16(1)), height: UInt16(bitPattern: d.le16(3)),
                                   refreshHz: d[5], count: d[6])
        default:
            return nil
        }
    }
}

extension Data {
    mutating func appendLE<T: FixedWidthInteger>(_ v: T) {
        var le = v.littleEndian
        Swift.withUnsafeBytes(of: &le) { self.append(contentsOf: $0) }
    }

    func le16(_ offset: Int) -> Int16 {
        guard count >= offset + 2 else { return 0 }
        return Int16(UInt8(self[offset])) | Int16(UInt8(self[offset + 1])) << 8
    }
}
