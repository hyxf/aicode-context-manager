package com.aicode.action;

import com.aicode.service.AICodeFileService;
import com.intellij.ide.DataManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.diagnostic.Logger;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Inserts selected project paths at the cursor of the currently selected terminal.
 */
public class AddToTerminalAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(AddToTerminalAction.class);
    private static final String TERMINAL_VIEW_CLASS = "com.intellij.terminal.frontend.view.TerminalView";
    private static final String TERMINAL_TAB_CLASS =
            "com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab";
    private static final String TERMINAL_TABS_MANAGER_CLASS =
            "com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager";
    private static final DataKey<Object> TERMINAL_VIEW_KEY = DataKey.create("TerminalView");

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
        if (selectedContent == null) {
            showNotification(project, "Open a terminal session before adding a file path", NotificationType.WARNING);
            return;
        }

        TerminalWidget terminalWidget = TerminalToolWindowManager.findWidgetByContent(selectedContent);

        String text = relativePaths.stream()
                .map(path -> "@" + path)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        try {
            boolean sentToReworkedTerminal = sendToReworkedTerminal(project, selectedContent, text);
            if (!sentToReworkedTerminal && terminalWidget == null) {
                showNotification(project, "Open a terminal session before adding a file path", NotificationType.WARNING);
                return;
            }
            if (!sentToReworkedTerminal) {
                sendToClassicTerminal(terminalWidget, text);
            }
        } catch (ReflectiveOperationException | UncheckedIOException ex) {
            LOG.warn("Failed to insert paths into the selected terminal", ex);
            showNotification(project, "Failed to add the file path to Terminal: " + ex.getMessage(), NotificationType.ERROR);
            return;
        }
        if (toolWindow != null) {
            toolWindow.activate(() -> {
                if (terminalWidget != null) {
                    terminalWidget.requestFocus();
                } else {
                    selectedContent.getComponent().requestFocusInWindow();
                }
            });
        }
    }

    /**
     * Uses the public Reworked Terminal API when it is available (2025.3+).
     * Reflection keeps the plugin binary compatible with its 2023.2 baseline.
     */
    private static boolean sendToReworkedTerminal(
            @NotNull Project project,
            @NotNull Content selectedContent,
            @NotNull String text
    ) throws ReflectiveOperationException {
        try {
            Class<?> terminalViewClass = Class.forName(TERMINAL_VIEW_CLASS);
            Object terminalView = findReworkedTerminalView(project, selectedContent);
            if (terminalView == null) {
                return false;
            }
            Method sendText = terminalViewClass.getMethod("sendText", String.class);
            sendText.invoke(terminalView, text);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    /**
     * Resolves the view from the tabs manager instead of relying on the outer Content component's
     * data context. In 2025.3 the TerminalView data provider is installed on an inner panel, so a
     * data context created for the outer tool-window panel cannot see it.
     */
    private static Object findReworkedTerminalView(
            @NotNull Project project,
            @NotNull Content selectedContent
    ) throws ReflectiveOperationException {
        try {
            Class<?> tabsManagerClass = Class.forName(TERMINAL_TABS_MANAGER_CLASS);
            Class<?> tabClass = Class.forName(TERMINAL_TAB_CLASS);
            Method getInstance = tabsManagerClass.getMethod("getInstance", Project.class);
            Method getTabs = tabsManagerClass.getMethod("getTabs");
            Method getContent = tabClass.getMethod("getContent");
            Method getView = tabClass.getMethod("getView");

            Object tabsManager = getInstance.invoke(null, project);
            Object tabs = getTabs.invoke(tabsManager);
            if (tabs instanceof Iterable<?> iterable) {
                for (Object tab : iterable) {
                    if (getContent.invoke(tab) == selectedContent) {
                        return getView.invoke(tab);
                    }
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            // The tabs manager API is absent before the Reworked Terminal was introduced.
        }

        return DataManager.getInstance()
                .getDataContext(selectedContent.getComponent())
                .getData(TERMINAL_VIEW_KEY);
    }

    private static void sendToClassicTerminal(
            @NotNull TerminalWidget terminalWidget,
            @NotNull String text
    ) {
        terminalWidget.getTtyConnectorAccessor().executeWithTtyConnector(connector -> {
            try {
                connector.write(text);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
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
