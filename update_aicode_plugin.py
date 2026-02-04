import os

# 1. 定义文件路径
# 新建的配置类路径
file_settings = "src/main/java/com/aicode/settings/AICodeIgnoreSettings.java"
# 需要修改的 Action
file_action = "src/main/java/com/aicode/action/AddToAICodeAction.java"

# 2. 新建 AICodeIgnoreSettings.java 内容
content_settings = """package com.aicode.settings;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration class for ignored files and directories.
 * Defined in code as a static list.
 */
public class AICodeIgnoreSettings {

    // Configure your ignore list here
    public static final List<String> IGNORED_NAMES = Arrays.asList(
            ".git",
            ".idea",
            ".gradle",
            "build",
            "target",
            "out",
            "node_modules",
            ".DS_Store",
            "dist",
            ".mvn",
            "venv",
            "__pycache__"
    );

    /**
     * Check if the file/directory name should be ignored.
     */
    public static boolean isIgnored(String name) {
        return IGNORED_NAMES.contains(name);
    }
}
"""

# 3. 更新 AddToAICodeAction.java 内容
# 引入了 AICodeIgnoreSettings 并应用过滤逻辑
content_action = """package com.aicode.action;

import com.aicode.service.AICodeFileService;
import com.aicode.settings.AICodeIgnoreSettings;
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
                        // Check Ignore List (Directory or File name)
                        if (AICodeIgnoreSettings.isIgnored(child.getName())) {
                            // Return false to skip processing this directory's children
                            return false;
                        }

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
                // Check if the single file selected is in the ignore list
                if (AICodeIgnoreSettings.isIgnored(file.getName())) {
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

                if (!isRoot && !isConfigFile && !isIgnored) {
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
"""

def write_file(path, content):
    # Ensure directory exists
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Updated: {path}")

if __name__ == "__main__":
    write_file(file_settings, content_settings)
    write_file(file_action, content_action)
    print("Code update complete.")