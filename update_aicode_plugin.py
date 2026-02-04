import os

# 目标文件
file_listener = "src/main/java/com/aicode/listener/AICodeFileListener.java"

# 更新后的内容
content_listener = """package com.aicode.listener;

import com.aicode.service.AICodeFileService;
import com.aicode.settings.AICodeIgnoreSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Listener for file system events to auto-maintain .aicode.json
 * and refresh UI when file structure changes (e.g. for missing file detection).
 */
public class AICodeFileListener implements BulkFileListener {

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
            handleEvent(event);
        }
    }

    private void handleEvent(@NotNull VFileEvent event) {
        VirtualFile file = event.getFile();

        // For Create events, file might be null in getFile() sometimes depending on impl,
        // but usually available. For Delete, getFile() is valid.
        if (file == null) {
            return;
        }

        // Optimization: Ignore hidden files or explicitly ignored files (like .git changes)
        // to prevent excessive UI refreshes.
        if (AICodeIgnoreSettings.isIgnored(file.getName())) {
            return;
        }

        Project project = findProject(file);
        if (project == null) {
            return;
        }

        AICodeFileService service = AICodeFileService.getInstance(project);

        if (event instanceof VFileCreateEvent) {
            handleCreate(service, file);
        } else if (event instanceof VFileDeleteEvent) {
            handleDelete(service, file);
        } else if (event instanceof VFileMoveEvent) {
            handleMove(service, (VFileMoveEvent) event);
        } else if (event instanceof VFilePropertyChangeEvent) {
            handlePropertyChange(service, (VFilePropertyChangeEvent) event);
        } else if (event instanceof VFileContentChangeEvent) {
            handleContentChange(service, file);
        }
    }

    /**
     * Handle new file creation.
     * Even if we don't auto-add it, we must refresh UI because the parent directory's
     * "missing files" status (*) might have changed.
     */
    private void handleCreate(@NotNull AICodeFileService service, @NotNull VirtualFile file) {
        if (!file.isDirectory()) {
             service.notifyChange();
        }
    }

    /**
     * Handle file deletion.
     * If tracked, remove from config.
     * If not tracked, still refresh UI because (*) status might disappear.
     */
    private void handleDelete(@NotNull AICodeFileService service, @NotNull VirtualFile file) {
        String relativePath = service.getRelativePath(file);
        boolean wasTracked = false;

        if (relativePath != null && service.readFilePaths().contains(relativePath)) {
            service.removeFilePath(relativePath); // This triggers notifyChange() internally
            wasTracked = true;
        }

        // If it wasn't tracked, we still need to refresh UI to update (*) markers
        if (!wasTracked) {
            service.notifyChange();
        }
    }

    private void handleMove(@NotNull AICodeFileService service, @NotNull VFileMoveEvent event) {
        VirtualFile file = event.getFile();
        Project project = service.getProject(); // Using the project we found earlier effectively

        // Calculate old path
        VirtualFile oldParent = event.getOldParent();
        String oldPath = oldParent.getPath() + "/" + file.getName();

        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null && oldPath.startsWith(baseDir.getPath())) {
            String oldRelativePath = oldPath.substring(baseDir.getPath().length() + 1);
            String newRelativePath = service.getRelativePath(file);

            boolean updated = false;
            if (newRelativePath != null && service.readFilePaths().contains(oldRelativePath)) {
                service.updateFilePath(oldRelativePath, newRelativePath); // Triggers notifyChange
                updated = true;
            }

            if (!updated) {
                service.notifyChange();
            }
        }
    }

    private void handlePropertyChange(@NotNull AICodeFileService service, @NotNull VFilePropertyChangeEvent event) {
        if (!VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
            return;
        }

        VirtualFile file = event.getFile();
        String oldName = (String) event.getOldValue();
        String newName = (String) event.getNewValue();

        if (oldName == null || newName == null || oldName.equals(newName)) {
            return;
        }

        // Calculate old path logic
        VirtualFile parent = file.getParent();
        if (parent == null) return;

        Project project = service.getProject(); // Helper needed or use logic below
        VirtualFile baseDir = project.getBaseDir();

        if (baseDir != null && parent.getPath().startsWith(baseDir.getPath())) {
            String relativePath = parent.getPath().substring(baseDir.getPath().length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }

            String oldRelativePath = relativePath.isEmpty() ? oldName : relativePath + "/" + oldName;
            String newRelativePath = service.getRelativePath(file);

            boolean updated = false;
            if (newRelativePath != null && service.readFilePaths().contains(oldRelativePath)) {
                service.updateFilePath(oldRelativePath, newRelativePath); // Triggers notifyChange
                updated = true;
            }

            if (!updated) {
                service.notifyChange();
            }
        }
    }

    private void handleContentChange(@NotNull AICodeFileService service, @NotNull VirtualFile file) {
        if (".aicode.json".equals(file.getName())) {
            service.notifyChange();
        }
    }

    @org.jetbrains.annotations.Nullable
    private Project findProject(@NotNull VirtualFile file) {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            VirtualFile baseDir = project.getBaseDir();
            // Check if file is under project base dir
            if (baseDir != null && file.getPath().startsWith(baseDir.getPath())) {
                return project;
            }
        }
        return null;
    }
}
"""

