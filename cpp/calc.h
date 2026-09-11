#ifndef CALC_H
#define CALC_H

// Shared, transport-agnostic core.
// The SAME source file is compiled twice by the SAME compiler (the NDK's clang):
//   - to a native .so  (loaded through JNI)
//   - to a .wasm module (loaded through the Chicory interpreter)
//
// The contract is FROZEN as `double calc_divide(int, int)` so the running app never
// has to change to accept a new module. Only the *body* changes between versions:
//   - v0.1: integer division widened to double   -> (double)(a / b)
//   - v0.2: real float division (the live demo)   -> (double)a / (double)b
//
// On a division by zero it calls __builtin_trap():
//   - native  -> illegal instruction -> SIGILL -> the whole process dies
//   - wasm    -> `unreachable` opcode -> Chicory throws a catchable exception
extern "C" double calc_divide(int a, int b);

#endif // CALC_H
