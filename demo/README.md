# demo — Act 2 (hot update to v0.2, float division)

This patch is versioned on purpose: clone/pull the repo on the demo machine and it is
already here. It flips the wasm core from integer to float division and bumps the
release version, so pushing it triggers CI to publish `v0.2`.

From the repo root:

```bash
git apply demo/v0.2-float.patch
git add -A
git commit -m "wasm v0.2: float division"
git push origin master     # CI publishes release v0.2 with the new calc.wasm
```

Then, in the **still-running** app, press Submit on **WebAssembly**:
*"Version 0.2 is available"* → **Yes** → `10 / 4 = 2.5` (label `· v0.2`).

To rehearse again, reset the two files and re-apply:

```bash
git checkout -- cpp/calc.cpp webassembly/VERSION
git apply demo/v0.2-float.patch
```

The patch changes exactly two things:
- `cpp/calc.cpp`: `return (double)(a / b);` → `return (double)a / (double)b;`
- `webassembly/VERSION`: `0.1` → `0.2`
