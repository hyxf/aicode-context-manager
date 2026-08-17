package com.aicode.feature.gradle.model

data class ExportResult(
    val totalDependencies: Int = 0,
    val exportedFiles: Int = 0,
    val skippedFiles: Int = 0,
    val failedDependencies: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val cancelled: Boolean = false,
    val durationMillis: Long = 0,
)
