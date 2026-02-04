package com.aicode.service;

import com.aicode.model.AICodeConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Service for managing .aicode.json file operations with Context Groups support.
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

        // Create new file with empty config
        try {
            return WriteCommandAction.writeCommandAction(project).compute(() -> {
                try {
                    VirtualFile newFile = baseDir.createChildData(this, AICODE_FILE_NAME);
                    AICodeConfig defaultConfig = new AICodeConfig();
                    String json = gson.toJson(defaultConfig);
                    newFile.setBinaryContent(json.getBytes(StandardCharsets.UTF_8));
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
     * Read the full configuration.
     * Handles migration from old List format to new Object format.
     */
    @NotNull
    public AICodeConfig readConfig() {
        VirtualFile aiCodeFile = getOrCreateAICodeFile();
        if (aiCodeFile == null) {
            return new AICodeConfig();
        }

        try {
            String content = new String(aiCodeFile.contentsToByteArray(), StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) {
                return new AICodeConfig();
            }

            // 1. Try parse as new Config object
            try {
                AICodeConfig config = gson.fromJson(content, AICodeConfig.class);
                // Basic validation to ensure it wasn't parsed as an empty object from a list
                if (config != null && config.getGroups() != null && !config.getGroups().isEmpty()) {
                    return config;
                }
            } catch (JsonSyntaxException ignored) {
                // Not a valid object, might be a list (old format)
            }

            // 2. Fallback: Try parse as List<String> (Old Format)
            try {
                Type listType = new TypeToken<ArrayList<String>>() {}.getType();
                List<String> oldPaths = gson.fromJson(content, listType);

                if (oldPaths != null) {
                    // Migrate to new format
                    AICodeConfig config = new AICodeConfig();
                    config.setActiveGroup(AICodeConfig.DEFAULT_GROUP);
                    config.getGroups().put(AICodeConfig.DEFAULT_GROUP, oldPaths);
                    return config;
                }
            } catch (JsonSyntaxException ignored) {
                // Completely invalid
            }

            return new AICodeConfig();

        } catch (Exception e) {
            return new AICodeConfig();
        }
    }

    /**
     * Save configuration to file
     */
    public void saveConfig(@NotNull AICodeConfig config) {
        VirtualFile aiCodeFile = getOrCreateAICodeFile();
        if (aiCodeFile == null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                String json = gson.toJson(config);
                aiCodeFile.setBinaryContent(json.getBytes(StandardCharsets.UTF_8));
                notifyChange();
            } catch (IOException e) {
                // Handle error
            }
        });
    }

    // ============================================================
    // Context Group Management
    // ============================================================

    public String getActiveGroupName() {
        return readConfig().getActiveGroup();
    }

    public Set<String> getGroupNames() {
        return readConfig().getGroups().keySet();
    }

    public void setActiveGroup(String groupName) {
        AICodeConfig config = readConfig();
        if (config.getGroups().containsKey(groupName)) {
            config.setActiveGroup(groupName);
            saveConfig(config);
        }
    }

    public void addGroup(String groupName) {
        AICodeConfig config = readConfig();
        if (!config.getGroups().containsKey(groupName)) {
            config.getGroups().put(groupName, new ArrayList<>());
            config.setActiveGroup(groupName); // Auto switch to new group
            saveConfig(config);
        }
    }

    public void removeGroup(String groupName) {
        AICodeConfig config = readConfig();
        // Don't allow removing the last group, or ensure at least one exists
        if (config.getGroups().size() <= 1 && config.getGroups().containsKey(groupName)) {
            // If deleting the only group, just clear it or rename to Default
            config.getGroups().remove(groupName);
            config.getGroups().put(AICodeConfig.DEFAULT_GROUP, new ArrayList<>());
            config.setActiveGroup(AICodeConfig.DEFAULT_GROUP);
        } else {
            config.getGroups().remove(groupName);
            // If we removed the active group, switch to default or any other
            if (groupName.equals(config.getActiveGroup())) {
                String nextGroup = config.getGroups().keySet().iterator().next();
                config.setActiveGroup(nextGroup);
            }
        }
        saveConfig(config);
    }

    // ============================================================
    // File Path Management (Operates on ACTIVE Group)
    // ============================================================

    /**
     * Read file paths from CURRENT ACTIVE group
     */
    @NotNull
    public List<String> readFilePaths() {
        return readConfig().getActivePaths();
    }

    /**
     * Write file paths to CURRENT ACTIVE group
     */
    public void writeFilePaths(@NotNull List<String> paths) {
        AICodeConfig config = readConfig();
        config.setActivePaths(paths);
        saveConfig(config);
    }

    /**
     * Add file to .aicode.json (Active Group)
     */
    public void addFile(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        if (relativePath == null) return;

        WriteCommandAction.runWriteCommandAction(project, "Add to AICode", null, () -> {
            AICodeConfig config = readConfig();
            List<String> paths = config.getActivePaths();
            if (!paths.contains(relativePath)) {
                paths.add(relativePath);
                config.setActivePaths(paths);
                saveConfig(config);
            }
        });
    }

    /**
     * Remove file from .aicode.json (Active Group)
     */
    public void removeFile(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        if (relativePath == null) return;

        WriteCommandAction.runWriteCommandAction(project, "Remove from AICode", null, () -> {
            AICodeConfig config = readConfig();
            List<String> paths = config.getActivePaths();
            if (paths.remove(relativePath)) {
                config.setActivePaths(paths);
                saveConfig(config);
            }
        });
    }

    /**
     * Remove file path from .aicode.json (Active Group)
     */
    public void removeFilePath(@NotNull String path) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            AICodeConfig config = readConfig();
            List<String> paths = config.getActivePaths();
            if (paths.remove(path)) {
                config.setActivePaths(paths);
                saveConfig(config);
            }
        });
    }

    /**
     * Update file path in .aicode.json (Active Group)
     */
    public void updateFilePath(@NotNull String oldPath, @NotNull String newPath) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            AICodeConfig config = readConfig();
            List<String> paths = config.getActivePaths();
            int index = paths.indexOf(oldPath);
            if (index >= 0) {
                paths.set(index, newPath);
                config.setActivePaths(paths);
                saveConfig(config);
            }
        });
    }

    public void notifyChange() {
        if (project.isDisposed()) return;
        project.getMessageBus().syncPublisher(AICODE_TOPIC).onContextChanged();
    }

    public boolean containsFile(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        if (relativePath == null) return false;
        return readFilePaths().contains(relativePath);
    }

    @Nullable
    public String getRelativePath(@NotNull VirtualFile file) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) return null;

        String basePath = baseDir.getPath();
        String filePath = file.getPath();

        if (!filePath.startsWith(basePath)) return null;

        String relativePath = filePath.substring(basePath.length());
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        return relativePath;
    }

    @Nullable
    public VirtualFile getFileFromPath(@NotNull String relativePath) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) return null;
        return baseDir.findFileByRelativePath(relativePath);
    }
}
