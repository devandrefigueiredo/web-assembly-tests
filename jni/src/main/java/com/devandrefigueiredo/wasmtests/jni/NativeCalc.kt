package com.devandrefigueiredo.wasmtests.jni

/**
 * JNI transport. Calls straight into libcalc.so (the shared C++ core).
 *
 * It is baked into the APK at build time — unlike the wasm module, it can only
 * change with a new app build. In the demo it stays at the app's built-in version
 * while the wasm side hot-updates.
 *
 * A division by zero reaches __builtin_trap() inside the .so, which raises a
 * native signal (SIGILL). That kills the whole process — a Kotlin try/catch
 * around [divide] does NOT save it.
 */
object NativeCalc {
    init { System.loadLibrary("calc") }

    external fun divide(a: Int, b: Int): Double
}
