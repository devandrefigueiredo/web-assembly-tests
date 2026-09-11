# webassembly/

This is the `:webassembly` Gradle library module. It has no C/C++ source of its own:
the `.wasm` is built from the very same `../cpp/calc.cpp` that the JNI `.so` is built
from — one core, one compiler. Alongside it lives the Kotlin API (`WasmCalc`) and the
Chicory dependency, so the app just depends on this module.

The build is driven by this module's own Gradle task, `:webassembly:buildWasm`
(declared in `build.gradle.kts`), which invokes the NDK's own clang:

```
<ndk>/toolchains/llvm/prebuilt/<host>/bin/clang \
    --target=wasm32 -nostdlib -O2 \
    -Wl,--no-entry -Wl,--export-all \
    -o test_app/app/src/main/assets/calc.wasm \
    ../cpp/calc.cpp
```

- `--target=wasm32` — the wasm backend that ships inside the Android NDK's clang.
- `-nostdlib` / `-Wl,--no-entry` — freestanding module, no libc, no `_start`. This keeps
  the module free of any WASI import, so Chicory can instantiate it with no host imports.
- `-Wl,--export-all` — exports `calc_divide` so the Kotlin side can call it.

The task wires itself before `preBuild`, so building this module (and therefore the
app) regenerates the `.wasm`. The output lands in this module's own
`src/main/assets/calc.wasm` and is merged into the APK's assets.
