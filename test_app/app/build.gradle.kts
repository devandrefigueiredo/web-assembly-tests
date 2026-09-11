plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The app is deliberately dumb: it owns only the screen. Both transports — the
// native .so and the wasm module, each with its own Kotlin API and build logic —
// come in as library modules (:jni and :webassembly).
android {
    namespace = "com.devandrefigueiredo.wasmtests"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.devandrefigueiredo.wasmtests"
        // Chicory 1.1.0 (pulled in by :webassembly) uses MethodHandle.invokeExact,
        // which D8 only accepts from API 26.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        // The app always ships at 0.1 (the bundled JNI + wasm). The wasm side then
        // hot-updates to whatever the latest GitHub release is.
        versionName = "0.1"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation(project(":jni"))
    implementation(project(":webassembly"))
}
