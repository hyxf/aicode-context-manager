import os
import sys

def create_file(path, content):
    # 如果路径以 / 或 \ 开头，去掉它
    if path.startswith('/') or path.startswith('\\'):
        path = path[1:]

    # 获取当前工作目录
    base_dir = os.getcwd()
    full_path = os.path.join(base_dir, path)
    dir_name = os.path.dirname(full_path)

    # 简单检查：如果您在错误的目录下运行（例如根目录下没有 src），给予提示
    if path.startswith('src') and not os.path.exists(os.path.join(base_dir, 'src')):
        print(f"Warning: 'src' directory not found in {base_dir}. Are you in the project root?")

    # 确保目录存在
    if not os.path.exists(dir_name):
        try:
            os.makedirs(dir_name)
        except OSError as e:
            print(f"Error creating directory {dir_name}: {e}")
            return

    # 写入文件
    try:
        with open(full_path, 'w', encoding='utf-8') as f:
            if content.startswith('\n'):
                f.write(content[1:])
            else:
                f.write(content)
        print(f"Updated: {path}")
    except IOError as e:
        print(f"Error writing to {full_path}: {e}")

files = {}

# ================= 修正路径后的文件列表 =================

# 1. AddToAICodeAction.java (路径去掉了项目名前缀)
files['src/main/java/com/aicode/action/AddToAICodeAction.java'] = r"""
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

                if (!isRoot && !isConfigFile) {
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

# 2. RemoveFromAICodeAction.java (路径去掉了项目名前缀)
files['src/main/java/com/aicode/action/RemoveFromAICodeAction.java'] = r"""
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
"""

def main():
    print("Updating action files (path corrected)...")
    for path, content in files.items():
        create_file(path, content)
    print("\nDone.")

if __name__ == '__main__':
    main()