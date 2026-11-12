import Foundation

/// Penyimpanan JWT akun. Sementara diisi manual dari web;
/// lihat README untuk alur OAuth penuh. (Di XyDeskCore supaya dipakai
/// bersama oleh target Host maupun Klien.)
public enum TokenStore {
    private static let key = "xydesk.jwt"

    public static var jwt: String? {
        get {
            if let d = UserDefaults.standard.string(forKey: key), !d.isEmpty { return d }
            return nil
        }
        set {
            UserDefaults.standard.set(newValue ?? "", forKey: key)
        }
    }
}
