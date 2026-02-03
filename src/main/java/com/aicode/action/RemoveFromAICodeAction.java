package com.aicode.action;

import com.aicode.service.AICodeFileService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Action to remove file or directory from AICode context
 * Supports multiple file selection.
 */
public class RemoveFromAICodeAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        // Support multi-selection
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

        if (project == null || files == null || files.length == 0) {
            return;
        }

        AICodeFileService service = AICodeFileService.getInstance(project);
        List<String> currentPaths = service.readFilePaths();
        Set<String> pathsToRemove = new HashSet<>();

        for (VirtualFile file : files) {
            if (file.isDirectory()) {
                // Collect all relative paths inside this directory that need removal
                String dirRelativePath = service.getRelativePath(file);
                if (dirRelativePath != null) {
                    // Determine prefix (e.g., "src/main/java/")
                    String prefix = dirRelativePath.endsWith("/") ? dirRelativePath : dirRelativePath + "/";

                    for (String path : currentPaths) {
                        if (path.startsWith(prefix) || path.equals(dirRelativePath)) {
                            pathsToRemove.add(path);
                        }
                    }
                }
            } else {
                // Single file
                String relativePath = service.getRelativePath(file);
                if (relativePath != null) {
                    pathsToRemove.add(relativePath);
                }
            }
        }

        if (!pathsToRemove.isEmpty()) {
            currentPaths.removeAll(pathsToRemove);
            service.writeFilePaths(currentPaths);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

        boolean visible = false;
        if (project != null && files != null && files.length > 0) {
            AICodeFileService service = AICodeFileService.getInstance(project);

            // Check if ANY of the selected files can be removed
            for (VirtualFile file : files) {
                if (file.isDirectory()) {
                    boolean isRoot = file.equals(project.getBaseDir());
                    if (!isRoot) {
                         String dirPath = service.getRelativePath(file);
                         if (dirPath != null) {
                             String prefix = dirPath.endsWith("/") ? dirPath : dirPath + "/";
                             // Check if any tracked file starts with this directory
                             List<String> paths = service.readFilePaths();
                             for (String path : paths) {
                                 if (path.startsWith(prefix)) {
                                     visible = true;
                                     break;
                                 }
                             }
                         }
                    }
                } else {
                    // Single file
                    if (service.containsFile(file)) {
                        visible = true;
                    }
                }

                if (visible) break;
            }
        }

        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
