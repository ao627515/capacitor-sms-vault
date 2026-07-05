// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorSmsVault",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapacitorSmsVault",
            targets: ["SmsVaultPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "SmsVaultPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/SmsVaultPlugin"),
        .testTarget(
            name: "SmsVaultPluginTests",
            dependencies: ["SmsVaultPlugin"],
            path: "ios/Tests/SmsVaultPluginTests")
    ]
)