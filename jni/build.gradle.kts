plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Self-contained JNI module: the C++ core (../cpp) + the JNI wrapper are compiled
// into libcalc.so (every ABI), and the Kotlin API (NativeCalc) is bundled alongside.
// The test app just depends on this module and calls NativeCalc.divide().
android {
    namespace = "com.devandrefigueiredo.wasmtests.jni"
    compileSdk = 35
    ndkVersion = "27.1.12297006"

    defaultConfig {
        minSdk = 26
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
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
