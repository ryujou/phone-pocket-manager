// swift-tools-version: 6.2
import PackageDescription
let package = Package(name: "PocketCore", platforms: [.macOS(.v14)], products: [.library(name: "PocketCore", targets: ["PocketCore"])], targets: [.target(name: "PocketCore", path: "Core"), .testTarget(name: "PocketCoreTests", dependencies: ["PocketCore"], path: "Tests")])
