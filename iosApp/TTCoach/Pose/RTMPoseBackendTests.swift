import XCTest
import simd

@testable import TTCoach

/// Unit tests for `RTMPoseBackend`'s PURE pieces — `normalize` (per-axis divide +
/// [0,1] clamp) and `selectBest` (argmax mean-score person selection). No ORT,
/// no CVPixelBuffer, no inference: these are the only parts decoupled from the
/// ONNX sessions, and they encode the schema-v2 output contract + the export's
/// `best_person` rule, so they are worth pinning directly.
final class RTMPoseBackendTests: XCTestCase {

    // MARK: - normalize

    func testNormalizeDividesPerAxis() {
        let kps = [SIMD2<Float>(320, 240), SIMD2<Float>(640, 480)]
        let scores: [Float] = [0.5, 0.9]
        let out = RTMPoseBackend.normalize(
            keypoints: kps, scores: scores, frameWidth: 640, frameHeight: 480)

        XCTAssertEqual(out.count, 2)
        XCTAssertEqual(out[0].x, 0.5, accuracy: 1e-6)   // 320 / 640
        XCTAssertEqual(out[0].y, 0.5, accuracy: 1e-6)   // 240 / 480
        XCTAssertEqual(out[0].score, 0.5, accuracy: 1e-6)
        XCTAssertEqual(out[1].x, 1.0, accuracy: 1e-6)   // 640 / 640
        XCTAssertEqual(out[1].y, 1.0, accuracy: 1e-6)   // 480 / 480
        XCTAssertEqual(out[1].score, 0.9, accuracy: 1e-6)
    }

    func testNormalizeClampsCoordsAndScoresToUnitRange() {
        // Out-of-frame pixels (negative + beyond extent) and out-of-range scores.
        let kps = [SIMD2<Float>(-10, -5), SIMD2<Float>(1000, 1000)]
        let scores: [Float] = [-0.2, 1.7]
        let out = RTMPoseBackend.normalize(
            keypoints: kps, scores: scores, frameWidth: 100, frameHeight: 100)

        XCTAssertEqual(out[0].x, 0.0, accuracy: 1e-6)   // clamp low
        XCTAssertEqual(out[0].y, 0.0, accuracy: 1e-6)
        XCTAssertEqual(out[0].score, 0.0, accuracy: 1e-6)
        XCTAssertEqual(out[1].x, 1.0, accuracy: 1e-6)   // clamp high
        XCTAssertEqual(out[1].y, 1.0, accuracy: 1e-6)
        XCTAssertEqual(out[1].score, 1.0, accuracy: 1e-6)
    }

    func testNormalizeBoundariesStayInRange() {
        let kps = [SIMD2<Float>(0, 0), SIMD2<Float>(640, 480)]
        let scores: [Float] = [0, 1]
        let out = RTMPoseBackend.normalize(
            keypoints: kps, scores: scores, frameWidth: 640, frameHeight: 480)
        XCTAssertEqual(out[0].x, 0.0, accuracy: 1e-6)
        XCTAssertEqual(out[1].x, 1.0, accuracy: 1e-6)
        XCTAssertEqual(out[1].y, 1.0, accuracy: 1e-6)
    }

    func testNormalizeShortScoresArrayDefaultsMissingToZero() {
        let kps = [SIMD2<Float>(10, 10), SIMD2<Float>(20, 20)]
        let scores: [Float] = [0.7]  // only one score for two keypoints
        let out = RTMPoseBackend.normalize(
            keypoints: kps, scores: scores, frameWidth: 100, frameHeight: 100)
        XCTAssertEqual(out.count, 2)
        XCTAssertEqual(out[0].score, 0.7, accuracy: 1e-6)
        XCTAssertEqual(out[1].score, 0.0, accuracy: 1e-6)
    }

    func testNormalizeZeroDimensionsReturnsEmpty() {
        let kps = [SIMD2<Float>(10, 10)]
        XCTAssertTrue(RTMPoseBackend.normalize(
            keypoints: kps, scores: [0.5], frameWidth: 0, frameHeight: 100).isEmpty)
        XCTAssertTrue(RTMPoseBackend.normalize(
            keypoints: kps, scores: [0.5], frameWidth: 100, frameHeight: 0).isEmpty)
    }

    // MARK: - selectBest

    func testSelectBestPicksHighestMeanScore() {
        let a = (keypoints: [SIMD2<Float>(0, 0)], scores: [Float](repeating: 0.2, count: 3))
        let b = (keypoints: [SIMD2<Float>(0, 0)], scores: [Float](repeating: 0.9, count: 3))
        let c = (keypoints: [SIMD2<Float>(0, 0)], scores: [Float](repeating: 0.5, count: 3))
        XCTAssertEqual(RTMPoseBackend.selectBest(candidates: [a, b, c]), 1)
    }

    func testSelectBestUsesMeanNotMax() {
        // Candidate 0 has a higher single peak but lower mean than candidate 1.
        let peaky = (keypoints: [SIMD2<Float>(0, 0)], scores: [Float]([1.0, 0.0, 0.0, 0.0]))
        let even = (keypoints: [SIMD2<Float>(0, 0)], scores: [Float]([0.4, 0.4, 0.4, 0.4]))
        XCTAssertEqual(RTMPoseBackend.selectBest(candidates: [peaky, even]), 1)
    }

    func testSelectBestEmptyReturnsNil() {
        let empty: [(keypoints: [SIMD2<Float>], scores: [Float])] = []
        XCTAssertNil(RTMPoseBackend.selectBest(candidates: empty))
    }

    func testSelectBestTieResolvesToFirst() {
        let a = (keypoints: [SIMD2<Float>(0, 0)], scores: [Float](repeating: 0.5, count: 2))
        let b = (keypoints: [SIMD2<Float>(0, 0)], scores: [Float](repeating: 0.5, count: 2))
        XCTAssertEqual(RTMPoseBackend.selectBest(candidates: [a, b]), 0)
    }

    func testSelectBestCandidateWithNoScoresCountsAsZero() {
        let none = (keypoints: [SIMD2<Float>](), scores: [Float]())
        let some = (keypoints: [SIMD2<Float>(0, 0)], scores: [Float]([0.1]))
        XCTAssertEqual(RTMPoseBackend.selectBest(candidates: [none, some]), 1)
    }

    func testSelectBestSingleCandidate() {
        let only = (keypoints: [SIMD2<Float>(0, 0)], scores: [Float]([0.3]))
        XCTAssertEqual(RTMPoseBackend.selectBest(candidates: [only]), 0)
    }
}