# 为了支持 helper 方法获取 Project，我们需要稍微修改一下 AICodeFileService 增加 getProject()
# 或者直接在 Listener 里的 handleMove 复用逻辑。
# 上面的代码 handleMove 引用了 service.getProject()，这在之前的 Service 代码里没有。
# 我们需要先给 Service 加上 getProject()，或者在 Listener 里只用 findProject。
# 为了代码的安全性，我将修改 Listener 代码，不调用 service.getProject()，而是复用 findProject 的结果。

content_listener_fixed = """package com.aicode.listener;

import com.aicode.service.AICodeFileService;
import com.aicode.settings.AICodeIgnoreSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Listener for file system events to auto-maintain .aicode.json
 * and refresh UI when file structure changes.
 */
public class AICodeFileListener implements BulkFileListener {

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
            handleEvent(event);
        }
    }

    private void handleEvent(@NotNull VFileEvent event) {
        VirtualFile file = event.getFile();
        if (file == null) return;

        // Ignore noise
        if (AICodeIgnoreSettings.isIgnored(file.getName())) {
            return;
        }

        Project project = findProject(file);
        if (project == null) return;

        AICodeFileService service = AICodeFileService.getInstance(project);

        if (event instanceof VFileCreateEvent) {
            handleCreate(service, file);
        } else if (event instanceof VFileDeleteEvent) {
            handleDelete(service, file);
        } else if (event instanceof VFileMoveEvent) {
            handleMove(service, (VFileMoveEvent) event, project);
        } else if (event instanceof VFilePropertyChangeEvent) {
            handlePropertyChange(service, (VFilePropertyChangeEvent) event, project);
        } else if (event instanceof VFileContentChangeEvent) {
            handleContentChange(service, file);
        }
    }

    private void handleCreate(@NotNull AICodeFileService service, @NotNull VirtualFile file) {
        // Refresh UI so missing file indicators (*) can update
        service.notifyChange();
    }

    private void handleDelete(@NotNull AICodeFileService service, @NotNull VirtualFile file) {
        String relativePath = service.getRelativePath(file);
        boolean wasTracked = false;

        if (relativePath != null && service.readFilePaths().contains(relativePath)) {
            service.removeFilePath(relativePath); // This triggers notifyChange
            wasTracked = true;
        }

        if (!wasTracked) {
            service.notifyChange();
        }
    }

    private void handleMove(@NotNull AICodeFileService service, @NotNull VFileMoveEvent event, Project project) {
        VirtualFile file = event.getFile();
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) return;

        VirtualFile oldParent = event.getOldParent();
        if (oldParent == null) return;

        String oldPath = oldParent.getPath() + "/" + file.getName();

        if (oldPath.startsWith(baseDir.getPath())) {
            String oldRelativePath = oldPath.substring(baseDir.getPath().length() + 1);
            String newRelativePath = service.getRelativePath(file);

            boolean updated = false;
            if (newRelativePath != null && service.readFilePaths().contains(oldRelativePath)) {
                service.updateFilePath(oldRelativePath, newRelativePath);
                updated = true;
            }

            if (!updated) {
                service.notifyChange();
            }
        }
    }

    private void handlePropertyChange(@NotNull AICodeFileService service, @NotNull VFilePropertyChangeEvent event, Project project) {
        if (!VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
            return;
        }

        VirtualFile file = event.getFile();
        String oldName = (String) event.getOldValue();
        String newName = (String) event.getNewValue();

        if (oldName == null || newName == null || oldName.equals(newName)) {
            return;
        }

        VirtualFile parent = file.getParent();
        if (parent == null) return;

        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null && parent.getPath().startsWith(baseDir.getPath())) {
            String parentPath = parent.getPath();
            String relativePath = parentPath.substring(baseDir.getPath().length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }

            String oldRelativePath = relativePath.isEmpty() ? oldName : relativePath + "/" + oldName;
            String newRelativePath = service.getRelativePath(file);

            boolean updated = false;
            if (newRelativePath != null && service.readFilePaths().contains(oldRelativePath)) {
                service.updateFilePath(oldRelativePath, newRelativePath);
                updated = true;
            }

            if (!updated) {
                service.notifyChange();
            }
        }
    }

    private void handleContentChange(@NotNull AICodeFileService service, @NotNull VirtualFile file) {
        if (".aicode.json".equals(file.getName())) {
            service.notifyChange();
        }
    }

    @org.jetbrains.annotations.Nullable
    private Project findProject(@NotNull VirtualFile file) {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            VirtualFile baseDir = project.getBaseDir();
            if (baseDir != null && file.getPath().startsWith(baseDir.getPath())) {
                return project;
            }
        }
        return null;
    }
}
"""

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Updated: {path}")

if __name__ == "__main__":
    write_file(file_listener, content_listener_fixed)
    print("AICodeFileListener updated to handle Create events.")