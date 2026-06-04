// swift-tools-version: 5.10
//
// SwiftSonarParser — the Swift-side companion binary spawned by the Java plugin.
// Reads parse requests from stdin as NDJSON and emits parse responses on stdout.
//
// Build:    swift build -c release
// Binary:   .build/release/SwiftSonarParser
// Test:     swift test

import PackageDescription

let package = Package(
    name: "SwiftSonarParser",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(name: "SwiftSonarParser", targets: ["SwiftSonarParser"])
    ],
    dependencies: [
        // Pinned to swift-syntax 600.x to match recent Swift major releases.
        // Bump together with the Swift toolchain you build against.
        .package(url: "https://github.com/swiftlang/swift-syntax.git",
                 from: "600.0.0")
    ],
    targets: [
        .executableTarget(
            name: "SwiftSonarParser",
            dependencies: [
                .product(name: "SwiftSyntax", package: "swift-syntax"),
                .product(name: "SwiftParser", package: "swift-syntax"),
                .product(name: "SwiftDiagnostics", package: "swift-syntax")
            ],
            path: "Sources/SwiftSonarParser"
        ),
        .testTarget(
            name: "SwiftSonarParserTests",
            dependencies: ["SwiftSonarParser"],
            path: "Tests/SwiftSonarParserTests"
        )
    ]
)
