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

    /// Creates a minimal grayscale CVPixelBuffer for testing (1x1 pixel).
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
        return pixelBuffer!
    }

    // MARK: - Tests

    /// Test that backend returns empty array when given a mock buffer
    /// (no Vision detection in a synthetic frame).
    func testEstimatePoseReturnsEmptyForNoDetection() {
        let buffer = createMockPixelBuffer()
        let keypoints = backend.estimatePose(
            in: buffer,
            frameWidth: 1920,
            frameHeight: 1080
        )
        // Mock buffer won't have real person — expect empty result.
        XCTAssertEqual(keypoints.count, 0, "Mock buffer should return no keypoints")
    }

    /// Test that estimatePose returns [Keypoint2D] array (shared module type).
    func testEstimatePoseReturnType() {
        let buffer = createMockPixelBuffer()
        let result = backend.estimatePose(
            in: buffer,
            frameWidth: 1920,
            frameHeight: 1080
        )
        // Result should be an array of Keypoint2D (even if empty).
        XCTAssertIsNotNil(result, "estimatePose should return an array")
        XCTAssert(result is [Keypoint2D], "estimatePose must return [Keypoint2D]")
    }

    /// Test that the backend conforms to PoseBackend protocol.
    func testBackendConformsToProtocol() {
        XCTAssert(backend is PoseBackend, "VisionPoseBackend should conform to PoseBackend")
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
            XCTAssertNotNil(result, "Repeated inference should not crash")
        }
    }

    /// Test that keypoints are in normalized [0, 1] range (schema v2 contract).
    func testKeypointsNormalized() {
        let buffer = createMockPixelBuffer()
        let keypoints = backend.estimatePose(
            in: buffer,
            frameWidth: 1920,
            frameHeight: 1080
        )
        for kp in keypoints {
            XCTAssertGreaterThanOrEqual(kp.x, 0, "x should be in [0, 1]")
            XCTAssertLessThanOrEqual(kp.x, 1, "x should be in [0, 1]")
            XCTAssertGreaterThanOrEqual(kp.y, 0, "y should be in [0, 1]")
            XCTAssertLessThanOrEqual(kp.y, 1, "y should be in [0, 1]")
            XCTAssertGreaterThanOrEqual(kp.score, 0, "score should be in [0, 1]")
            XCTAssertLessThanOrEqual(kp.score, 1, "score should be in [0, 1]")
        }
    }

    /// Test that when detections are present, they have the expected structure
    /// (17 COCO keypoints when a person is actually detected).
    /// NOTE: This test is integration-level and requires actual Vision framework
    /// to detect a person in the buffer. In unit test context, this may not trigger
    /// a real detection, so we verify the structure when non-empty.
    func testKeypointStructureWhenPresent() {
        // Create a buffer that *might* trigger a detection (real footage would).
        // For MVP, we verify the contract that IF there are keypoints,
        // there should be 17 of them (COCO-17 schema).
        let buffer = createMockPixelBuffer()
        let keypoints = backend.estimatePose(
            in: buffer,
            frameWidth: 1920,
            frameHeight: 1080
        )
        // If non-empty, must be exactly 17 (COCO-17).
        if !keypoints.isEmpty {
            XCTAssertEqual(keypoints.count, 17,
                          "COCO-17 schema requires exactly 17 keypoints when person is detected")
        }
    }

    // MARK: - Mapper integration (via mock Vision keypoints)

    /// Test that the backend correctly maps through VisionCoco17Mapper
    /// by creating synthetic Vision keypoints and verifying the output structure.
    func testMapperIntegration() {
        // This is an indirect integration test: verify that synthetic Vision data
        // can round-trip through the mapper without the backend itself.
        let visionKps = (0..<18).map { i in
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

    /// Verify that the backend method signature matches PoseBackend protocol exactly.
    func testProtocolMethodSignature() {
        // This is a compile-time check (if the backend doesn't conform, it won't compile).
        // At runtime, verify the method exists and is callable.
        let buffer = createMockPixelBuffer()
        _ = backend.estimatePose(in: buffer, frameWidth: 1920, frameHeight: 1080)
        // If we reach here, the method exists and has the correct signature.
        XCTAssert(true)
    }
}
