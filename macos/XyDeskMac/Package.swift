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
        // Distribusi biner WebRTC (xcframework) dari stasel/WebRTC — kanonis,
        // dipakai luas. (Overtake/GoogleWebRTC menghilang 2026-11; sudah diganti.)
        .package(url: "https://github.com/stasel/WebRTC", branch: "latest"),
    ],
    targets: [
        .target(
            name: "XyDeskCore",
            dependencies: [.product(name: "WebRTC", package: "WebRTC")]
        ),
        .executableTarget(
            name: "XyDeskHost",
            dependencies: ["XyDeskCore", .product(name: "WebRTC", package: "WebRTC")]
        ),
        .executableTarget(
            name: "XyDeskClient",
            dependencies: ["XyDeskCore", .product(name: "WebRTC", package: "WebRTC")]
        ),
    ]
)
