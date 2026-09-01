package com.aicode.feature.git.action

import com.aicode.feature.git.ui.CommonCommitMessageDialog
import com.aicode.feature.git.service.CommonCommitMessageService
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task

class SelectCommonCommitMessageAction : AnAction(
    "Common Commit Messages...",
    "Select a predefined commit message",
    AllIcons.Actions.Search,
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dataContext = e.dataContext
        val commitMessageControl = VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(dataContext) ?: return
        object : Task.Backgroundable(project, "Loading common commit messages", false) {
            private var messages: List<String> = emptyList()
            private var error: String? = null

            override fun run(indicator: ProgressIndicator) {
                try {
                    messages = CommonCommitMessageService.getInstance().getMessages()
                } catch (ex: IllegalStateException) {
                    error = ex.message ?: "Failed to load common commit messages."
                }
            }

            override fun onSuccess() {
                if (project.isDisposed) return
                error?.let {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("AICode")
                        .createNotification(it, NotificationType.ERROR)
                        .notify(project)
                    return
                }
                if (messages.isEmpty()) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("AICode")
                        .createNotification(
                            "Configure messages in Settings | Tools | Common Commit Messages.",
                            NotificationType.INFORMATION,
                        )
                        .notify(project)
                    return
                }
                val dialog = CommonCommitMessageDialog(project, messages)
                if (dialog.showAndGet()) {
                    dialog.selectedMessage?.let(commitMessageControl::setCommitMessage)
                }
            }
        }.queue()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null && VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(e.dataContext) != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}
