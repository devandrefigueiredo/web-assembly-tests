package com.devandrefigueiredo.wasmtests.wasm

import android.content.Context
import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import java.io.File

/**
 * WebAssembly transport. Runs the SAME C++ core, compiled to calc.wasm, inside
 * the Chicory interpreter (pure JVM).
 *
 * The module is hot-swappable AND persistent:
 *  - [init] loads a previously downloaded module from internal storage if one exists,
 *    otherwise the fallback bundled in the APK's assets.
 *  - [update] re-instantiates from bytes fetched at runtime (a newer release) and
 *    persists them, so a kill + relaunch keeps the downloaded version instead of
 *    dropping back to the bundled one (and re-prompting for the same update).
 *  - Offline, the last persisted (or bundled) module keeps serving.
 *
 * The wasm ABI is frozen as `double calc_divide(int, int)`, so a newer module that
 * only changes the computation still binds to this exact call.
 *
 * A division by zero reaches __builtin_trap(), the `unreachable` opcode in wasm.
 * Chicory turns that into a plain [RuntimeException] that propagates out of [divide]
 * — so a normal try/catch contains it and the app stays alive.
 */
object WasmCalc {
    private const val BUNDLED_VERSION = "0.1"
    private const val PREFS = "wasm"
    private const val KEY_VERSION = "version"
    private const val CURRENT_FILE = "calc-current.wasm"

    private lateinit var appContext: Context
    private var divideFn: ExportFunction? = null

    @Volatile
    var activeVersion: String = BUNDLED_VERSION
        private set

    /** Loads the persisted downloaded module if present, else the bundled fallback. */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (divideFn != null) return

        val persistedVersion = prefs().getString(KEY_VERSION, null)
        val persistedFile = currentFile()
        if (persistedVersion != null && persistedFile.exists()) {
            try {
                load(persistedFile.readBytes())
                activeVersion = persistedVersion
                return
            } catch (e: Exception) {
                // Corrupt persisted module — drop it and fall back to the bundle.
                persistedFile.delete()
                prefs().edit().remove(KEY_VERSION).apply()
            }
        }
        load(appContext.assets.open("calc.wasm").use { it.readBytes() })
        activeVersion = BUNDLED_VERSION
    }

    /** Hot-swaps the running module for one downloaded at runtime AND persists it. */
    fun update(bytes: ByteArray, version: String) {
        load(bytes) // throws before we persist if the download is not a valid module
        activeVersion = version
        persist(bytes, version)
    }

    private fun persist(bytes: ByteArray, version: String) {
        val tmp = File(appContext.filesDir, "$CURRENT_FILE.tmp")
        tmp.writeBytes(bytes)
        val dst = currentFile()
        if (!tmp.renameTo(dst)) {
            dst.writeBytes(bytes)
            tmp.delete()
        }
        // commit(), not apply(): update() already runs off the main thread, and the version
        // must hit disk before a kill, or the next launch re-offers the same update.
        prefs().edit().putString(KEY_VERSION, version).commit()
    }

    private fun load(bytes: ByteArray) {
        val module = Parser.parse(bytes)
        divideFn = Instance.builder(module).build().export("calc_divide")
    }

    private fun currentFile() = File(appContext.filesDir, CURRENT_FILE)

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** @throws com.dylibso.chicory.wasm.ChicoryException on a wasm trap (e.g. divide by zero). */
    fun divide(a: Int, b: Int): Double {
        val fn = divideFn ?: error("WasmCalc.init() was not called")
        val rawBits = fn.apply(a.toLong(), b.toLong())[0]
        return java.lang.Double.longBitsToDouble(rawBits)
    }
}
