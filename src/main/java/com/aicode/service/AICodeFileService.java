package com.aicode.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing .aicode.json file operations
 */
public class AICodeFileService {
    private static final String AICODE_FILE_NAME = ".aicode.json";
    private final Project project;
    private final Gson gson;

    // Topic for notifications
    public static final Topic<AICodeStateListener> AICODE_TOPIC =
            Topic.create("AICode Context Changed", AICodeStateListener.class);

    public interface AICodeStateListener {
        void onContextChanged();
    }

    public AICodeFileService(Project project) {
        this.project = project;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @NotNull
    public static AICodeFileService getInstance(@NotNull Project project) {
        return project.getService(AICodeFileService.class);
    }

    /**
     * Get or create .aicode.json file in project root
     */
    @Nullable
    public VirtualFile getOrCreateAICodeFile() {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return null;
        }

        VirtualFile aiCodeFile = baseDir.findChild(AICODE_FILE_NAME);
        if (aiCodeFile != null) {
            return aiCodeFile;
        }

        // Create new file with empty array
        try {
            return WriteCommandAction.writeCommandAction(project).compute(() -> {
                try {
                    VirtualFile newFile = baseDir.createChildData(this, AICODE_FILE_NAME);
                    newFile.setBinaryContent("[]".getBytes(StandardCharsets.UTF_8));
                    return newFile;
                } catch (IOException e) {
                    return null;
                }
            });
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Read file paths from .aicode.json
     */
    @NotNull
    public List<String> readFilePaths() {
        VirtualFile aiCodeFile = getOrCreateAICodeFile();
        if (aiCodeFile == null) {
            return new ArrayList<>();
        }

        // FIX: Removed synchronous refresh on EDT which caused "Write-unsafe context" crash.
        // We rely on BulkFileListener and VFS events to keep things in sync.

        try {
            String content = new String(aiCodeFile.contentsToByteArray(), StandardCharsets.UTF_8);
            Type listType = new TypeToken<ArrayList<String>>() {}.getType();
            List<String> paths = gson.fromJson(content, listType);
            return paths != null ? paths : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Write file paths to .aicode.json
     */
    public void writeFilePaths(@NotNull List<String> paths) {
        VirtualFile aiCodeFile = getOrCreateAICodeFile();
        if (aiCodeFile == null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                String json = gson.toJson(paths);
                aiCodeFile.setBinaryContent(json.getBytes(StandardCharsets.UTF_8));
                // Notify listeners
                notifyChange();
            } catch (IOException e) {
                // Handle error
            }
        });
    }

    /**
     * Add file to .aicode.json
     */
    public void addFile(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        if (relativePath == null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, "Add to AICode", null, () -> {
            List<String> paths = readFilePaths();
            if (!paths.contains(relativePath)) {
                paths.add(relativePath);
                writeFilePaths(paths);
                // writeFilePaths calls notify, but wrapped here ensures consistency
            }
        });
    }

    /**
     * Remove file from .aicode.json
     */
    public void removeFile(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        if (relativePath == null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, "Remove from AICode", null, () -> {
            List<String> paths = readFilePaths();
            paths.remove(relativePath);
            writeFilePaths(paths);
        });
    }

    /**
     * Remove file path from .aicode.json (for file system events)
     */
    public void removeFilePath(@NotNull String path) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            List<String> paths = readFilePaths();
            paths.remove(path);
            writeFilePaths(paths);
        });
    }

    /**
     * Update file path in .aicode.json (for rename/move events)
     */
    public void updateFilePath(@NotNull String oldPath, @NotNull String newPath) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            List<String> paths = readFilePaths();
            int index = paths.indexOf(oldPath);
            if (index >= 0) {
                paths.set(index, newPath);
                writeFilePaths(paths);
            }
        });
    }

    /**
     * Public method to trigger context change notification.
     * Used by listeners when external changes happen to .aicode.json
     */
    public void notifyChange() {
        if (project.isDisposed()) return;
        project.getMessageBus().syncPublisher(AICODE_TOPIC).onContextChanged();
    }

    /**
     * Check if file is in .aicode.json
     */
    public boolean containsFile(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        if (relativePath == null) {
            return false;
        }
        return readFilePaths().contains(relativePath);
    }

    /**
     * Get relative path from project base path
     */
    @Nullable
    public String getRelativePath(@NotNull VirtualFile file) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return null;
        }

        String basePath = baseDir.getPath();
        String filePath = file.getPath();

        if (!filePath.startsWith(basePath)) {
            return null;
        }

        String relativePath = filePath.substring(basePath.length());
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        return relativePath;
    }

    /**
     * Get VirtualFile from relative path
     */
    @Nullable
    public VirtualFile getFileFromPath(@NotNull String relativePath) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return null;
        }
        return baseDir.findFileByRelativePath(relativePath);
    }
}
