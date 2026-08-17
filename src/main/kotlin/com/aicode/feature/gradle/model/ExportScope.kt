package com.aicode.feature.gradle.model

enum class ExportScope(val displayName: String) {
    RUNTIME("Runtime"),
    COMPILE_AND_RUNTIME("Compile + Runtime"),
    ALL("All resolvable configurations"),
}
