package com.aicode.feature.gradle.model

import java.nio.file.Path

data class ExportOptions(
    val repository: Path,
    val scope: ExportScope,
    val includeTests: Boolean,
    val includeBuildscriptClasspath: Boolean = true,
)
