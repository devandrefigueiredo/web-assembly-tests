pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WasmTests"

include(":app")

// The two transports live as sibling folders, each a self-contained library module.
include(":jni")
project(":jni").projectDir = file("../jni")

include(":webassembly")
project(":webassembly").projectDir = file("../webassembly")
