import org.gradle.internal.os.OperatingSystem

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Self-contained WebAssembly module: it compiles the SAME ../cpp/calc.cpp to
// calc.wasm (using the NDK's own clang — no wasi-sdk / emscripten), bundles it as
// an asset, and exposes the Kotlin API (WasmCalc, backed by the Chicory runtime).
// The test app just depends on this module and calls WasmCalc.divide().
val ndkVer = "27.1.12297006"

android {
    namespace = "com.devandrefigueiredo.wasmtests.wasm"
    compileSdk = 35
    ndkVersion = ndkVer

    defaultConfig {
        minSdk = 26
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
    // `api` so the app gets the runtime transitively without declaring it.
    api("com.dylibso.chicory:runtime:1.1.0")
    api("com.dylibso.chicory:wasm:1.1.0")
}

// ---------------------------------------------------------------------------
// buildWasm: compiles ../cpp/calc.cpp to a freestanding calc.wasm via the NDK's
// clang, dropping it into THIS module's assets. Runs before preBuild, so building
// the module (and thus the app) always regenerates the module from the current cpp/.
// ---------------------------------------------------------------------------
val buildWasm by tasks.registering(Exec::class) {
    group = "build"
    description = "Compiles ../cpp/calc.cpp to a freestanding calc.wasm via the NDK clang"

    val os = OperatingSystem.current()
    val hostTag = when {
        os.isWindows -> "windows-x86_64"
        os.isMacOsX -> "darwin-x86_64"
        else -> "linux-x86_64"
    }
    val exe = if (os.isWindows) "clang.exe" else "clang"
    val clang = File(android.sdkDirectory, "ndk/$ndkVer/toolchains/llvm/prebuilt/$hostTag/bin/$exe")

    val src = file("../cpp/calc.cpp")
    val outWasm = file("src/main/assets/calc.wasm")

    inputs.file(src)
    outputs.file(outWasm)

    doFirst { outWasm.parentFile.mkdirs() }

    commandLine(
        clang.absolutePath,
        "--target=wasm32", "-nostdlib", "-O2",
        "-Wl,--no-entry", "-Wl,--export-all",
        "-o", outWasm.absolutePath,
        src.absolutePath
    )
}

tasks.named("preBuild") {
    dependsOn(buildWasm)
}
