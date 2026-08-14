package com.aicode.feature.git.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.vcs.log.Hash;
import git4idea.GitTag;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.push.GitPushParamsImpl;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/** Executes release tag operations exclusively through the bundled Git4Idea API. */
public final class GitTagService {
    private static final Logger LOG = Logger.getInstance(GitTagService.class);
    private final Git git;

    public GitTagService() {
        git = Git.getInstance();
    }

    public @NotNull List<String> getLocalTags(@NotNull Project project, @NotNull GitRepository repository)
            throws VcsException {
        return GitBranchUtil.getAllTags(project, repository.getRoot());
    }

    public @NotNull String resolveHead(@NotNull GitRepository repository) throws VcsException {
        Hash hash = git.resolveReference(repository, "HEAD");
        if (hash == null) {
            throw new VcsException("The repository has no resolvable HEAD commit");
        }
        return hash.asString();
    }

    public @NotNull PublishResult publishTag(
            @NotNull Project project,
            @NotNull GitRepository repository,
            @NotNull GitRemote remote,
            @NotNull String tagName,
            @NotNull String reference
    ) {
        if (project.isDisposed() || !repository.getRoot().isValid()) {
            return PublishResult.failure(PublishStatus.CHECK_FAILED, "The Git repository is no longer available.");
        }
        GitRemote currentRemote;
        try {
            repository.update();
            currentRemote = repository.getRemotes().stream()
                    .filter(candidate -> candidate.getName().equals(remote.getName()))
                    .findFirst()
                    .orElse(null);
            if (currentRemote == null || currentRemote.getPushUrls().isEmpty()) {
                return PublishResult.failure(PublishStatus.CHECK_FAILED,
                        "Remote " + remote.getName() + " is no longer available for push.");
            }
            if (getLocalTags(project, repository).contains(tagName)) {
                return PublishResult.failure(PublishStatus.LOCAL_TAG_EXISTS,
                        "Tag " + tagName + " already exists locally.");
            }
            if (remoteTagExists(project, repository, currentRemote, tagName)) {
                return PublishResult.failure(PublishStatus.REMOTE_TAG_EXISTS,
                        "Tag " + tagName + " already exists on remote " + currentRemote.getName() + ".");
            }
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (VcsException | RuntimeException exception) {
            return PublishResult.failure(PublishStatus.CHECK_FAILED, safeMessage(exception));
        }

        GitCommandResult createResult;
        try {
            createResult = git.createNewTag(repository, tagName, null, reference);
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return PublishResult.failure(PublishStatus.CREATE_FAILED, safeMessage(exception));
        }
        if (!createResult.success()) {
            return PublishResult.failure(PublishStatus.CREATE_FAILED, createResult.getErrorOutputAsJoinedString());
        }
        refreshTags(repository);

        String fullRef = GitTag.REFS_TAGS_PREFIX + tagName;
        GitPushParamsImpl pushParams = new GitPushParamsImpl(
                currentRemote,
                fullRef + ":" + fullRef,
                false,
                false,
                false,
                null,
                Collections.emptyList()
        );
        GitCommandResult pushResult;
        try {
            pushResult = git.push(repository, pushParams);
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return PublishResult.failure(PublishStatus.PUSH_FAILED, safeMessage(exception));
        }
        if (!pushResult.success()) {
            String message = pushResult.getErrorOutputAsJoinedString();
            if (pushResult.isAuthenticationFailed()) {
                message = "Authentication failed. " + message;
            }
            return PublishResult.failure(PublishStatus.PUSH_FAILED, message);
        }
        return PublishResult.success();
    }

    private static void refreshTags(@NotNull GitRepository repository) {
        try {
            repository.getRepositoryFiles().refreshTagsFiles();
        } catch (RuntimeException exception) {
            LOG.warn("Failed to refresh Git tag files for " + repository.getRoot().getPath(), exception);
        }
    }

    private boolean remoteTagExists(
            @NotNull Project project,
            @NotNull GitRepository repository,
            @NotNull GitRemote remote,
            @NotNull String tagName
    ) throws VcsException {
        String fullRef = GitTag.REFS_TAGS_PREFIX + tagName;
        for (String pushUrl : remote.getPushUrls()) {
            GitCommandResult result = git.lsRemote(
                    project,
                    VfsUtilCore.virtualToIoFile(repository.getRoot()),
                    pushUrl
            );
            result.throwOnError();
            if (result.getOutput().stream().anyMatch(line -> hasRef(line, fullRef))) {
                return true;
            }
        }
        return false;
    }

    static boolean hasRef(@NotNull String line, @NotNull String expectedRef) {
        int separator = line.lastIndexOf('\t');
        return separator >= 0 && line.substring(separator + 1).equals(expectedRef);
    }

    private static @NotNull String safeMessage(@NotNull Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Unexpected Git error" : message;
    }

    public enum PublishStatus {
        SUCCESS,
        LOCAL_TAG_EXISTS,
        REMOTE_TAG_EXISTS,
        CHECK_FAILED,
        CREATE_FAILED,
        PUSH_FAILED
    }

    public record PublishResult(@NotNull PublishStatus status, @NotNull String message) {
        public static @NotNull PublishResult success() {
            return new PublishResult(PublishStatus.SUCCESS, "");
        }

        public static @NotNull PublishResult failure(
                @NotNull PublishStatus status,
                @NotNull String message
        ) {
            return new PublishResult(status, message.isBlank() ? "Unexpected Git error" : message);
        }
    }
}
