#if canImport(Testing)
import Testing
import Sentencepiece

@Suite("Sentencepiece Swift Export Tests")
struct SentencepieceExportTests {
    @Test("Sentencepiece swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        #expect(Bool(true), "Sentencepiece swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import Sentencepiece

final class SentencepieceExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "Sentencepiece swift module imported cleanly")
    }
}
#endif
