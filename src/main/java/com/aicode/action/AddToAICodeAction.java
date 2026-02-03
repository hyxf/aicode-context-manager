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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Action to add file or directory (recursively) to AICode context
 */
public class AddToAICodeAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (project == null || file == null) {
            return;
        }

        // Prevent adding project root
        if (file.equals(project.getBaseDir())) {
            return;
        }

        AICodeFileService service = AICodeFileService.getInstance(project);

        // Batch operation to avoid multiple refreshes
        List<String> currentPaths = new ArrayList<>(service.readFilePaths());
        Set<String> existingSet = new HashSet<>(currentPaths);
        List<String> newPathsToAdd = new ArrayList<>();

        if (file.isDirectory()) {
            // Recursively visit directory
            VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile child) {
                    if (!child.isDirectory() && !".aicode.json".equals(child.getName())) {
                        String relativePath = service.getRelativePath(child);
                        if (relativePath != null && !existingSet.contains(relativePath)) {
                            newPathsToAdd.add(relativePath);
                        }
                    }
                    return true;
                }
            });
        } else {
            // Single file
            String relativePath = service.getRelativePath(file);
            if (relativePath != null && !existingSet.contains(relativePath)) {
                newPathsToAdd.add(relativePath);
            }
        }

        if (!newPathsToAdd.isEmpty()) {
            currentPaths.addAll(newPathsToAdd);
            service.writeFilePaths(currentPaths);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        boolean visible = false;
        if (project != null && file != null) {
            // 1. Exclude Project Root
            boolean isRoot = file.equals(project.getBaseDir());
            // 2. Exclude config file
            boolean isConfigFile = ".aicode.json".equals(file.getName());

            if (!isRoot && !isConfigFile) {
                AICodeFileService service = AICodeFileService.getInstance(project);

                if (file.isDirectory()) {
                    // For directory: Always show "Add" (simplified logic,
                    // or checking if ANY file inside is missing could be expensive)
                    visible = true;
                } else {
                    // For file: Only show if NOT in list
                    visible = !service.containsFile(file);
                }
            }
        }

        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
