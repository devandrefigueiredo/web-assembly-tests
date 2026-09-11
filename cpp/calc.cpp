#include "calc.h"

extern "C" double calc_divide(int a, int b) {
    if (b == 0) {
        // Deterministic on every CPU and on both transports:
        //   native -> SIGILL (crash)   |   wasm -> trap (catchable in Chicory)
        __builtin_trap();
    }
    // v0.1: integer division, then widened to the frozen double contract.
    // The live demo changes ONLY this line to: return (double)a / (double)b;
    return (double)(a / b);
}
