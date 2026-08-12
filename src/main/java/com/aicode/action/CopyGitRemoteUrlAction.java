package com.aicode.action;

import com.aicode.util.ClipboardService;
import com.aicode.util.GitRemoteUrlResolver;
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

/** Copies the current Git repository's origin remote URL. */
public final class CopyGitRemoteUrlAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(CopyGitRemoteUrlAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        try {
            String remoteUrl = getRemoteUrl(project, e.getData(CommonDataKeys.VIRTUAL_FILE));
            if (remoteUrl == null) {
                notify(project, "No origin remote was found for this Git repository", NotificationType.WARNING);
                return;
            }
            ClipboardService.copyToClipboard(remoteUrl);
            notify(project, "Copied Git remote URL: " + remoteUrl, NotificationType.INFORMATION);
        } catch (RuntimeException ex) {
            LOG.warn("Failed to copy the Git remote URL", ex);
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "unexpected error"
                    : ex.getMessage();
            notify(project, "Failed to copy the Git remote URL: " + message, NotificationType.ERROR);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean available = project != null
                && getRemoteUrl(project, e.getData(CommonDataKeys.VIRTUAL_FILE)) != null;
        e.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static @Nullable String getRemoteUrl(
            @NotNull Project project,
            @Nullable VirtualFile selected
    ) {
        GitRepository repository = GitRemoteUrlResolver.findRepository(project, selected);
        return repository == null ? null : GitRemoteUrlResolver.getOriginUrl(repository);
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
