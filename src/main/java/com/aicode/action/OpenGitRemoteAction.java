package com.aicode.action;

import com.aicode.util.GitRemoteUrlResolver;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Opens the project's origin repository, optionally at the current branch or selected directory. */
public class OpenGitRemoteAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(OpenGitRemoteAction.class);

    public enum Target {
        REPOSITORY,
        BRANCH,
        DIRECTORY
    }

    private final Target target;

    public OpenGitRemoteAction() {
        this(Target.REPOSITORY);
    }

    protected OpenGitRemoteAction(@NotNull Target target) {
        this.target = target;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        try {
            VirtualFile selected = e.getData(CommonDataKeys.VIRTUAL_FILE);
            GitRepository repository = GitRemoteUrlResolver.findRepository(project, selected);
            if (repository == null) {
                notify(project, "The project is not inside a Git repository", NotificationType.WARNING);
                return;
            }
            String origin = GitRemoteUrlResolver.getOriginUrl(repository);
            if (origin == null) {
                notify(project, "No origin remote was found for this Git repository", NotificationType.WARNING);
                return;
            }

            String branch = target == Target.REPOSITORY ? null : GitRemoteUrlResolver.getCurrentBranch(repository);
            if (target != Target.REPOSITORY && branch == null) {
                notify(project, "HEAD is detached; opening the repository home page", NotificationType.WARNING);
            }
            String relativePath = target == Target.DIRECTORY && branch != null
                    ? selectedDirectory(repository, selected)
                    : null;
            if (target == Target.DIRECTORY && branch != null && relativePath == null) {
                notify(project, "The selected file is not inside the target Git repository", NotificationType.WARNING);
                return;
            }
            String webUrl = GitRemoteUrlResolver.toWebUrl(origin, branch, relativePath);
            if (webUrl == null) {
                notify(project, "The origin remote URL format is not supported", NotificationType.ERROR);
                return;
            }
            BrowserUtil.browse(webUrl);
        } catch (RuntimeException ex) {
            LOG.warn("Failed to resolve the Git remote web URL", ex);
            notify(project, "Failed to open the Git remote: " + safeMessage(ex), NotificationType.ERROR);
        }
    }

    private static @Nullable String selectedDirectory(
            @NotNull GitRepository repository,
            @Nullable VirtualFile selected
    ) {
        if (selected == null) {
            return null;
        }
        VirtualFile directory = selected.isDirectory() ? selected : selected.getParent();
        if (directory == null) {
            return null;
        }
        String relative = com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(
                directory,
                repository.getRoot(),
                '/'
        );
        return relative;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile selected = e.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean available = false;
        if (project != null) {
            GitRepository repository = GitRemoteUrlResolver.findRepository(project, selected);
            available = repository != null && GitRemoteUrlResolver.getOriginUrl(repository) != null;
            if (available && target != Target.REPOSITORY) {
                available = GitRemoteUrlResolver.getCurrentBranch(repository) != null;
            }
            if (available && target == Target.DIRECTORY) {
                available = selectedDirectory(repository, selected) != null;
            }
        }
        e.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static @NotNull String safeMessage(@NotNull Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "unexpected error"
                : exception.getMessage();
    }

    private static void notify(
            @NotNull Project project,
            @NotNull String message,
            @NotNull NotificationType type
    ) {
        Notifications.Bus.notify(new Notification(
                "AICode",
                "AICode Context Manager",
                message,
                type
        ), project);
    }
}
