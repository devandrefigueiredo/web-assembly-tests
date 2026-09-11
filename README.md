# web-assembly-tests

A POC that shows **resilience/containment**: the *same* C++ core, compiled by the
*same* compiler (the Android NDK's clang) into two transports, behaves in opposite
ways on a division by zero.

- **JNI** (native `.so`): the fault raises a native signal (SIGILL) that **kills the
  whole process** — a Kotlin `try/catch` does not save it.
- **WebAssembly** (interpreted by [Chicory](https://github.com/dylibso/chicory) inside
  the JVM): the same fault is a wasm trap that surfaces as an ordinary
  `RuntimeException`, so a normal `try/catch` **contains it and the app stays alive**.

## Folders

Each transport is a **self-contained Android library module** — its native/wasm
build logic *and* its Kotlin API live together. The app is a thin UI that depends on
both and knows nothing about the NDK, clang, or Chicory.

| folder | Gradle module | what it holds |
| --- | --- | --- |
| `cpp/` | — | `calc.cpp` / `calc.h` — the shared, transport-agnostic core (`calc_divide`). On `b == 0` it calls `__builtin_trap()`. |
| `jni/` | `:jni` | `calc_jni.cpp` + `CMakeLists.txt` (compiles `../cpp/calc.cpp` → `libcalc.so`) **and** `NativeCalc.kt`, the Kotlin API. |
| `webassembly/` | `:webassembly` | the `buildWasm` Gradle task (compiles `../cpp/calc.cpp` → `calc.wasm`, no source of its own) **and** `WasmCalc.kt`, the Kotlin API. Carries the Chicory dependency (`api`). See `build-wasm.md`. |
| `test_app/` | `:app` | the dumb UI only: one screen, `EditText / EditText = result`, a `JNI`/`WebAssembly` radio, and Submit. Depends on `:jni` and `:webassembly`. |

The two modules live as sibling folders of `test_app/` and are wired in via
`test_app/settings.gradle.kts` (`project(":jni").projectDir = file("../jni")`).

## The one trick that makes it work

`__builtin_trap()` is the only failure primitive that is deterministic on **both**
transports and **every** CPU:

- native ARM64 **and** x86 → illegal instruction → **SIGILL** (process dies).
- wasm → the `unreachable` opcode → **Chicory throws** `TrapException` (catchable).

Plain integer division by zero was rejected on purpose: it is silent on ARM64
(returns 0), so it would not crash on a real phone or on an Apple-Silicon emulator.
`abort()` / `assert(false)` were rejected too — `abort()` drags a WASI import the bare
Chicory bridge doesn't provide, and `assert` vanishes under `NDEBUG`.

## Build & run

Requires the Android SDK + NDK `27.1.12297006` (set in `test_app/app/build.gradle.kts`).
No wasi-sdk or emscripten needed — the NDK's own clang has the `wasm32` backend.

```bash
cd test_app
./gradlew :webassembly:buildWasm  # compiles cpp/calc.cpp -> webassembly/src/main/assets/calc.wasm
./gradlew :app:assembleDebug      # :jni builds libcalc.so (all ABIs), :webassembly bundles the wasm
./gradlew :app:installDebug       # install on a connected device/emulator
```

`buildWasm` runs automatically before the `:webassembly` module builds
(`preBuild.dependsOn(buildWasm)`), so the `.wasm` is always regenerated from the
current `cpp/`.

## Demo script

1. `10 / 2`, **WebAssembly** → `5`. `10 / 2`, **JNI** → `5`. Both engines agree.
2. `10 / 0`, **WebAssembly** → the result shows `erro`, a toast says
   *"Trapped on unreachable instruction"*, and **the app keeps running**.
3. `10 / 0`, **JNI** → the process dies (SIGILL in `libcalc.so`, tombstone in logcat).
   You have to reopen the app.

Put step 2 and step 3 side by side: the same `try/catch`, the same input, the same
C++ — one is contained, the other takes the whole app down.

## Verified

Built and exercised on an Android 16 `x86_64` emulator:

| input | engine | outcome |
| --- | --- | --- |
| 10 / 2 | JNI | `5`, app alive |
| 10 / 2 | WebAssembly | `5`, app alive |
| 10 / 0 | WebAssembly | `erro` + caught `TrapException`, app alive |
| 10 / 0 | JNI | `Fatal signal 4 (SIGILL)` from `libcalc.so`, process dead |
