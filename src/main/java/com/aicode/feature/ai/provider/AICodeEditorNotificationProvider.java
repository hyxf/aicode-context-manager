package com.aicode.feature.ai.provider;

import com.aicode.feature.ai.icons.AICodeIcons;
import com.aicode.feature.ai.service.AICodeFileService;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

/**
 * Provides an editor banner notification for files that are in the active AICode context.
 */
public class AICodeEditorNotificationProvider implements EditorNotificationProvider {

    @Override
    public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
        AICodeFileService service = AICodeFileService.getInstance(project);

        // Skip directories and the config file itself
        if (file.isDirectory() || ".aicode.json".equals(file.getName())) {
            return null;
        }

        // Banner 开关关闭时不显示
        if (!service.isBannerEnabled()) {
            return null;
        }

        // Only show banner if the file is tracked in the current AICode Context
        if (service.containsFile(file)) {
            return fileEditor -> {
                EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info);
                panel.setText("This file is in the active AICode context.");
                panel.icon(AICodeIcons.LOGO);

                panel.createActionLabel("Remove from Context", () -> {
                    service.removeFile(file);
                });

                return panel;
            };
        }

        return null;
    }
}