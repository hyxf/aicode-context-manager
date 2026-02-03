package com.aicode.action;

import com.aicode.service.AICodeFileService;
import com.aicode.util.ClipboardService;
import com.aicode.util.MarkdownBuilder;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Action to copy AICode context as Markdown to clipboard
 */
public class CopyMarkdownAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        AICodeFileService service = AICodeFileService.getInstance(project);
        List<String> filePaths = service.readFilePaths();

        if (filePaths.isEmpty()) {
            showNotification(project, "No files in AICode context", NotificationType.WARNING);
            return;
        }

        try {
            String markdown = MarkdownBuilder.buildMarkdown(
                    project,
                    filePaths,
                    service::getFileFromPath
            );

            ClipboardService.copyToClipboard(markdown);

            String message = String.format("AICode Markdown copied to clipboard (%d files)", filePaths.size());
            showNotification(project, message, NotificationType.INFORMATION);

        } catch (Exception ex) {
            showNotification(project, "Failed to export Markdown: " + ex.getMessage(), NotificationType.ERROR);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        boolean visible = false;
        if (project != null && file != null && !file.isDirectory()) {
            // Only show for .aicode.json file
            visible = ".aicode.json".equals(file.getName());
        }

        e.getPresentation().setEnabledAndVisible(visible);
    }

    private void showNotification(@NotNull Project project, @NotNull String content, @NotNull NotificationType type) {
        Notification notification = new Notification(
                "AICode",
                "AICode Context Manager",
                content,
                type
        );
        Notifications.Bus.notify(notification, project);
    }
}
