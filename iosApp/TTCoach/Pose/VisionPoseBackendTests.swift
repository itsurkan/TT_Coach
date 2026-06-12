import XCTest
import CoreVideo
import TTCoachShared

@testable import TTCoach

final class VisionPoseBackendTests: XCTestCase {

    var backend: VisionPoseBackend!

    override func setUp() {
        super.setUp()
        backend = VisionPoseBackend()
    }

    override func tearDown() {
        super.tearDown()
        backend = nil
    }

    // MARK: - Mock CVPixelBuffer for testing

    /// Creates a blank BGRA CVPixelBuffer for testing (no person in frame).
    private func createMockPixelBuffer() -> CVPixelBuffer {
        var pixelBuffer: CVPixelBuffer?
        let status = CVPixelBufferCreate(
            kCFAllocatorDefault,
            1920,  // width
            1080,  // height
            kCVPixelFormatType_32BGRA,
            nil,
            &pixelBuffer
        )
        precondition(status == kCVReturnSuccess, "Failed to create CVPixelBuffer")
        // Zero-fill so Vision sees a deterministic black frame.
        CVPixelBufferLockBaseAddress(pixelBuffer!, [])
        if let base = CVPixelBufferGetBaseAddress(pixelBuffer!) {
            memset(base, 0, CVPixelBufferGetDataSize(pixelBuffer!))
        }
        CVPixelBufferUnlockBaseAddress(pixelBuffer!, [])
        return pixelBuffer!
    }

    // MARK: - Tests

    /// Synthetic blank buffer: Vision should not hallucinate a person, but the
    /// hard contract is: either empty (no detection) or exactly 17 COCO keypoints.
    func testEstimatePoseContractOnSyntheticBuffer() {
        let buffer = createMockPixelBuffer()
        let keypoints = backend.estimatePose(
            in: buffer,
            frameWidth: 1920,
            frameHeight: 1080
        )
        XCTAssertTrue(
            keypoints.isEmpty || keypoints.count == 17,
            "estimatePose must return empty or exactly 17 COCO keypoints, got \(keypoints.count)"
        )
    }

    /// Smoke test: repeated calls don't crash (verifies request reuse is safe).
    func testRepeatedInference() {
        let buffer = createMockPixelBuffer()
        for _ in 0..<5 {
            let result = backend.estimatePose(
                in: buffer,
                frameWidth: 1920,
                frameHeight: 1080
            )
            XCTAssertTrue(
                result.isEmpty || result.count == 17,
                "Repeated inference should keep the empty-or-17 contract"
            )
        }
    }

    /// Test that any returned keypoints are in normalized [0, 1] range (schema v2 contract).
    func testKeypointsNormalized() {
        let buffer = createMockPixelBuffer()
        let keypoints = backend.estimatePose(
            in: buffer,
            frameWidth: 1920,
            frameHeight: 1080
        )
        for kp in keypoints where kp.score > 0 {
            XCTAssertGreaterThanOrEqual(kp.x, 0, "x should be in [0, 1]")
            XCTAssertLessThanOrEqual(kp.x, 1, "x should be in [0, 1]")
            XCTAssertGreaterThanOrEqual(kp.y, 0, "y should be in [0, 1]")
            XCTAssertLessThanOrEqual(kp.y, 1, "y should be in [0, 1]")
            XCTAssertGreaterThanOrEqual(kp.score, 0, "score should be in [0, 1]")
            XCTAssertLessThanOrEqual(kp.score, 1, "score should be in [0, 1]")
        }
    }

    // MARK: - Mapper integration (via synthetic Vision keypoints)

    /// Verify that synthetic 19-joint Vision data round-trips through the mapper
    /// and converts to the shared module's Keypoint2D, as the backend does internally.
    func testMapperIntegration() {
        let visionKps = (0..<19).map { i in
            VisionCoco17Mapper.VisionKeypoint(
                x: Float(i) / 100.0,
                y: 0.5,
                confidence: 0.8
            )
        }
        let coco17 = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )
        XCTAssertEqual(coco17.count, 17, "Mapper should produce 17 COCO keypoints")

        // Convert to Keypoint2D (as VisionPoseBackend does internally).
        let keypoints = coco17.map { kp in
            Keypoint2D(x: kp.x, y: kp.y, score: kp.confidence)
        }
        XCTAssertEqual(keypoints.count, 17, "Converted array should have 17 keypoints")
    }

    // MARK: - Protocol contract verification

    /// PoseBackend conformance is a compile-time guarantee; this just exercises
    /// the protocol-typed call path.
    func testProtocolMethodSignature() {
        let poseBackend: PoseBackend = backend
        let buffer = createMockPixelBuffer()
        let result = poseBackend.estimatePose(in: buffer, frameWidth: 1920, frameHeight: 1080)
        XCTAssertTrue(result.isEmpty || result.count == 17)
    }
}
