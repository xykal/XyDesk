// swift-tools-version: 5.9
// Paket macOS XyDesk — host (penyedia layar) + klien (pengendali) native Swift.
// Protokol signaling, input biner, dan WebRTC SAMA dengan Android/Web/Windows,
// sehingga Mac bisa mengendalikan PC Windows dan dikendalikan dari Android/Web.
import PackageDescription

let package = Package(
    name: "XyDeskMac",
    platforms: [.macOS(.v13)],
    products: [
        .executable(name: "XyDeskMacHost", targets: ["XyDeskHost"]),
        .executable(name: "XyDeskMacClient", targets: ["XyDeskClient"]),
    ],
    dependencies: [
        // Distribusi biner WebRTC (xcframework). Bila repo ini berpindah,
        // pasang xcframework WebRTC manual dan ganti target linkage — lihat README.
        .package(url: "https://github.com/Overtake/GoogleWebRTC", branch: "master"),
    ],
    targets: [
        .target(
            name: "XyDeskCore",
            dependencies: [.product(name: "GoogleWebRTC", package: "GoogleWebRTC")]
        ),
        .executableTarget(
            name: "XyDeskHost",
            dependencies: ["XyDeskCore", .product(name: "GoogleWebRTC", package: "GoogleWebRTC")]
        ),
        .executableTarget(
            name: "XyDeskClient",
            dependencies: ["XyDeskCore", .product(name: "GoogleWebRTC", package: "GoogleWebRTC")]
        ),
    ]
)
