// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "SwiftTestHarness",
    platforms: [
        .macOS(.v15),
    ],
    dependencies: [
        .package(name: "Sentencepiece", path: "../build/SPMPackage/macosArm64/Debug")
    ],
    targets: [
        .testTarget(
            name: "SwiftTestHarnessTests",
            dependencies: [
                .product(name: "SentencepieceLibrary", package: "Sentencepiece")
            ],
            linkerSettings: [
                .unsafeFlags([
                    "-L", "../build/swift-test",
                    "-lSentencepiece",
                ]),
            ]
        ),
    ]
)