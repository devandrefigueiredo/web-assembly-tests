package com.devandrefigueiredo.wasmtests

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.devandrefigueiredo.wasmtests.jni.NativeCalc
import com.devandrefigueiredo.wasmtests.wasm.WasmCalc

class MainActivity : AppCompatActivity() {

    private lateinit var inputA: EditText
    private lateinit var inputB: EditText
    private lateinit var result: TextView
    private lateinit var engineGroup: RadioGroup
    private lateinit var engineInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WasmCalc.init(this)

        inputA = findViewById(R.id.inputA)
        inputB = findViewById(R.id.inputB)
        result = findViewById(R.id.result)
        engineGroup = findViewById(R.id.engineGroup)
        engineInfo = findViewById(R.id.engineInfo)
        findViewById<Button>(R.id.submit).setOnClickListener { onSubmit() }
    }

    private fun onSubmit() {
        val a = inputA.text.toString().toIntOrNull()
        val b = inputB.text.toString().toIntOrNull()
        if (a == null || b == null) {
            Toast.makeText(this, "Enter two integers", Toast.LENGTH_SHORT).show()
            return
        }
        if (engineGroup.checkedRadioButtonId == R.id.optWasm) {
            checkUpdateThenComputeWasm(a, b)
        } else {
            computeJni(a, b)
        }
    }

    // JNI: baked into the APK, cannot hot-update. A native SIGILL here blows past
    // this try/catch and kills the whole app.
    private fun computeJni(a: Int, b: Int) {
        try {
            val value = NativeCalc.divide(a, b)
            result.text = format(value)
            engineInfo.text = "engine: JNI (native .so) · built-in v${BuildConfig.VERSION_NAME}"
        } catch (e: RuntimeException) {
            result.text = "erro"
            engineInfo.text = "caught: ${e.javaClass.simpleName}: ${e.message}"
            Toast.makeText(this, "Erro na operação: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // WASM: before computing, ask GitHub if a newer release exists. If so, offer the
    // update; on "Yes" we download it and compute with the fresh module — no restart.
    private fun checkUpdateThenComputeWasm(a: Int, b: Int) {
        Thread {
            val release = try { ReleaseUpdater.latest() } catch (e: Exception) { null }
            runOnUiThread {
                if (release != null && ReleaseUpdater.isNewer(release.version, WasmCalc.activeVersion)) {
                    AlertDialog.Builder(this)
                        .setTitle("Update available")
                        .setMessage("Version ${release.version} is available. Would you like to update?")
                        .setPositiveButton("Yes") { _, _ -> downloadThenComputeWasm(release, a, b) }
                        .setNegativeButton("No") { _, _ -> computeWasm(a, b) }
                        .setCancelable(false)
                        .show()
                } else {
                    computeWasm(a, b)
                }
            }
        }.start()
    }

    private fun downloadThenComputeWasm(release: ReleaseUpdater.Release, a: Int, b: Int) {
        engineInfo.text = "downloading v${release.version}…"
        Thread {
            val ok = try {
                WasmCalc.update(ReleaseUpdater.download(release.wasmUrl), release.version)
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (!ok) {
                    Toast.makeText(this, "Update failed, using v${WasmCalc.activeVersion}", Toast.LENGTH_LONG).show()
                }
                computeWasm(a, b)
            }
        }.start()
    }

    private fun computeWasm(a: Int, b: Int) {
        try {
            val value = WasmCalc.divide(a, b)
            result.text = format(value)
            engineInfo.text = "engine: WebAssembly (Chicory) · v${WasmCalc.activeVersion}"
        } catch (e: RuntimeException) {
            result.text = "erro"
            engineInfo.text = "caught: ${e.javaClass.simpleName}: ${e.message}"
            Toast.makeText(this, "Erro na operação: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Whole doubles show as integers (v0.1 look); real fractions show the decimals (v0.2).
    private fun format(v: Double): String =
        if (v.isFinite() && v == Math.floor(v)) v.toLong().toString() else v.toString()
}
