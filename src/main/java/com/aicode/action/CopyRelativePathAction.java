package com.aicode.action;

import com.aicode.service.AICodeFileService;
import com.aicode.util.ClipboardService;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Copies paths relative to the project root from Project View or an editor tab.
 */
public class CopyRelativePathAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (project == null || files == null || files.length == 0) {
            return;
        }

        List<String> relativePaths = getRelativePaths(project, files);
        if (relativePaths.size() != files.length) {
            showNotification(project, "Only files inside the project root can be copied", NotificationType.WARNING);
            return;
        }

        try {
            ClipboardService.copyToClipboard(String.join("\n", relativePaths));
            String message = relativePaths.size() == 1
                    ? "Copied relative path: " + relativePaths.get(0)
                    : String.format("Copied %d relative paths", relativePaths.size());
            showNotification(project, message, NotificationType.INFORMATION);
        } catch (Exception ex) {
            showNotification(project, "Failed to copy relative path: " + ex.getMessage(), NotificationType.ERROR);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        boolean available = project != null
                && files != null
                && files.length > 0
                && getRelativePaths(project, files).size() == files.length;
        e.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static @NotNull List<String> getRelativePaths(
            @NotNull Project project,
            @NotNull VirtualFile[] files
    ) {
        AICodeFileService service = AICodeFileService.getInstance(project);
        List<String> relativePaths = new ArrayList<>(files.length);
        for (VirtualFile file : files) {
            String relativePath = service.getRelativePath(file);
            if (relativePath == null || relativePath.isEmpty()) {
                break;
            }
            relativePaths.add(relativePath);
        }
        return relativePaths;
    }

    private static void showNotification(
            @NotNull Project project,
            @NotNull String content,
            @NotNull NotificationType type
    ) {
        Notification notification = new Notification(
                "AICode",
                "AICode Context Manager",
                content,
                type
        );
        Notifications.Bus.notify(notification, project);
    }
}
