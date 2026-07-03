import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    jvm()

    // iOS targets (compile only on a macOS host; Kotlin disables them elsewhere
    // with a warning, so jvmTest/Android builds stay green on Linux CI).
    // Build the framework for Xcode with:
    //   ./gradlew :shared:assembleTTCoachSharedXCFramework        (both configs)
    //   ./gradlew :shared:assembleTTCoachSharedDebugXCFramework   (debug only)
    // Output: shared/build/XCFrameworks/<config>/TTCoachShared.xcframework
    // The iosApp Xcode project instead uses the direct-integration task
    // :shared:embedAndSignAppleFrameworkForXcode from a Run Script build phase
    // (see iosApp/project.yml).
    val xcf = XCFramework("TTCoachShared")
    listOf(
        iosArm64(),          // physical iPhones
        iosSimulatorArm64()  // Simulator on Apple-silicon Macs
        // iosX64 intentionally omitted: Intel-Mac Simulator only; the dev Mac is M4.
        // Add `iosX64()` here if an Intel Mac ever needs the Simulator.
    ).forEach { target ->
        target.binaries.framework {
            baseName = "TTCoachShared"
            // Static framework: simplest embedding (no dyld/embed step needed for
            // the binary itself), fine for a single shared framework.
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // No external dependencies — pure Kotlin only
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.ttcoachai.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}
