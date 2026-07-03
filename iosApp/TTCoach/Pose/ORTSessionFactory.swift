// ORTSessionFactory.swift
//
// Small reusable helper that builds configured ONNX Runtime sessions. Used by both
// the in-app pose backend (iOS) and the RTMPoseExport macOS CLI spike.
//
// The ONNX Runtime SPM package (microsoft/onnxruntime-swift-package-manager, product
// "onnxruntime") exposes the Objective-C API under the Swift module `OnnxRuntimeBindings`.
//
// We share a single ORTEnv across sessions and attempt to append the CoreML Execution
// Provider; if CoreML EP is unavailable or fails to append, we fall back to plain CPU
// (ORT's default) instead of failing. The chosen path is logged.

import Foundation
import OnnxRuntimeBindings

/// Errors surfaced when building an ORT session. Swift `throws` is fine here — this is
/// platform (Apple) code, not KMP shared logic.
enum ORTSessionFactoryError: Error, CustomStringConvertible {
    case modelFileMissing(path: String)
    case sessionCreationFailed(model: String, underlying: Error)

    var description: String {
        switch self {
        case .modelFileMissing(let path):
            return "ONNX model file not found at: \(path)"
        case .sessionCreationFailed(let model, let underlying):
            return "Failed to create ORTSession for \(model): \(underlying)"
        }
    }
}

/// Builds configured `ORTSession` instances backed by a single shared `ORTEnv`.
enum ORTSessionFactory {

    /// One process-wide ORT environment (log level: warning).
    private static let sharedEnv: ORTEnv = {
        // ORTEnv init can throw; if it does at process start, the spike is fundamentally
        // broken, so a fatalError with a clear message is acceptable here.
        do {
            return try ORTEnv(loggingLevel: ORTLoggingLevel.warning)
        } catch {
            fatalError("Failed to initialize ORTEnv: \(error)")
        }
    }()

    /// Builds session options. When `coreML` is true, appends the CoreML Execution
    /// Provider (falling back to plain CPU if it cannot be appended). When `coreML`
    /// is false, the CoreML EP is skipped entirely and the session runs CPU-only.
    ///
    /// CPU-only matters for the YOLOX detector: its baked-in NMS has a dynamic-shape
    /// node the CoreML EP rejects (hard-fails when there are zero detections — common
    /// on real footage). See RTMPOSE_PARITY.md "Execution provider".
    private static func makeSessionOptions(modelLabel: String, coreML: Bool) throws -> ORTSessionOptions {
        let options = try ORTSessionOptions()
        guard coreML else {
            print("[ORTSessionFactory] \(modelLabel): CPU-only (CoreML EP skipped by request).")
            return options
        }
        do {
            let coreMLOptions = ORTCoreMLExecutionProviderOptions()
            try options.appendCoreMLExecutionProvider(with: coreMLOptions)
            print("[ORTSessionFactory] \(modelLabel): CoreML Execution Provider appended.")
        } catch {
            // CoreML EP not available on this platform/build, or append failed — fall
            // back to CPU. This is not fatal; ORT runs on CPU by default.
            print("[ORTSessionFactory] \(modelLabel): CoreML EP unavailable (\(error)); falling back to CPU.")
        }
        return options
    }

    /// Creates a session for a model at an absolute file path (used by the macOS CLI).
    /// Pass `coreML: false` for a CPU-only session (required for the YOLOX detector).
    static func makeSession(modelPath: String, modelLabel: String? = nil, coreML: Bool = true) throws -> ORTSession {
        let label = modelLabel ?? (modelPath as NSString).lastPathComponent
        guard FileManager.default.fileExists(atPath: modelPath) else {
            throw ORTSessionFactoryError.modelFileMissing(path: modelPath)
        }
        let options = try makeSessionOptions(modelLabel: label, coreML: coreML)
        do {
            return try ORTSession(env: sharedEnv, modelPath: modelPath, sessionOptions: options)
        } catch {
            throw ORTSessionFactoryError.sessionCreationFailed(model: label, underlying: error)
        }
    }

    /// Creates a session for a model file URL.
    static func makeSession(modelURL: URL, modelLabel: String? = nil, coreML: Bool = true) throws -> ORTSession {
        try makeSession(modelPath: modelURL.path, modelLabel: modelLabel, coreML: coreML)
    }

    /// Creates a session from an app-bundle resource (used by the iOS app backend).
    static func makeSession(
        bundleResource name: String,
        extension ext: String = "onnx",
        in bundle: Bundle = .main,
        coreML: Bool = true
    ) throws -> ORTSession {
        guard let url = bundle.url(forResource: name, withExtension: ext) else {
            throw ORTSessionFactoryError.modelFileMissing(path: "\(name).\(ext) (in bundle \(bundle.bundlePath))")
        }
        return try makeSession(modelURL: url, modelLabel: name, coreML: coreML)
    }
}
