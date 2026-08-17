package com.aicode.feature.gradle.service

import com.aicode.feature.gradle.model.ExportOptions
import com.aicode.feature.gradle.model.ExportResult
import com.google.gson.Gson
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Files
import java.nio.file.Path

class GradleDependencyResolver(private val gson: Gson = Gson()) {
    fun resolveAndExport(
        project: Project,
        linkedProjectPath: String,
        options: ExportOptions,
        callback: (ExportResult?, String?) -> Unit,
    ) {
        var temporaryDirectory: Path? = null
        try {
            val createdDirectory = Files.createTempDirectory("aicode-gradle-export-")
            temporaryDirectory = createdDirectory
            val initScript = createdDirectory.resolve("export.init.gradle")
            val resultFile = createdDirectory.resolve("result.json")
            Files.writeString(initScript, GradleExportInitScript.create(options, resultFile))
            val settings = ExternalSystemTaskExecutionSettings().apply {
                executionName = "Exporting Gradle Dependencies"
                externalSystemIdString = GradleConstants.SYSTEM_ID.id
                externalProjectPath = linkedProjectPath
                taskNames = listOf(GradleExportInitScript.TASK_NAME)
                scriptParameters = ParametersListUtil.join(listOf("--init-script", initScript.toString()))
            }
            ExternalSystemUtil.runTask(
                settings,
                DefaultRunExecutor.EXECUTOR_ID,
                project,
                GradleConstants.SYSTEM_ID,
                object : TaskCallback {
                    override fun onSuccess() = finish(resultFile, createdDirectory, callback)

                    override fun onFailure() {
                        cleanup(createdDirectory)
                        callback(
                            null,
                            "Gradle dependency export failed or was cancelled. Open the Gradle Build tool window for details.",
                        )
                    }
                },
                ProgressExecutionMode.IN_BACKGROUND_ASYNC,
            )
        } catch (ex: Exception) {
            temporaryDirectory?.let(::cleanup)
            callback(null, "Could not start the Gradle dependency export: ${ex.message ?: ex.javaClass.simpleName}")
        }
    }

    private fun finish(
        resultFile: Path,
        temporaryDirectory: Path,
        callback: (ExportResult?, String?) -> Unit,
    ) {
        try {
            if (Files.isRegularFile(resultFile)) {
                Files.newBufferedReader(resultFile).use {
                    val result = gson.fromJson(it, ExportResult::class.java)
                    callback(result, null)
                }
            } else callback(null, "Gradle did not produce an export result")
        } catch (ex: Exception) {
            callback(null, "Could not read the Gradle export result: ${ex.message ?: ex.javaClass.simpleName}")
        } finally {
            cleanup(temporaryDirectory)
        }
    }

    private fun cleanup(temporaryDirectory: Path) {
        runCatching { Files.deleteIfExists(temporaryDirectory.resolve("result.json")) }
        runCatching { Files.deleteIfExists(temporaryDirectory.resolve("export.init.gradle")) }
        runCatching { Files.deleteIfExists(temporaryDirectory) }
    }
}
