package com.aicode.listener;

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
