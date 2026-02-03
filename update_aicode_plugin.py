import os
import sys

# 1. AddToAICodeAction.java
# 修改点：
# - update: 允许文件夹（非根目录）。
# - actionPerformed: 如果是文件夹，递归获取内部所有文件，计算相对路径，批量添加到列表，一次性写入。
ADD_ACTION_CONTENT = r"""package com.aicode.action;

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
"""

# 2. RemoveFromAICodeAction.java
# 修改点：
# - 支持递归删除文件夹下的所有已选文件
REMOVE_ACTION_CONTENT = r"""package com.aicode.action;

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
"""

UPDATES = {
    "src/main/java/com/aicode/action/AddToAICodeAction.java": ADD_ACTION_CONTENT,
    "src/main/java/com/aicode/action/RemoveFromAICodeAction.java": REMOVE_ACTION_CONTENT
}

def main():
    # 路径跨平台处理
    for relative_path, content in UPDATES.items():
        full_path = os.path.join(*relative_path.split("/"))

        # 简单检查目录是否存在
        if not os.path.exists(os.path.dirname(full_path)):
            print(f"错误：找不到目录 {os.path.dirname(full_path)}")
            continue

        try:
            with open(full_path, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"成功更新: {full_path}")
        except IOError as e:
            print(f"写入失败 {full_path}: {e}")

    print("\n完成。右键菜单现在支持目录操作（已排除根目录）。")
    print("请重新构建插件。")

if __name__ == "__main__":
    main()