package com.aicode.action;

import com.aicode.service.AICodeFileService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Action to add file to AICode context
 */
public class AddToAICodeAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (project == null || file == null || file.isDirectory()) {
            return;
        }

        AICodeFileService service = AICodeFileService.getInstance(project);
        service.addFile(file);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        boolean visible = false;
        if (project != null && file != null && !file.isDirectory()) {
            AICodeFileService service = AICodeFileService.getInstance(project);
            // Only show "Add" if file is not already in the list
            visible = !service.containsFile(file);
        }

        e.getPresentation().setEnabledAndVisible(visible);
    }
}
