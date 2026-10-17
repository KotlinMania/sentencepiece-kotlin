#if canImport(Testing)
import Testing
import Sentencepiece

@Suite("Sentencepiece Swift Export Tests")
struct SentencepieceExportTests {
    @Test("Sentencepiece swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        let span = PieceSpan(begin: 0, end: 5)
        #expect(span.begin == 0)
        #expect(span.end == 5)
    }
}
#elseif canImport(XCTest)
import XCTest
import Sentencepiece

final class SentencepieceExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        let span = PieceSpan(begin: 0, end: 5)
        XCTAssertEqual(span.begin, 0)
        XCTAssertEqual(span.end, 5)
    }
}
#endif
