package com.devandrefigueiredo.wasmtests.wasm

import android.content.Context
import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser

/**
 * WebAssembly transport. Runs the SAME C++ core, compiled to calc.wasm, inside
 * the Chicory interpreter (pure JVM).
 *
 * The module is hot-swappable: [init] loads the bundled fallback that ships in the
 * APK, and [update] re-instantiates from bytes fetched at runtime (a newer release).
 * No restart is needed — the next [divide] uses whatever module is loaded. If the
 * network is gone, the last successfully loaded module keeps serving.
 *
 * The wasm ABI is frozen as `double calc_divide(int, int)`, so a newer module that
 * only changes the computation still binds to this exact call.
 *
 * A division by zero reaches __builtin_trap(), which is the `unreachable` opcode
 * in wasm. Chicory turns that into a plain [RuntimeException] that propagates out
 * of [divide] — so a normal try/catch contains it and the app stays alive.
 */
object WasmCalc {
    private const val BUNDLED_VERSION = "0.1"

    private var divideFn: ExportFunction? = null

    @Volatile
    var activeVersion: String = BUNDLED_VERSION
        private set

    /** Loads the fallback module bundled in the app's assets. */
    fun init(context: Context) {
        if (divideFn != null) return
        load(context.assets.open("calc.wasm").use { it.readBytes() })
        activeVersion = BUNDLED_VERSION
    }

    /** Hot-swaps the running module for one downloaded at runtime. */
    fun update(bytes: ByteArray, version: String) {
        load(bytes)
        activeVersion = version
    }

    private fun load(bytes: ByteArray) {
        val module = Parser.parse(bytes)
        divideFn = Instance.builder(module).build().export("calc_divide")
    }

    /** @throws com.dylibso.chicory.wasm.ChicoryException on a wasm trap (e.g. divide by zero). */
    fun divide(a: Int, b: Int): Double {
        val fn = divideFn ?: error("WasmCalc.init() was not called")
        val rawBits = fn.apply(a.toLong(), b.toLong())[0]
        return java.lang.Double.longBitsToDouble(rawBits)
    }
}
