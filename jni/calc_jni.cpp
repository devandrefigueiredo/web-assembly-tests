#include <jni.h>
#include "calc.h"

// JNI wrapper. It only forwards to the shared core in ../cpp/calc.cpp.
// No try/catch is possible here that would survive __builtin_trap(): the trap
// raises a native signal that kills the process before any Kotlin catch runs.
//
// The symbol name follows the Kotlin class: com.devandrefigueiredo.wasmtests.jni.NativeCalc
extern "C" JNIEXPORT jdouble JNICALL
Java_com_devandrefigueiredo_wasmtests_jni_NativeCalc_divide(JNIEnv* /*env*/, jobject /*thiz*/, jint a, jint b) {
    return calc_divide(a, b);
}
