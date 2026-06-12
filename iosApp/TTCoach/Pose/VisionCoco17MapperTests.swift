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

    /// Test that input with fewer than 18 joints returns empty (malformed).
    func testInsufficientJoints() {
        let visionKps = (0..<17).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: 0.9)
        }
        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )
        XCTAssertEqual(result.count, 0, "Input with < 18 joints should yield empty output")
    }

    /// Test y-flip transformation: y_vision=0.1 (bottom) -> y_coco=0.9 (top-aligned).
    func testYFlip() {
        var visionKps: [VisionCoco17Mapper.VisionKeypoint] = []
        // Build a minimal 18-joint skeleton with specific y values.
        for i in 0..<18 {
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
        for i in 0..<18 {
            // Assign unique x values per Vision joint so we can track mappings.
            let x = Float(i) / 100.0  // 0.00, 0.01, 0.02, ... 0.17
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
        for i in 0..<18 {
            let conf = Float(i) / 18.0  // 0.0, 0.056, 0.111, ..., 0.944
            visionKps.append(VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: conf))
        }

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17, "Should produce 17 COCO keypoints")
        // Spot-check: Vision 3 (conf=0.167...) -> COCO 5
        XCTAssertAlmostEqual(result[5].confidence, Float(3) / 18.0, accuracy: 0.0001, "Confidence should pass through")
    }

    /// Test that all 17 COCO indices are populated (no skipped indices).
    /// Eye/ear indices (1-4) are synthesized with zero confidence if Vision doesn't provide them.
    func testAllCocoIndicesPresent() {
        var visionKps: [VisionCoco17Mapper.VisionKeypoint] = []
        for _ in 0..<18 {
            visionKps.append(VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: 0.9))
        }

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17, "All 17 COCO indices must be present")
    }

    /// Test that eye/ear placeholders are synthesized with zero confidence.
    func testEyeEarPlaceholders() {
        var visionKps: [VisionCoco17Mapper.VisionKeypoint] = []
        // Build 18-joint skeleton with high confidence for body, but
        // we'll verify eye/ear get synthesized even with high input confidence.
        for _ in 0..<18 {
            visionKps.append(VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: 0.9))
        }

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17, "Should have all 17 indices")
        // Eye/ear indices (1-4) in the result should have zero confidence
        // because Vision doesn't provide them natively.
        for idx in [1, 2, 3, 4] {
            XCTAssertEqual(result[idx].confidence, 0, "Eye/ear (index \(idx)) should have zero confidence")
        }
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

    /// Test bestPerson with detections of varying array sizes.
    func testBestPersonWithVariingArraySizes() {
        let person1 = (0..<10).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: 0.9)
        }
        let person2 = (0..<20).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: 0.7)
        }

        let best = VisionCoco17Mapper.bestPerson(from: [person1, person2])
        XCTAssertNotNil(best, "Should select a person despite varying array sizes")
        // person1 mean = 0.9, person2 mean = 0.7, so person1 should win
        XCTAssertEqual(best!.count, 10, "Should select person1 (smaller but higher confidence)")
    }

    /// Test bestPerson when all detections have very low confidence.
    func testBestPersonAllLowConfidence() {
        let person1 = (0..<17).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: 0.05)
        }
        let person2 = (0..<17).map { _ in
            VisionCoco17Mapper.VisionKeypoint(x: 0.5, y: 0.5, confidence: 0.08)
        }

        let best = VisionCoco17Mapper.bestPerson(from: [person1, person2])
        XCTAssertNotNil(best, "Should still select even when all are low confidence")
        // person2 mean = 0.08 > person1 mean = 0.05
        XCTAssertAlmostEqual(best![0].confidence, 0.08, accuracy: 0.0001, "Should select person2 (higher low confidence)")
    }

    /// Test mapToCoco17 with exactly 18 joints (boundary condition).
    func testMapToCoco17With18JointsExact() {
        var visionKps: [VisionCoco17Mapper.VisionKeypoint] = []
        for i in 0..<18 {
            let conf = Float(i) / 18.0
            visionKps.append(VisionCoco17Mapper.VisionKeypoint(x: Float(i) / 100.0, y: 0.5, confidence: conf))
        }

        let result = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: visionKps,
            frameWidth: 1920,
            frameHeight: 1080
        )

        XCTAssertEqual(result.count, 17, "Exactly 18 joints input should produce exactly 17 COCO joints")
        // All indices should be populated (no gaps)
        for i in 0..<17 {
            XCTAssertNotNil(result[i], "COCO index \(i) should be present")
        }
    }

    // MARK: - Helper

    func XCTAssertAlmostEqual(_ a: Float, _ b: Float, accuracy: Float, _ message: String = "") {
        XCTAssertTrue(abs(a - b) < accuracy, "\(a) ≠ \(b) (±\(accuracy)) — \(message)")
    }
}
