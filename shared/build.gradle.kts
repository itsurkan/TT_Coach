import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
