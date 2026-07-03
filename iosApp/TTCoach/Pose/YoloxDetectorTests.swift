import CoreVideo
import XCTest
import simd

@testable import TTCoach

/// Tests for the pure YOLOX postprocess (`parseDets`) and the deterministic letterbox
/// sampler. Real ORT inference is exercised by the CLI parity gate later, not here.
final class YoloxDetectorTests: XCTestCase {

    // MARK: - parseDets

    func testParseDetsDividesCoordsByRatio() {
        // One box, ratio 0.5 -> coords double, score passes.
        let flat: [Float] = [100, 50, 300, 450, 0.9]
        let boxes = YoloxDetector.parseDets(detsFlat: flat, boxCount: 1, ratio: 0.5)
        XCTAssertEqual(boxes.count, 1)
        XCTAssertEqual(boxes[0], BoundingBox(x1: 200, y1: 100, x2: 600, y2: 900, score: 0.9))
    }

    func testParseDetsRatioOne() {
        let flat: [Float] = [10, 20, 30, 40, 0.5]
        let boxes = YoloxDetector.parseDets(detsFlat: flat, boxCount: 1, ratio: 1.0)
        XCTAssertEqual(boxes, [BoundingBox(x1: 10, y1: 20, x2: 30, y2: 40, score: 0.5)])
    }

    func testParseDetsScoreThresholdBoundary() {
        // Exactly 0.3 is EXCLUDED (rtmlib: score > 0.3); 0.31 is kept.
        let flat: [Float] = [
            0, 0, 1, 1, 0.30,
            0, 0, 1, 1, 0.31,
            0, 0, 1, 1, 0.299,
        ]
        let boxes = YoloxDetector.parseDets(detsFlat: flat, boxCount: 3, ratio: 1.0)
        XCTAssertEqual(boxes.count, 1)
        XCTAssertEqual(boxes[0].score, 0.31, accuracy: 1e-6)
    }

    func testParseDetsEmptyWhenZeroBoxes() {
        XCTAssertEqual(YoloxDetector.parseDets(detsFlat: [], boxCount: 0, ratio: 1.0), [])
    }

    func testParseDetsOrderingPreserved() {
        // Input is score-descending; output preserves it (no re-sort).
        let flat: [Float] = [
            0, 0, 10, 10, 0.95,
            0, 0, 20, 20, 0.80,
            0, 0, 30, 30, 0.40,
        ]
        let boxes = YoloxDetector.parseDets(detsFlat: flat, boxCount: 3, ratio: 1.0)
        XCTAssertEqual(boxes.map { $0.score }, [0.95, 0.80, 0.40])
        XCTAssertEqual(boxes.map { $0.x2 }, [10, 20, 30])
    }

    func testParseDetsAllBelowThreshold() {
        let flat: [Float] = [0, 0, 1, 1, 0.1, 0, 0, 1, 1, 0.2]
        XCTAssertEqual(YoloxDetector.parseDets(detsFlat: flat, boxCount: 2, ratio: 1.0), [])
    }

    func testParseDetsGuardsShortArray() {
        // boxCount claims 2 but only one row of data -> guard returns [].
        let flat: [Float] = [0, 0, 1, 1, 0.9]
        XCTAssertEqual(YoloxDetector.parseDets(detsFlat: flat, boxCount: 2, ratio: 1.0), [])
    }

    // MARK: - letterboxToTensor

    func testLetterboxToCHWShapeRatioPaddingAndBGROrder() throws {
        // 4x2 solid color, BGR = (10, 20, 30). ratio = min(640/2, 640/4) = 160.
        // resized = (4*160, 2*160) = (640, 320): full width, top 320 rows = image,
        // rows 320..639 = 114 padding. We test the pure CHW core; the thin
        // `letterboxToTensor` wrapper just wraps this buffer as shape [1,3,640,640].
        let pb = try makeSolidBGRAPixelBuffer(width: 4, height: 2, b: 10, g: 20, r: 30)
        let (floats, ratio) = try PixelBufferSampler.letterboxToCHW(pixelBuffer: pb, targetSize: 640)

        XCTAssertEqual(ratio, RTMPoseMath.letterboxRatio(imageWidth: 4, imageHeight: 2), accuracy: 1e-6)
        XCTAssertEqual(ratio, 160, accuracy: 1e-6)

        // Flat CHW length corresponds to shape [1, 3, 640, 640].
        XCTAssertEqual(floats.count, 3 * 640 * 640)

        let plane = 640 * 640
        // Interior image pixel (dx=320, dy=160): all neighbors in-bounds -> exact color.
        // BGR channel order: B plane = 10, G plane = 20, R plane = 30.
        let interior = 160 * 640 + 320
        XCTAssertEqual(floats[interior], 10, accuracy: 1e-3)               // B
        XCTAssertEqual(floats[plane + interior], 20, accuracy: 1e-3)       // G
        XCTAssertEqual(floats[2 * plane + interior], 30, accuracy: 1e-3)   // R

        // Padded region (row 500 >= 320) is 114 in every channel.
        let pad = 500 * 640 + 10
        XCTAssertEqual(floats[pad], 114, accuracy: 1e-6)
        XCTAssertEqual(floats[plane + pad], 114, accuracy: 1e-6)
        XCTAssertEqual(floats[2 * plane + pad], 114, accuracy: 1e-6)
    }

    // MARK: - helpers

    /// Build a solid-color 32BGRA CVPixelBuffer. Memory order per pixel: B, G, R, A.
    private func makeSolidBGRAPixelBuffer(
        width: Int, height: Int, b: UInt8, g: UInt8, r: UInt8
    ) throws -> CVPixelBuffer {
        var pb: CVPixelBuffer?
        let attrs: [CFString: Any] = [kCVPixelBufferCGImageCompatibilityKey: true]
        let status = CVPixelBufferCreate(
            kCFAllocatorDefault, width, height,
            kCVPixelFormatType_32BGRA, attrs as CFDictionary, &pb)
        guard status == kCVReturnSuccess, let buffer = pb else {
            throw NSError(domain: "test", code: Int(status),
                          userInfo: [NSLocalizedDescriptionKey: "CVPixelBufferCreate failed"])
        }
        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        let base = CVPixelBufferGetBaseAddress(buffer)!.assumingMemoryBound(to: UInt8.self)
        let bpr = CVPixelBufferGetBytesPerRow(buffer)
        for y in 0..<height {
            for x in 0..<width {
                let o = y * bpr + x * 4
                base[o] = b
                base[o + 1] = g
                base[o + 2] = r
                base[o + 3] = 255
            }
        }
        return buffer
    }
}
