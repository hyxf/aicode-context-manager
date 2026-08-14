package com.aicode.feature.git.action

import com.aicode.feature.git.service.GlobalGitIgnoreService
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class OpenGlobalGitIgnoreAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        object : Task.Backgroundable(project, "Locating Git Global Ignore", false) {
            private var path: Path? = null
            private var error: String? = null

            override fun run(indicator: ProgressIndicator) {
                try {
                    path = GlobalGitIgnoreService().locate(project)
                } catch (exception: VcsException) {
                    LOG.warn("Unable to locate the Git global ignore file", exception)
                    error = exception.message
                } catch (exception: RuntimeException) {
                    LOG.warn("Unable to locate the Git global ignore file", exception)
                    error = exception.message ?: "Unexpected Git configuration error."
                }
            }

            override fun onSuccess() {
                if (project.isDisposed) return
                error?.let {
                    notifyError(project, "Unable to open Git global ignore file: $it")
                    return
                }
                openOrCreate(project, path ?: return)
            }
        }.queue()
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    companion object {
        private val LOG = Logger.getInstance(OpenGlobalGitIgnoreAction::class.java)

        private fun openOrCreate(project: Project, path: Path) {
            if (Files.exists(path)) {
                if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                    notifyError(project, "Unable to open Git global ignore file: the file is not readable: $path")
                    return
                }
                if (!Files.isWritable(path)) {
                    notifyError(project, "Unable to edit Git global ignore file: the file is not writable: $path")
                    return
                }
                open(project, path)
                return
            }

            val answer = Messages.showYesNoDialog(
                project,
                "Global Git Ignore file does not exist.\n\n$path\n\nCreate it now?",
                "Open Global Git Ignore",
                Messages.getQuestionIcon(),
            )
            if (answer != Messages.YES) return

            try {
                val file = create(project, path)
                FileEditorManager.getInstance(project).openFile(file, true)
            } catch (exception: IOException) {
                LOG.warn("Unable to create the Git global ignore file", exception)
                notifyError(project, "Unable to create Git global ignore file: ${safeMessage(exception)}")
            } catch (exception: RuntimeException) {
                LOG.warn("Unable to create the Git global ignore file", exception)
                notifyError(project, "Unable to create Git global ignore file: ${safeMessage(exception)}")
            }
        }

        @Throws(IOException::class)
        private fun create(project: Project, path: Path): VirtualFile = WriteAction.compute<VirtualFile, IOException> {
            val parentPath = path.parent ?: throw IOException("The target has no parent directory: $path")
            val parent = VfsUtil.createDirectories(parentPath.toString())
            parent.findChild(path.fileName.toString()) ?: parent.createChildData(project, path.fileName.toString())
        }

        private fun open(project: Project, path: Path) {
            val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
            if (file == null) {
                notifyError(project, "Unable to open Git global ignore file: IDEA could not locate $path")
                return
            }
            FileEditorManager.getInstance(project).openFile(file, true)
        }

        private fun notifyError(project: Project, message: String) {
            Notifications.Bus.notify(
                Notification("AICode", "Open Global Git Ignore", message, NotificationType.ERROR),
                project,
            )
        }

        private fun safeMessage(exception: Exception): String =
            exception.message?.takeIf { it.isNotBlank() } ?: exception.javaClass.simpleName
    }
}
