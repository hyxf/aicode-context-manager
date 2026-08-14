package com.aicode.feature.ai.action;

import com.aicode.feature.ai.service.AICodeFileService;
import com.aicode.feature.ai.settings.AICodeIgnoreSettings;
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
 * Supports multiple file selection.
 * Ignores binary files.
 */
public class AddToAICodeAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        // Support multi-selection
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

        if (project == null || files == null || files.length == 0) {
            return;
        }

        AICodeFileService service = AICodeFileService.getInstance(project);
        VirtualFile baseDir = project.getBaseDir();

        // Batch operation to avoid multiple refreshes
        List<String> currentPaths = new ArrayList<>(service.readFilePaths());
        Set<String> existingSet = new HashSet<>(currentPaths);
        List<String> newPathsToAdd = new ArrayList<>();

        for (VirtualFile file : files) {
            // Prevent adding project root
            if (file.equals(baseDir)) {
                continue;
            }

            if (file.isDirectory()) {
                // Recursively visit directory
                VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
                    @Override
                    public boolean visitFile(@NotNull VirtualFile child) {
                        // Check Ignore List
                        if (AICodeIgnoreSettings.isIgnored(child.getName())) {
                            return false; // Skip directory contents
                        }

                        if (!child.isDirectory()) {
                            // SKIP BINARY FILES
                            if (child.getFileType().isBinary()) {
                                return true;
                            }

                            if (!".aicode.json".equals(child.getName())) {
                                String relativePath = service.getRelativePath(child);
                                if (relativePath != null && !existingSet.contains(relativePath)) {
                                    newPathsToAdd.add(relativePath);
                                }
                            }
                        }
                        return true;
                    }
                });
            } else {
                // Single file
                if (AICodeIgnoreSettings.isIgnored(file.getName())) {
                    continue;
                }
                // SKIP BINARY FILES
                if (file.getFileType().isBinary()) {
                    continue;
                }

                if (!".aicode.json".equals(file.getName())) {
                    String relativePath = service.getRelativePath(file);
                    if (relativePath != null && !existingSet.contains(relativePath)) {
                        newPathsToAdd.add(relativePath);
                    }
                }
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
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

        boolean visible = false;
        if (project != null && files != null && files.length > 0) {
            AICodeFileService service = AICodeFileService.getInstance(project);

            // Check if ANY of the selected files can be added
            for (VirtualFile file : files) {
                // 1. Exclude Project Root
                boolean isRoot = file.equals(project.getBaseDir());
                // 2. Exclude config file
                boolean isConfigFile = ".aicode.json".equals(file.getName());
                // 3. Exclude Ignored Files
                boolean isIgnored = AICodeIgnoreSettings.isIgnored(file.getName());
                // 4. Exclude Binary Files (unless directory)
                boolean isBinary = !file.isDirectory() && file.getFileType().isBinary();

                if (!isRoot && !isConfigFile && !isIgnored && !isBinary) {
                    if (file.isDirectory()) {
                        // Directory is always addable (simplified)
                        visible = true;
                    } else {
                        // File is visible if NOT in list
                        if (!service.containsFile(file)) {
                            visible = true;
                        }
                    }
                }

                // If we found at least one valid candidate, enable the action
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
