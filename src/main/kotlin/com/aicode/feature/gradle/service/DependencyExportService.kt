package com.aicode.feature.gradle.service

import com.aicode.feature.gradle.model.ExportOptions
import com.aicode.feature.gradle.model.ExportResult
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

class DependencyExportService(private val resolver: GradleDependencyResolver = GradleDependencyResolver()) {
    fun export(project: Project, linkedProjectPath: String, options: ExportOptions, callback: (ExportResult?, String?) -> Unit) {
        val started = System.currentTimeMillis()
        LOG.info("Starting Gradle dependency export for $linkedProjectPath to ${options.repository}")
        resolver.resolveAndExport(project, linkedProjectPath, options) { result, error ->
            if (result != null) {
                LOG.info("Gradle dependency export finished: dependencies=${result.totalDependencies}, exported=${result.exportedFiles}, skipped=${result.skippedFiles}, failed=${result.failedDependencies.size}, cancelled=${result.cancelled}, durationMs=${result.durationMillis}")
            } else {
                LOG.warn("Gradle dependency export did not complete after ${System.currentTimeMillis() - started} ms: $error")
            }
            callback(result, error)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(DependencyExportService::class.java)
    }
}
