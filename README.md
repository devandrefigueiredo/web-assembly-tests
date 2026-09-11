# web-assembly-tests

A POC with two points, both from the *same* C++ core compiled by the *same* compiler
(the Android NDK's clang) into two transports:

**1. Resilience / containment** — on a division by zero the two transports diverge:

- **JNI** (native `.so`): the fault raises a native signal (SIGILL) that **kills the
  whole process** — a Kotlin `try/catch` does not save it.
- **WebAssembly** (interpreted by [Chicory](https://github.com/dylibso/chicory) inside
  the JVM): the same fault is a wasm trap that surfaces as an ordinary
  `RuntimeException`, so a normal `try/catch` **contains it and the app stays alive**.

**2. Hot update** — the wasm module is not frozen into the APK the way the `.so` is.
The frozen ABI is `double calc_divide(int, int)`; only its *body* changes between
versions. Every push to `master` publishes a GitHub release with a fresh `calc.wasm`,
and the app offers to download the latest and **re-instantiate it without a restart**.
The native side would need a whole new app build (and a store review) to change.

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

## Releases & hot update

- **CI** ([`.github/workflows/release.yml`](.github/workflows/release.yml)): on every
  push to `master`, GitHub Actions installs `clang`+`lld`, compiles `cpp/calc.cpp` to
  `calc.wasm`, and publishes a release tagged `v<version>` — the version read from
  [`webassembly/VERSION`](webassembly/VERSION) — with `calc.wasm` attached. Bump
  `VERSION` to make a new "latest".
- **The app** ships bundled at `0.1` (JNI + wasm both at `0.1`). On a WebAssembly
  submit it asks the public `releases/latest` API; if the latest version is higher than
  the wasm module currently loaded, it shows *"Version X is available. Would you like to
  update?"*. On **Yes** it downloads that release's `calc.wasm`, re-instantiates
  ([`WasmCalc.update`](webassembly/src/main/java/com/devandrefigueiredo/wasmtests/wasm/WasmCalc.kt)),
  and delivers the result with the new module — no restart. Offline, it keeps the last
  module that loaded. The label under Submit always says which version produced the
  result (`· v0.1`).

## Demo script

**Act 1 — resilience**

1. `10 / 2`, **WebAssembly** → `5`. `10 / 2`, **JNI** → `5`. Both engines agree.
2. `10 / 0`, **WebAssembly** → the result shows `erro`, a toast says
   *"Trapped on unreachable instruction"*, and **the app keeps running**.
3. `10 / 0`, **JNI** → the process dies (SIGILL in `libcalc.so`, tombstone in logcat).
   You have to reopen the app.

Put step 2 and step 3 side by side: the same `try/catch`, the same input, the same
C++ — one is contained, the other takes the whole app down.

**Act 2 — hot update (no rebuild, no restart)**

Start with the app open at `v0.1`, `10 / 4` on **WebAssembly** → `2` (integer division).

1. Edit [`cpp/calc.cpp`](cpp/calc.cpp): change `return (double)(a / b);` to
   `return (double)a / (double)b;`.
2. Bump [`webassembly/VERSION`](webassembly/VERSION) to `0.2`.
3. Commit and push to `master`. CI publishes release `v0.2` with the new `calc.wasm`.
4. Back in the **still-running** app, press Submit again on **WebAssembly**. The dialog
   *"Version 0.2 is available…"* appears → **Yes** → `10 / 4 = 2.5`, label `· v0.2`.
5. Switch to **JNI** and Submit: still `2`, label `· built-in v0.1` — the native side
   didn't change, because it can't without a new app build.

## Verified

Built and exercised on an Android 16 `x86_64` emulator:

| input | engine | outcome |
| --- | --- | --- |
| 10 / 4 | WebAssembly | `2`, app alive, `· v0.1` (integer division widened to double) |
| 10 / 0 | WebAssembly | `erro` + caught `TrapException`, app alive |
| 10 / 0 | JNI | `Fatal signal 4 (SIGILL)` from `libcalc.so`, process dead |
| update to a newer release | WebAssembly | dialog → Yes → downloads, re-instantiates, `· v0.2`, no restart |

CI is green: pushing `master` published release `v0.1` with `calc.wasm` attached, and
the app upgraded to it live (verified against the real release with a throwaway build
whose baseline was `0.0`).
