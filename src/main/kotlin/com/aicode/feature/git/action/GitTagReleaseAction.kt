package com.aicode.feature.git.action

import com.aicode.feature.git.service.GitTagService
import com.aicode.feature.git.service.GitTagService.*
import com.aicode.feature.git.service.GitTagVersionService
import com.aicode.feature.git.service.GitTagVersionService.VersionCandidates
import com.aicode.feature.git.ui.GitTagVersionDialog
import com.aicode.feature.git.util.GitRemoteUrlResolver
import com.intellij.ide.BrowserUtil
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.*

class GitTagReleaseAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val repository =
            chooseRepository(project, event.getData(CommonDataKeys.VIRTUAL_FILE)) ?: return
        val remote = chooseRemote(project, repository) ?: return
        loadVersions(project, repository, remote)
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val has =
            project != null && GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
        event.presentation.isVisible = project != null
        event.presentation.isEnabled = has
        event.presentation.description =
            if (has) "Create and push the next semantic version Git tag"
            else "No Git repository was detected for this project"
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    companion object {
        private val LOG = Logger.getInstance(GitTagReleaseAction::class.java)

        private fun loadVersions(project: Project, repository: GitRepository, remote: GitRemote) {
            object : Task.Backgroundable(project, "Reading Git Tags", true) {
                    private var candidates: VersionCandidates? = null
                    private var error: String? = null

                    override fun run(indicator: ProgressIndicator) {
                        try {
                            candidates =
                                GitTagVersionService.calculateCandidates(
                                    GitTagService().getLocalTags(project, repository)
                                )
                        } catch (ex: ProcessCanceledException) {
                            throw ex
                        } catch (ex: VcsException) {
                            failed(ex)
                        } catch (ex: RuntimeException) {
                            failed(ex)
                        }
                    }

                    private fun failed(ex: Exception) {
                        LOG.warn("Failed to calculate the next Git tag", ex)
                        error = safeMessage(ex)
                    }

                    override fun onSuccess() {
                        if (project.isDisposed) return
                        error?.let {
                            notify(project, "Failed to read Git tags: $it", NotificationType.ERROR)
                            return
                        }
                        showVersionDialog(project, repository, remote, candidates!!)
                    }
                }
                .queue()
        }

        private fun showVersionDialog(
            project: Project,
            repository: GitRepository,
            remote: GitRemote,
            candidates: VersionCandidates,
        ) {
            val dialog = GitTagVersionDialog(project, candidates)
            if (dialog.showAndGet())
                resolveReleaseTarget(project, repository, remote, dialog.getSelectedTag())
        }

        private fun resolveReleaseTarget(
            project: Project,
            repository: GitRepository,
            remote: GitRemote,
            tagName: String,
        ) {
            object : Task.Backgroundable(project, "Resolving Git HEAD", true) {
                    private var reference: String? = null
                    private var error: String? = null

                    override fun run(indicator: ProgressIndicator) {
                        try {
                            reference = GitTagService().resolveHead(repository)
                        } catch (ex: ProcessCanceledException) {
                            throw ex
                        } catch (ex: VcsException) {
                            failed(ex)
                        } catch (ex: RuntimeException) {
                            failed(ex)
                        }
                    }

                    private fun failed(ex: Exception) {
                        LOG.warn("Failed to resolve Git HEAD", ex)
                        error = safeMessage(ex)
                    }

                    override fun onSuccess() {
                        if (project.isDisposed) return
                        error?.let {
                            notify(
                                project,
                                "Failed to resolve Git HEAD: $it",
                                NotificationType.ERROR,
                            )
                            return
                        }
                        confirmRelease(project, repository, remote, tagName, reference!!)
                    }
                }
                .queue()
        }

        private fun confirmRelease(
            project: Project,
            repository: GitRepository,
            remote: GitRemote,
            tagName: String,
            reference: String,
        ) {
            val message =
                "The following Git tag will be created at the current HEAD and pushed to ${remote.name}:\n\n$tagName\nCommit: ${abbreviate(reference)}\n\nContinue?"
            if (
                Messages.showYesNoDialog(
                    project,
                    message,
                    "Confirm Git Tag Release",
                    "Confirm Release",
                    Messages.getCancelButton(),
                    Messages.getQuestionIcon(),
                ) == Messages.YES
            )
                publish(project, repository, remote, tagName, reference)
        }

        private fun publish(
            project: Project,
            repository: GitRepository,
            remote: GitRemote,
            tagName: String,
            reference: String,
        ) {
            object : Task.Backgroundable(project, "Publishing Git Tag $tagName", true) {
                    private var result: PublishResult? = null

                    override fun run(indicator: ProgressIndicator) {
                        try {
                            result =
                                GitTagService()
                                    .publishTag(project, repository, remote, tagName, reference)
                        } catch (ex: ProcessCanceledException) {
                            throw ex
                        } catch (ex: RuntimeException) {
                            LOG.warn("Unexpected failure while publishing Git tag $tagName", ex)
                            result =
                                PublishResult.failure(PublishStatus.CHECK_FAILED, safeMessage(ex))
                        }
                    }

                    override fun onSuccess() {
                        if (!project.isDisposed)
                            showPublishResult(project, remote, tagName, result!!)
                    }
                }
                .queue()
        }

        private fun showPublishResult(
            project: Project,
            remote: GitRemote,
            tagName: String,
            result: PublishResult,
        ) {
            when (result.status) {
                PublishStatus.SUCCESS -> {
                    val url = remote.firstUrl?.let { GitRemoteUrlResolver.toWebUrl(it, null, null) }
                    notify(
                        project,
                        "Created and pushed Git tag $tagName to ${remote.name}.",
                        NotificationType.INFORMATION,
                        url,
                    )
                }
                PublishStatus.PUSH_FAILED ->
                    notify(
                        project,
                        "Tag $tagName was created locally, but push did not report success. The remote state may be uncertain: ${result.message}",
                        NotificationType.ERROR,
                    )
                PublishStatus.LOCAL_TAG_EXISTS,
                PublishStatus.REMOTE_TAG_EXISTS ->
                    notify(
                        project,
                        result.message + " Existing tags will not be overwritten.",
                        NotificationType.WARNING,
                    )
                PublishStatus.CHECK_FAILED ->
                    notify(
                        project,
                        "Could not verify whether the tag exists; no tag was created: ${result.message}",
                        NotificationType.ERROR,
                    )
                PublishStatus.CREATE_FAILED ->
                    notify(
                        project,
                        "Failed to create Git tag $tagName: ${result.message}",
                        NotificationType.ERROR,
                    )
            }
        }

        private fun chooseRepository(project: Project, context: VirtualFile?): GitRepository? {
            val manager = GitRepositoryManager.getInstance(project)
            context
                ?.let { manager.getRepositoryForFileQuick(it) }
                ?.let {
                    return it
                }
            val repositories = manager.repositories.sortedBy { it.root.presentableUrl }
            if (repositories.isEmpty()) {
                notify(
                    project,
                    "No Git repository was detected for this project.",
                    NotificationType.WARNING,
                )
                return null
            }
            if (repositories.size == 1) return repositories[0]
            val options = repositories.map { it.root.presentableUrl }.toTypedArray()
            val selected =
                Messages.showChooseDialog(
                    project,
                    "The current context does not identify a single Git repository.",
                    "Select Git Repository",
                    Messages.getQuestionIcon(),
                    options,
                    options[0],
                )
            return if (selected < 0) null else repositories[selected]
        }

        private fun chooseRemote(project: Project, repository: GitRepository): GitRemote? {
            val remotes =
                repository.remotes.filter { it.pushUrls.isNotEmpty() }.sortedBy { it.name }
            if (remotes.isEmpty()) {
                notify(
                    project,
                    "The selected Git repository has no remote with a push URL.",
                    NotificationType.WARNING,
                )
                return null
            }
            remotes
                .firstOrNull { it.name == GitRemote.ORIGIN }
                ?.let {
                    return it
                }
            if (remotes.size == 1) return remotes[0]
            val options = remotes.map { it.name }.toTypedArray()
            val selected =
                Messages.showChooseDialog(
                    project,
                    "Select the remote that will receive the new tag.",
                    "Select Git Remote",
                    Messages.getQuestionIcon(),
                    options,
                    options[0],
                )
            return if (selected < 0) null else remotes[selected]
        }

        private fun safeMessage(ex: Exception) =
            ex.message?.takeUnless { it.isBlank() } ?: "Unexpected error"

        private fun abbreviate(reference: String) =
            if (reference.length <= 12) reference else reference.substring(0, 12)

        private fun notify(
            project: Project,
            message: String,
            type: NotificationType,
            repositoryUrl: String? = null,
        ) {
            if (project.isDisposed) return
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    val notification = Notification("AICode", "Git Tag Release", message, type)
                    if (repositoryUrl != null)
                        notification.addAction(
                            NotificationAction.createSimple("Open Repository") {
                                BrowserUtil.browse(repositoryUrl)
                            }
                        )
                    Notifications.Bus.notify(notification, project)
                }
            }
        }
    }
}
