package com.aicode.action;

import com.aicode.util.GitRemoteUrlResolver;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PropertiesComponent;
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Opens the project's origin repository, current branch, or a selected path. */
public class OpenGitRemoteAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(OpenGitRemoteAction.class);
    private static final String HOSTING_PLATFORM_PROPERTY_PREFIX = "aicode.gitRemote.hostingPlatform.";

    public enum Target {
        REPOSITORY,
        BRANCH,
        DIRECTORY,
        FILE
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
            String relativePath = branch == null ? null : switch (target) {
                case DIRECTORY -> selectedDirectory(repository, selected);
                case FILE -> selectedFile(repository, selected);
                default -> null;
            };
            if ((target == Target.DIRECTORY || target == Target.FILE)
                    && branch != null && relativePath == null) {
                String selectedPathType = target == Target.DIRECTORY ? "directory" : "file";
                notify(project,
                        "The selected " + selectedPathType + " is not inside the target Git repository",
                        NotificationType.WARNING);
                return;
            }
            GitRemoteUrlResolver.HostingPlatform platform = branch == null
                    ? GitRemoteUrlResolver.HostingPlatform.UNKNOWN
                    : resolveHostingPlatform(project, origin);
            if (branch != null && platform == null) {
                return;
            }
            GitRemoteUrlResolver.PathType pathType = target == Target.FILE
                    ? GitRemoteUrlResolver.PathType.FILE
                    : GitRemoteUrlResolver.PathType.DIRECTORY;
            String webUrl = GitRemoteUrlResolver.toWebUrl(origin, branch, relativePath, platform, pathType);
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

    private static @Nullable GitRemoteUrlResolver.HostingPlatform resolveHostingPlatform(
            @NotNull Project project,
            @NotNull String origin
    ) {
        GitRemoteUrlResolver.HostingPlatform detected = GitRemoteUrlResolver.detectHostingPlatform(origin);
        if (detected != GitRemoteUrlResolver.HostingPlatform.UNKNOWN) {
            return detected;
        }
        String host = GitRemoteUrlResolver.getHost(origin);
        if (host == null || host.isBlank()) {
            return GitRemoteUrlResolver.HostingPlatform.UNKNOWN;
        }

        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        String propertyKey = HOSTING_PLATFORM_PROPERTY_PREFIX + host.toLowerCase(java.util.Locale.ROOT);
        String saved = properties.getValue(propertyKey);
        if (saved != null) {
            try {
                return GitRemoteUrlResolver.HostingPlatform.valueOf(saved);
            } catch (IllegalArgumentException ex) {
                properties.unsetValue(propertyKey);
            }
        }

        String[] options = {
                "GitLab (/-/tree/)",
                "GitHub / Gitee / Codeup (/tree/)",
                "Bitbucket (/src/)",
                Messages.getCancelButton()
        };
        int choice = Messages.showDialog(
                project,
                "Select the hosting platform used by " + host + ". The choice will be remembered for this project.",
                "Select Git Hosting Platform",
                options,
                0,
                Messages.getQuestionIcon()
        );
        GitRemoteUrlResolver.HostingPlatform selected = switch (choice) {
            case 0 -> GitRemoteUrlResolver.HostingPlatform.GITLAB;
            case 1 -> GitRemoteUrlResolver.HostingPlatform.GITHUB;
            case 2 -> GitRemoteUrlResolver.HostingPlatform.BITBUCKET;
            default -> null;
        };
        if (selected != null) {
            properties.setValue(propertyKey, selected.name());
        }
        return selected;
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

    private static @Nullable String selectedFile(
            @NotNull GitRepository repository,
            @Nullable VirtualFile selected
    ) {
        if (selected == null || selected.isDirectory()) {
            return null;
        }
        return com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(
                selected,
                repository.getRoot(),
                '/'
        );
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
            if (available && target == Target.FILE) {
                available = selectedFile(repository, selected) != null;
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
