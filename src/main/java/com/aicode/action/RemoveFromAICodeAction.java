package com.aicode.action;

import com.aicode.service.AICodeFileService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Action to remove file or directory from AICode context
 */
public class RemoveFromAICodeAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (project == null || file == null) {
            return;
        }

        AICodeFileService service = AICodeFileService.getInstance(project);
        List<String> currentPaths = service.readFilePaths();
        Set<String> pathsToRemove = new HashSet<>();

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

        if (!pathsToRemove.isEmpty()) {
            currentPaths.removeAll(pathsToRemove);
            service.writeFilePaths(currentPaths);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        boolean visible = false;
        if (project != null && file != null) {
            AICodeFileService service = AICodeFileService.getInstance(project);

            if (file.isDirectory()) {
                // If it's a directory, show "Remove" if ANY file inside is potentially tracked?
                // Or simplified: Just show it if it's not root, user can try to remove.
                // Better UX: check if directory path matches any prefix in the list.
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
                visible = service.containsFile(file);
            }
        }

        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
