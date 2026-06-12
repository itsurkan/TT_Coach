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

    /// Builds session options, appending the CoreML Execution Provider when available.
    /// Falls back to plain CPU (the ORT default) if CoreML EP cannot be appended.
    private static func makeSessionOptions(modelLabel: String) throws -> ORTSessionOptions {
        let options = try ORTSessionOptions()
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
    static func makeSession(modelPath: String, modelLabel: String? = nil) throws -> ORTSession {
        let label = modelLabel ?? (modelPath as NSString).lastPathComponent
        guard FileManager.default.fileExists(atPath: modelPath) else {
            throw ORTSessionFactoryError.modelFileMissing(path: modelPath)
        }
        let options = try makeSessionOptions(modelLabel: label)
        do {
            return try ORTSession(env: sharedEnv, modelPath: modelPath, sessionOptions: options)
        } catch {
            throw ORTSessionFactoryError.sessionCreationFailed(model: label, underlying: error)
        }
    }

    /// Creates a session for a model file URL.
    static func makeSession(modelURL: URL, modelLabel: String? = nil) throws -> ORTSession {
        try makeSession(modelPath: modelURL.path, modelLabel: modelLabel)
    }

    /// Creates a session from an app-bundle resource (used by the iOS app backend).
    static func makeSession(
        bundleResource name: String,
        extension ext: String = "onnx",
        in bundle: Bundle = .main
    ) throws -> ORTSession {
        guard let url = bundle.url(forResource: name, withExtension: ext) else {
            throw ORTSessionFactoryError.modelFileMissing(path: "\(name).\(ext) (in bundle \(bundle.bundlePath))")
        }
        return try makeSession(modelURL: url, modelLabel: name)
    }
}
