cmake_minimum_required(VERSION 3.22.1)
project(calc LANGUAGES CXX)

# libcalc.so = JNI wrapper + the shared core from ../cpp
add_library(calc SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/calc_jni.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../cpp/calc.cpp)

target_include_directories(calc PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../cpp)

find_library(log-lib log)
target_link_libraries(calc ${log-lib})
