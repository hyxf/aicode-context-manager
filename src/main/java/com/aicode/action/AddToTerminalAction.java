package com.aicode.action;

import com.aicode.service.AICodeFileService;
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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Inserts selected project paths at the cursor of the currently selected terminal.
 */
public class AddToTerminalAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (project == null || files == null || files.length == 0) {
            return;
        }

        List<String> relativePaths = getRelativePaths(project, files);
        if (relativePaths.size() != files.length) {
            showNotification(project, "Only files inside the project root can be added to Terminal", NotificationType.WARNING);
            return;
        }

        TerminalToolWindowManager terminalManager = TerminalToolWindowManager.getInstance(project);
        ToolWindow toolWindow = terminalManager.getToolWindow();
        Content selectedContent = toolWindow == null ? null : toolWindow.getContentManager().getSelectedContent();
        TerminalWidget terminalWidget = selectedContent == null
                ? null
                : TerminalToolWindowManager.findWidgetByContent(selectedContent);
        if (terminalWidget == null) {
            showNotification(project, "Open a terminal session before adding a file path", NotificationType.WARNING);
            return;
        }

        String text = relativePaths.stream()
                .map(path -> "@" + path)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        try {
            terminalWidget.getTtyConnectorAccessor().executeWithTtyConnector(connector -> {
                try {
                    connector.write(text);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            showNotification(project, "Failed to add the file path to Terminal: " + ex.getMessage(), NotificationType.ERROR);
            return;
        }
        if (toolWindow != null) {
            toolWindow.activate(terminalWidget::requestFocus);
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
        Notifications.Bus.notify(new Notification(
                "AICode",
                "AICode Context Manager",
                content,
                type
        ), project);
    }
}
