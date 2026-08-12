package com.aicode.action;

import com.aicode.service.GitTagService;
import com.aicode.service.GitTagService.PublishResult;
import com.aicode.service.GitTagVersionService;
import com.aicode.service.GitTagVersionService.VersionCandidates;
import com.aicode.ui.GitTagVersionDialog;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** Creates a calculated release tag at HEAD and pushes only that tag. */
public final class GitTagReleaseAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(GitTagReleaseAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        GitRepository repository = chooseRepository(
                project,
                event.getData(CommonDataKeys.VIRTUAL_FILE)
        );
        if (repository == null) {
            return;
        }
        GitRemote remote = chooseRemote(project, repository);
        if (remote == null) {
            return;
        }
        loadVersions(project, repository, remote);
    }

    private static void loadVersions(
            @NotNull Project project,
            @NotNull GitRepository repository,
            @NotNull GitRemote remote
    ) {
        new Task.Backgroundable(project, "Reading Git Tags", true) {
            private VersionCandidates candidates;
            private String error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    List<String> tags = new GitTagService().getLocalTags(project, repository);
                    candidates = GitTagVersionService.calculateCandidates(tags);
                } catch (ProcessCanceledException exception) {
                    throw exception;
                } catch (VcsException | RuntimeException exception) {
                    LOG.warn("Failed to calculate the next Git tag", exception);
                    error = safeMessage(exception);
                }
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }
                if (error != null) {
                    GitTagReleaseAction.notify(
                            project,
                            "Failed to read Git tags: " + error,
                            NotificationType.ERROR
                    );
                    return;
                }
                showVersionDialog(project, repository, remote, candidates);
            }
        }.queue();
    }

    private static void showVersionDialog(
            @NotNull Project project,
            @NotNull GitRepository repository,
            @NotNull GitRemote remote,
            @NotNull VersionCandidates candidates
    ) {
        GitTagVersionDialog dialog = new GitTagVersionDialog(project, candidates);
        if (!dialog.showAndGet()) {
            return;
        }
        String tagName = dialog.getSelectedTag();
        resolveReleaseTarget(project, repository, remote, tagName);
    }

    private static void resolveReleaseTarget(
            @NotNull Project project,
            @NotNull GitRepository repository,
            @NotNull GitRemote remote,
            @NotNull String tagName
    ) {
        new Task.Backgroundable(project, "Resolving Git HEAD", true) {
            private String reference;
            private String error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    reference = new GitTagService().resolveHead(repository);
                } catch (ProcessCanceledException exception) {
                    throw exception;
                } catch (VcsException | RuntimeException exception) {
                    LOG.warn("Failed to resolve Git HEAD", exception);
                    error = safeMessage(exception);
                }
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }
                if (error != null) {
                    GitTagReleaseAction.notify(
                            project,
                            "Failed to resolve Git HEAD: " + error,
                            NotificationType.ERROR
                    );
                    return;
                }
                confirmRelease(project, repository, remote, tagName, reference);
            }
        }.queue();
    }

    private static void confirmRelease(
            @NotNull Project project,
            @NotNull GitRepository repository,
            @NotNull GitRemote remote,
            @NotNull String tagName,
            @NotNull String reference
    ) {
        String message = "The following Git tag will be created at the current HEAD and pushed to "
                + remote.getName() + ":\n\n" + tagName
                + "\nCommit: " + abbreviate(reference) + "\n\nContinue?";
        int answer = Messages.showYesNoDialog(
                project,
                message,
                "Confirm Git Tag Release",
                "Confirm Release",
                Messages.getCancelButton(),
                Messages.getQuestionIcon()
        );
        if (answer == Messages.YES) {
            publish(project, repository, remote, tagName, reference);
        }
    }

    private static void publish(
            @NotNull Project project,
            @NotNull GitRepository repository,
            @NotNull GitRemote remote,
            @NotNull String tagName,
            @NotNull String reference
    ) {
        new Task.Backgroundable(project, "Publishing Git Tag " + tagName, true) {
            private PublishResult result;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    result = new GitTagService().publishTag(project, repository, remote, tagName, reference);
                } catch (ProcessCanceledException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    LOG.warn("Unexpected failure while publishing Git tag " + tagName, exception);
                    result = PublishResult.failure(
                            GitTagService.PublishStatus.CHECK_FAILED,
                            safeMessage(exception)
                    );
                }
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }
                showPublishResult(project, remote, tagName, result);
            }
        }.queue();
    }

    private static void showPublishResult(
            @NotNull Project project,
            @NotNull GitRemote remote,
            @NotNull String tagName,
            @NotNull PublishResult result
    ) {
        switch (result.status()) {
            case SUCCESS -> notify(
                    project,
                    "Created and pushed Git tag " + tagName + " to " + remote.getName() + ".",
                    NotificationType.INFORMATION
            );
            case PUSH_FAILED -> notify(
                    project,
                    "Tag " + tagName + " was created locally, but push did not report success. "
                            + "The remote state may be uncertain: " + result.message(),
                    NotificationType.ERROR
            );
            case LOCAL_TAG_EXISTS, REMOTE_TAG_EXISTS -> notify(
                    project,
                    result.message() + " Existing tags will not be overwritten.",
                    NotificationType.WARNING
            );
            case CHECK_FAILED -> notify(
                    project,
                    "Could not verify whether the tag exists; no tag was created: " + result.message(),
                    NotificationType.ERROR
            );
            case CREATE_FAILED -> notify(
                    project,
                    "Failed to create Git tag " + tagName + ": " + result.message(),
                    NotificationType.ERROR
            );
        }
    }

    private static @Nullable GitRepository chooseRepository(
            @NotNull Project project,
            @Nullable VirtualFile contextFile
    ) {
        GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
        if (contextFile != null) {
            GitRepository contextual = manager.getRepositoryForFileQuick(contextFile);
            if (contextual != null) {
                return contextual;
            }
        }

        List<GitRepository> repositories = manager.getRepositories().stream()
                .sorted(Comparator.comparing(repository -> repository.getRoot().getPresentableUrl()))
                .toList();
        if (repositories.isEmpty()) {
            notify(project, "No Git repository was detected for this project.", NotificationType.WARNING);
            return null;
        }
        if (repositories.size() == 1) {
            return repositories.get(0);
        }

        String[] options = repositories.stream()
                .map(repository -> repository.getRoot().getPresentableUrl())
                .toArray(String[]::new);
        int selected = Messages.showChooseDialog(
                project,
                "The current context does not identify a single Git repository.",
                "Select Git Repository",
                Messages.getQuestionIcon(),
                options,
                options[0]
        );
        return selected < 0 ? null : repositories.get(selected);
    }

    private static @Nullable GitRemote chooseRemote(
            @NotNull Project project,
            @NotNull GitRepository repository
    ) {
        List<GitRemote> remotes = repository.getRemotes().stream()
                .filter(remote -> !remote.getPushUrls().isEmpty())
                .sorted(Comparator.comparing(GitRemote::getName))
                .toList();
        if (remotes.isEmpty()) {
            notify(project, "The selected Git repository has no remote with a push URL.", NotificationType.WARNING);
            return null;
        }
        GitRemote origin = remotes.stream()
                .filter(remote -> GitRemote.ORIGIN.equals(remote.getName()))
                .findFirst()
                .orElse(null);
        if (origin != null) {
            return origin;
        }
        if (remotes.size() == 1) {
            return remotes.get(0);
        }

        String[] options = remotes.stream().map(GitRemote::getName).toArray(String[]::new);
        int selected = Messages.showChooseDialog(
                project,
                "Select the remote that will receive the new tag.",
                "Select Git Remote",
                Messages.getQuestionIcon(),
                options,
                options[0]
        );
        return selected < 0 ? null : remotes.get(selected);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        boolean available = project != null
                && !GitRepositoryManager.getInstance(project).getRepositories().isEmpty();
        event.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static @NotNull String safeMessage(@NotNull Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Unexpected error" : message;
    }

    private static @NotNull String abbreviate(@NotNull String reference) {
        return reference.length() <= 12 ? reference : reference.substring(0, 12);
    }

    private static void notify(
            @NotNull Project project,
            @NotNull String message,
            @NotNull NotificationType type
    ) {
        if (project.isDisposed()) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                Notifications.Bus.notify(new Notification(
                        "AICode",
                        "Git Tag Release",
                        message,
                        type
                ), project);
            }
        });
    }
}
