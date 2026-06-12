import XCTest

final class VisionCoco17MapperTests: XCTestCase {

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

    /// Test y-flip transformation: y_vision=0.1 (bottom) -> y_coco=0.9 (top-aligned).
    func testYFlip() {
        var visionKps: [VisionCoco17Mapper.VisionKeypoint] = []
        // Build a minimal 19-joint skeleton with specific y values.
        for i in 0..<19 {
            let y: Float = (i == 2) ? 0.1 : 0.5  // nose (index 2) at y=0.1
            visionKps.append(VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: y, confidence: 0.9))
        }

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17, "Should produce 17 COCO keypoints")
        // Nose is COCO index 0.
        let noseKeypointIdx = 0
        let flippedY = result[noseKeypointIdx].y
        XCTAssertAlmostEqual(flippedY, 0.9, accuracy: 0.0001, "y=0.1 (Vision bottom) should flip to 0.9 (COCO top)")
    }

    /// Test that joints map to correct COCO indices.
    func testJointMapping() {
        var visionKps: [VisionCoco17Mapper.VisionKeypoint] = []
        for i in 0..<19 {
            // Assign unique x values per Vision joint so we can track mappings.
            let x = Float(i) / 100.0  // 0.00, 0.01, 0.02, ... 0.18
            visionKps.append(VisionCoco17Mapper.VisionKeypoint(x: x, y: 0.5, confidence: 0.9))
        }

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17, "Should produce 17 COCO keypoints")

        // Spot-check a few mappings:
        // Vision 2 (nose, x=0.02) -> COCO 0 (nose)
        XCTAssertAlmostEqual(result[0].x, 0.02, accuracy: 0.0001, "Vision nose (x=0.02) should be at COCO[0]")

        // Vision 3 (leftShoulder, x=0.03) -> COCO 5 (leftShoulder)
        XCTAssertAlmostEqual(result[5].x, 0.03, accuracy: 0.0001, "Vision leftShoulder (x=0.03) should be at COCO[5]")

        // Vision 9 (leftHip, x=0.09) -> COCO 11 (leftHip)
        XCTAssertAlmostEqual(result[11].x, 0.09, accuracy: 0.0001, "Vision leftHip (x=0.09) should be at COCO[11]")
    }

    /// Test that confidence values pass through unchanged.
    func testConfidencePassthrough() {
        var visionKps: [VisionCoco17Mapper.VisionKeypoint] = []
        for i in 0..<19 {
            let conf = Float(i) / 19.0  // 0.0, 0.05, 0.10, ..., 0.95
            visionKps.append(VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: conf))
        }

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17, "Should produce 17 COCO keypoints")
        // Spot-check: Vision 3 (conf=0.157...) -> COCO 5
        XCTAssertAlmostEqual(result[5].confidence, Float(3) / 19.0, accuracy: 0.0001, "Confidence should pass through")
    }

    /// Test that all 17 COCO indices are populated (no skipped indices).
    func testAllCocoIndicesPresent() {
        var visionKps: [VisionCoco17Mapper.VisionKeypoint] = []
        for _ in 0..<19 {
            visionKps.append(VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: 0.9))
        }

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17, "All 17 COCO indices must be present")
    }

    /// Test multi-person detection: highest mean confidence wins.
    func testBestPersonSelection() {
        let person1 = (0..<17).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.3, y: 0.3, confidence: 0.8)
        }
        let person2 = (0..<17).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.7, y: 0.7, confidence: 0.95)
        }

        let best = VisionCoco17Mapper.bestPerson(from: [person1, person2])
        XCTAssertNotNil(best, "Should select a person")
        // Person 2 has higher mean confidence, so it should be selected.
        XCTAssertAlmostEqual(best![0].x, 0.7, accuracy: 0.0001, "Should select person with highest mean confidence")
    }

    /// Test empty detections array.
    func testBestPersonEmpty() {
        let best = VisionCoco17Mapper.bestPerson(from: [])
        XCTAssertNil(best, "Empty detections should return nil")
    }

    /// Test single detection.
    func testBestPersonSingle() {
        let person = (0..<17).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: 0.9)
        }

        let best = VisionCoco17Mapper.bestPerson(from: [person])
        XCTAssertNotNil(best, "Should select the only person")
        XCTAssertEqual(best!.count, 17, "Should return the same person")
    }

    // MARK: - Helper

    func XCTAssertAlmostEqual(_ a: Float, _ b: Float, accuracy: Float, _ message: String = "") {
        XCTAssertTrue(abs(a - b) < accuracy, "\(a) ≠ \(b) (±\(accuracy)) — \(message)")
    }
}
