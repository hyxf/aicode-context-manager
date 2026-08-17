package com.aicode.feature.gradle.action

import com.aicode.feature.gradle.model.ExportOptions
import com.aicode.feature.gradle.model.ExportResult
import com.aicode.feature.gradle.service.DependencyExportService
import com.aicode.feature.gradle.ui.ExportGradleDependenciesDialog
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.nio.file.Path

class ExportGradleDependenciesAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val linked = linkedProjects(project)
        if (linked.isEmpty()) {
            notify(project, "No linked Gradle project was detected. Import or link the Gradle project first.", NotificationType.WARNING)
            return
        }
        val selectedProject = chooseLinkedProject(project, linked) ?: return
        val dialog = ExportGradleDependenciesDialog(project)
        if (!dialog.showAndGet()) return
        val directory = chooseDirectory(project, Path.of(selectedProject.externalProjectPath)) ?: return
        val options = ExportOptions(
            directory,
            dialog.selectedScope(),
            dialog.includeTests(),
            dialog.includeBuildscriptClasspath(),
        )
        DependencyExportService().export(project, selectedProject.externalProjectPath, options) { result, error ->
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) showResult(project, directory, result, error)
            }
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val hasGradleProject = project != null && linkedProjects(project).isNotEmpty()
        event.presentation.isVisible = project != null
        event.presentation.isEnabled = hasGradleProject
        event.presentation.description = if (hasGradleProject) {
            "Export resolved Gradle dependencies as a local Maven repository"
        } else "No linked Gradle project was detected"
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private fun chooseDirectory(project: Project, root: Path): Path? {
        val suggested = root.resolve(".gradle/local-maven-repository")
        val initialPath = if (suggested.toFile().exists()) suggested else root.resolve(".gradle")
        val initial = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(initialPath)
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
            title = "Select Local Maven Repository Directory"
            description = "Resolved dependencies will be written using Maven repository layout"
        }
        return FileChooser.chooseFile(descriptor, project, initial)?.toNioPath()
    }

    private fun chooseLinkedProject(project: Project, projects: List<GradleProjectSettings>): GradleProjectSettings? {
        if (projects.size == 1) return projects.single()
        val paths = projects.map { it.externalProjectPath }.toTypedArray()
        val selected = com.intellij.openapi.ui.Messages.showChooseDialog(
            project,
            "Select the linked Gradle build to export.",
            "Select Gradle Project",
            null,
            paths,
            paths.first(),
        )
        if (selected < 0) return null
        return projects[selected]
    }

    companion object {
        private fun linkedProjects(project: Project): List<GradleProjectSettings> =
            GradleSettings.getInstance(project).linkedProjectsSettings.sortedBy { it.externalProjectPath }

        private fun showResult(project: Project, repository: Path, result: ExportResult?, error: String?) {
            if (result == null) {
                notify(project, error ?: "Gradle dependency export failed.", NotificationType.ERROR, true)
                return
            }
            val status = if (result.cancelled) "cancelled" else "completed"
            val seconds = "%.1f".format(result.durationMillis / 1000.0)
            val content = buildString {
                append("Gradle dependency export $status.<br>")
                append("Repository: ${repository.toAbsolutePath()}<br>")
                append("Dependencies: ${result.totalDependencies}<br>")
                append("Exported files: ${result.exportedFiles}<br>")
                append("Skipped files: ${result.skippedFiles}<br>")
                append("Failed: ${result.failedDependencies.size}<br>")
                append("Warnings: ${result.warnings.size}<br>")
                append("Duration: ${seconds}s")
            }
            notify(project, content, if (result.failedDependencies.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING, result.failedDependencies.isNotEmpty() || result.warnings.isNotEmpty())
        }

        private fun notify(project: Project, content: String, type: NotificationType, openLog: Boolean = false) {
            val notification = NotificationGroupManager.getInstance().getNotificationGroup("AICode")
                .createNotification("Gradle Dependency Export", content, type)
            if (openLog) notification.addAction(NotificationAction.createSimple("Open Log") {
                com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Build")?.show()
            })
            notification.notify(project)
        }
    }
}
