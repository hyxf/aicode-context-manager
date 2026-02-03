package com.aicode.listener;

import com.aicode.service.AICodeFileService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Listener for file system events to auto-maintain .aicode.json
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
        if (file == null || file.isDirectory()) {
            return;
        }

        Project project = findProject(file);
        if (project == null) {
            return;
        }

        AICodeFileService service = AICodeFileService.getInstance(project);

        if (event instanceof VFileDeleteEvent) {
            handleDelete(service, file);
        } else if (event instanceof VFileMoveEvent) {
            handleMove(service, (VFileMoveEvent) event);
        } else if (event instanceof VFilePropertyChangeEvent) {
            handlePropertyChange(service, (VFilePropertyChangeEvent) event);
        } else if (event instanceof VFileContentChangeEvent) {
            // 新增：处理内容变化
            handleContentChange(service, file);
        }
    }

    /**
     * 监听文件内容变化，如果是 .aicode.json 被手动修改，则刷新 UI
     */
    private void handleContentChange(@NotNull AICodeFileService service, @NotNull VirtualFile file) {
        if (".aicode.json".equals(file.getName())) {
            // 配置文件内容改变，强制通知 UI 刷新
            service.notifyChange();
        }
    }

    private void handleDelete(@NotNull AICodeFileService service, @NotNull VirtualFile file) {
        String relativePath = service.getRelativePath(file);
        if (relativePath != null && service.readFilePaths().contains(relativePath)) {
            service.removeFilePath(relativePath);
        }
    }

    private void handleMove(@NotNull AICodeFileService service, @NotNull VFileMoveEvent event) {
        VirtualFile file = event.getFile();
        if (file == null) {
            return;
        }

        Project project = findProject(file);
        if (project == null) {
            return;
        }

        // Calculate old path
        VirtualFile oldParent = event.getOldParent();
        String oldPath = oldParent.getPath() + "/" + file.getName();

        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null && oldPath.startsWith(baseDir.getPath())) {
            String oldRelativePath = oldPath.substring(baseDir.getPath().length() + 1);
            String newRelativePath = service.getRelativePath(file);

            if (newRelativePath != null && service.readFilePaths().contains(oldRelativePath)) {
                service.updateFilePath(oldRelativePath, newRelativePath);
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

        Project project = findProject(file);
        if (project == null) {
            return;
        }

        // Calculate old path
        VirtualFile parent = file.getParent();
        if (parent == null) {
            return;
        }

        String parentPath = parent.getPath();
        VirtualFile baseDir = project.getBaseDir();

        if (baseDir != null && parentPath.startsWith(baseDir.getPath())) {
            String relativePath = parentPath.substring(baseDir.getPath().length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }

            String oldRelativePath = relativePath.isEmpty() ? oldName : relativePath + "/" + oldName;
            String newRelativePath = service.getRelativePath(file);

            if (newRelativePath != null && service.readFilePaths().contains(oldRelativePath)) {
                service.updateFilePath(oldRelativePath, newRelativePath);
            }
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