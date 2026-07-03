import XCTest

@testable import TTCoach

final class VisionCoco17MapperTests: XCTestCase {

    /// Builds a 19-joint canonical Vision array with uniform values.
    private func uniformJoints(
        x: Float = 0.5, y: Float = 0.5, confidence: Float = 0.9
    ) -> [VisionCoco17Mapper.VisionKeypoint] {
        (0..<19).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: x, y: y, confidence: confidence)
        }
    }

    /// Test that empty input returns empty output.
    func testEmptyInput() {
        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: [],
            frameWidth: 1920,
            frameHeight: 1080
        )
        XCTAssertEqual(result.count, 0, "Empty input should yield empty output")
    }

    /// Test that input with fewer than 19 joints returns empty (malformed).
    func testInsufficientJoints() {
        let visionKps = (0..<18).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: 0.9)
        }
        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )
        XCTAssertEqual(result.count, 0, "Input with < 19 joints should yield empty output")
    }

    /// Test y-flip transformation: y_vision=0.1 (near bottom) -> y_coco=0.9 (near bottom in top-left coords).
    func testYFlip() {
        var visionKps: [VisionCoco17Mapper.VisionKeypoint] = []
        for i in 0..<19 {
            let y: Float = (i == 0) ? 0.1 : 0.5  // nose (canonical index 0) at y=0.1
            visionKps.append(VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: y, confidence: 0.9))
        }

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17, "Should produce 17 COCO keypoints")
        // Nose is COCO index 0.
        XCTAssertAlmostEqual(result[0].y, 0.9, accuracy: 0.0001, "y=0.1 (Vision bottom-left) should flip to 0.9 (COCO top-left)")
        // All other joints at y=0.5 flip to 0.5.
        XCTAssertAlmostEqual(result[5].y, 0.5, accuracy: 0.0001, "y=0.5 stays 0.5 after flip")
    }

    /// Test that joints map to correct COCO indices, including direct eye/ear mapping.
    func testJointMapping() {
        var visionKps: [VisionCoco17Mapper.VisionKeypoint] = []
        for i in 0..<19 {
            // Assign unique x values per canonical Vision joint so we can track mappings.
            let x = Float(i) / 100.0  // 0.00, 0.01, ... 0.18
            visionKps.append(VisionCoco17Mapper.VisionKeypoint(x: x, y: 0.5, confidence: 0.9))
        }

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17, "Should produce 17 COCO keypoints")

        // Vision 0 (nose) -> COCO 0
        XCTAssertAlmostEqual(result[0].x, 0.00, accuracy: 0.0001, "Vision nose should be at COCO[0]")
        // Vision 1 (leftEye) -> COCO 1 (direct eye mapping)
        XCTAssertAlmostEqual(result[1].x, 0.01, accuracy: 0.0001, "Vision leftEye should be at COCO[1]")
        // Vision 4 (rightEar) -> COCO 4 (direct ear mapping)
        XCTAssertAlmostEqual(result[4].x, 0.04, accuracy: 0.0001, "Vision rightEar should be at COCO[4]")
        // Vision 6 (leftShoulder) -> COCO 5 (neck at Vision 5 is dropped)
        XCTAssertAlmostEqual(result[5].x, 0.06, accuracy: 0.0001, "Vision leftShoulder should be at COCO[5]")
        // Vision 10 (leftWrist) -> COCO 9
        XCTAssertAlmostEqual(result[9].x, 0.10, accuracy: 0.0001, "Vision leftWrist should be at COCO[9]")
        // Vision 13 (leftHip) -> COCO 11 (root at Vision 12 is dropped)
        XCTAssertAlmostEqual(result[11].x, 0.13, accuracy: 0.0001, "Vision leftHip should be at COCO[11]")
        // Vision 18 (rightAnkle) -> COCO 16
        XCTAssertAlmostEqual(result[16].x, 0.18, accuracy: 0.0001, "Vision rightAnkle should be at COCO[16]")
    }

    /// Test that eyes/ears come through with their REAL coordinates and confidences
    /// (Vision provides them natively — no zero-confidence synthesis).
    func testEyeEarDirectMapping() {
        var visionKps = uniformJoints()
        visionKps[1] = VisionCoco17Mapper.VisionKeypoint(x: 0.45, y: 0.8, confidence: 0.77)  // leftEye
        visionKps[3] = VisionCoco17Mapper.VisionKeypoint(x: 0.40, y: 0.78, confidence: 0.66) // leftEar

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17)
        // COCO 1 = left_eye
        XCTAssertAlmostEqual(result[1].x, 0.45, accuracy: 0.0001, "leftEye x maps directly")
        XCTAssertAlmostEqual(result[1].y, 0.2, accuracy: 0.0001, "leftEye y flips (1 - 0.8)")
        XCTAssertAlmostEqual(result[1].confidence, 0.77, accuracy: 0.0001, "leftEye confidence passes through")
        // COCO 3 = left_ear
        XCTAssertAlmostEqual(result[3].x, 0.40, accuracy: 0.0001, "leftEar x maps directly")
        XCTAssertAlmostEqual(result[3].confidence, 0.66, accuracy: 0.0001, "leftEar confidence passes through")
    }

    /// Test that confidence values pass through unchanged.
    func testConfidencePassthrough() {
        var visionKps: [VisionCoco17Mapper.VisionKeypoint] = []
        for i in 0..<19 {
            let conf = Float(i) / 19.0
            visionKps.append(VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: conf))
        }

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17, "Should produce 17 COCO keypoints")
        // Vision 6 (leftShoulder, conf=6/19) -> COCO 5
        XCTAssertAlmostEqual(result[5].confidence, Float(6) / 19.0, accuracy: 0.0001, "Confidence should pass through")
        // Vision 12 (root) is dropped; COCO 11 carries Vision 13 (leftHip) confidence.
        XCTAssertAlmostEqual(result[11].confidence, Float(13) / 19.0, accuracy: 0.0001, "root is dropped, leftHip maps to COCO[11]")
    }

    /// Test that an undetected joint (zero-confidence placeholder) passes through
    /// with zero confidence — the shared pipeline gates on score < 0.3.
    func testUndetectedJointPlaceholder() {
        var visionKps = uniformJoints()
        visionKps[17] = VisionCoco17Mapper.VisionKeypoint(x: 0, y: 0, confidence: 0)  // leftAnkle missing

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17)
        // COCO 15 = left_ankle
        XCTAssertEqual(result[15].confidence, 0, "Undetected joint keeps zero confidence")
    }

    /// Test the canonical joint name list matches the 19-joint contract.
    func testCanonicalJointOrder() {
        let names = VisionCoco17Mapper.visionJointNames
        XCTAssertEqual(names.count, 19, "Canonical order must have 19 joints")
        XCTAssertEqual(names[0], .nose)
        XCTAssertEqual(names[1], .leftEye)
        XCTAssertEqual(names[4], .rightEar)
        XCTAssertEqual(names[5], .neck)
        XCTAssertEqual(names[12], .root)
        XCTAssertEqual(names[18], .rightAnkle)
    }

    /// Test multi-person detection: highest mean confidence wins.
    func testBestPersonSelection() {
        let person1 = (0..<19).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.3, y: 0.3, confidence: 0.8)
        }
        let person2 = (0..<19).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.7, y: 0.7, confidence: 0.95)
        }

        let best = VisionCoco17Mapper.bestPerson(from: [person1, person2])
        XCTAssertNotNil(best, "Should select a person")
        XCTAssertAlmostEqual(best![0].x, 0.7, accuracy: 0.0001, "Should select person with highest mean confidence")
    }

    /// Test empty detections array.
    func testBestPersonEmpty() {
        let best = VisionCoco17Mapper.bestPerson(from: [])
        XCTAssertNil(best, "Empty detections should return nil")
    }

    /// Test single detection.
    func testBestPersonSingle() {
        let person = uniformJoints()
        let best = VisionCoco17Mapper.bestPerson(from: [person])
        XCTAssertNotNil(best, "Should select the only person")
        XCTAssertEqual(best!.count, 19, "Should return the same person")
    }

    /// Test bestPerson when all detections have very low confidence.
    func testBestPersonAllLowConfidence() {
        let person1 = (0..<19).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: 0.05)
        }
        let person2 = (0..<19).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: 0.08)
        }

        let best = VisionCoco17Mapper.bestPerson(from: [person1, person2])
        XCTAssertNotNil(best, "Should still select even when all are low confidence")
        XCTAssertAlmostEqual(best![0].confidence, 0.08, accuracy: 0.0001, "Should select person2 (higher low confidence)")
    }

    /// Test mapToCoco17 with exactly 19 joints (boundary condition).
    func testMapToCoco17With19JointsExact() {
        let visionKps = (0..<19).map { i in
            VisionCoco17Mapper.VisionKeypoint(x: Float(i) / 100.0, y: 0.5, confidence: Float(i) / 19.0)
        }

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17, "Exactly 19 joints input should produce exactly 17 COCO joints")
    }

    // MARK: - Helper

    func XCTAssertAlmostEqual(_ a: Float, _ b: Float, accuracy: Float, _ message: String = "") {
        XCTAssertTrue(abs(a - b) < accuracy, "\(a) ≠ \(b) (±\(accuracy)) — \(message)")
    }
}
