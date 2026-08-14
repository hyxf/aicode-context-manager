package com.aicode.feature.git.service

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VfsUtilCore
import git4idea.GitTag
import git4idea.branch.GitBranchUtil
import git4idea.commands.Git
import git4idea.push.GitPushParamsImpl
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import java.util.Collections

class GitTagService {
    private val git = Git.getInstance()

    @Throws(VcsException::class)
    fun getLocalTags(project: Project, repository: GitRepository): List<String> =
        GitBranchUtil.getAllTags(project, repository.root)

    @Throws(VcsException::class)
    fun resolveHead(repository: GitRepository): String =
        git.resolveReference(repository, "HEAD")?.asString()
            ?: throw VcsException("The repository has no resolvable HEAD commit")

    fun publishTag(
        project: Project,
        repository: GitRepository,
        remote: GitRemote,
        tagName: String,
        reference: String,
    ): PublishResult {
        if (project.isDisposed || !repository.root.isValid)
            return PublishResult.failure(
                PublishStatus.CHECK_FAILED,
                "The Git repository is no longer available.",
            )
        val currentRemote: GitRemote
        try {
            repository.update()
            currentRemote =
                repository.remotes.firstOrNull { it.name == remote.name }
                    ?: return PublishResult.failure(
                        PublishStatus.CHECK_FAILED,
                        "Remote ${remote.name} is no longer available for push.",
                    )
            if (currentRemote.pushUrls.isEmpty())
                return PublishResult.failure(
                    PublishStatus.CHECK_FAILED,
                    "Remote ${remote.name} is no longer available for push.",
                )
            if (getLocalTags(project, repository).contains(tagName))
                return PublishResult.failure(
                    PublishStatus.LOCAL_TAG_EXISTS,
                    "Tag $tagName already exists locally.",
                )
            if (remoteTagExists(project, repository, currentRemote, tagName))
                return PublishResult.failure(
                    PublishStatus.REMOTE_TAG_EXISTS,
                    "Tag $tagName already exists on remote ${currentRemote.name}.",
                )
        } catch (ex: ProcessCanceledException) {
            throw ex
        } catch (ex: Exception) {
            return PublishResult.failure(PublishStatus.CHECK_FAILED, safeMessage(ex))
        }

        val createResult =
            try {
                git.createNewTag(repository, tagName, null, reference)
            } catch (ex: ProcessCanceledException) {
                throw ex
            } catch (ex: RuntimeException) {
                return PublishResult.failure(PublishStatus.CREATE_FAILED, safeMessage(ex))
            }
        if (!createResult.success())
            return PublishResult.failure(
                PublishStatus.CREATE_FAILED,
                createResult.errorOutputAsJoinedString,
            )
        refreshTags(repository)

        val fullRef = GitTag.REFS_TAGS_PREFIX + tagName
        val params =
            GitPushParamsImpl(
                currentRemote,
                "$fullRef:$fullRef",
                false,
                false,
                false,
                null,
                Collections.emptyList(),
            )
        val pushResult =
            try {
                git.push(repository, params)
            } catch (ex: ProcessCanceledException) {
                throw ex
            } catch (ex: RuntimeException) {
                return PublishResult.failure(PublishStatus.PUSH_FAILED, safeMessage(ex))
            }
        if (!pushResult.success()) {
            var message = pushResult.errorOutputAsJoinedString
            if (pushResult.isAuthenticationFailed) message = "Authentication failed. $message"
            return PublishResult.failure(PublishStatus.PUSH_FAILED, message)
        }
        return PublishResult.success()
    }

    @Throws(VcsException::class)
    private fun remoteTagExists(
        project: Project,
        repository: GitRepository,
        remote: GitRemote,
        tagName: String,
    ): Boolean {
        val fullRef = GitTag.REFS_TAGS_PREFIX + tagName
        for (url in remote.pushUrls) {
            val result = git.lsRemote(project, VfsUtilCore.virtualToIoFile(repository.root), url)
            result.throwOnError()
            if (result.output.any { hasRef(it, fullRef) }) return true
        }
        return false
    }

    enum class PublishStatus {
        SUCCESS,
        LOCAL_TAG_EXISTS,
        REMOTE_TAG_EXISTS,
        CHECK_FAILED,
        CREATE_FAILED,
        PUSH_FAILED,
    }

    data class PublishResult(val status: PublishStatus, val message: String) {
        fun status() = status

        fun message() = message

        companion object {
            @JvmStatic fun success() = PublishResult(PublishStatus.SUCCESS, "")

            @JvmStatic
            fun failure(status: PublishStatus, message: String) =
                PublishResult(status, message.ifBlank { "Unexpected Git error" })
        }
    }

    companion object {
        private val LOG = Logger.getInstance(GitTagService::class.java)

        private fun refreshTags(repository: GitRepository) {
            try {
                repository.repositoryFiles.refreshTagsFiles()
            } catch (ex: RuntimeException) {
                LOG.warn("Failed to refresh Git tag files for ${repository.root.path}", ex)
            }
        }

        @JvmStatic
        fun hasRef(line: String, expectedRef: String): Boolean {
            val separator = line.lastIndexOf('\t')
            return separator >= 0 && line.substring(separator + 1) == expectedRef
        }

        private fun safeMessage(exception: Exception) =
            exception.message?.takeUnless { it.isBlank() } ?: "Unexpected Git error"
    }
}
