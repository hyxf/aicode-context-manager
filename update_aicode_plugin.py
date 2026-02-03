import os

# 定义工程根目录
PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))

def write_file(relative_path, content):
    full_path = os.path.join(PROJECT_ROOT, relative_path)
    # 确保目录存在
    os.makedirs(os.path.dirname(full_path), exist_ok=True)

    with open(full_path, 'w', encoding='utf-8') as f:
        f.write(content.strip())
    print(f"✅ Replaced: {relative_path}")

# ==========================================
# 1. AICodeFileService.java
# 修改：将 notifyChange() 改为 public，以便 Listener 可以调用
# ==========================================
service_content = """
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

        // Force refresh to ensure we read latest content from disk if externally modified
        aiCodeFile.refresh(false, false);

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
"""

# ==========================================
# 2. AICodeFileListener.java
# 修改：增加对 VFileContentChangeEvent 的处理
# ==========================================
listener_content = """
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
"""

def main():
    print("🚀 Updating AICode Logic to support manual .aicode.json editing...")

    files_to_update = {
        "src/main/java/com/aicode/service/AICodeFileService.java": service_content,
        "src/main/java/com/aicode/listener/AICodeFileListener.java": listener_content
    }

    for path, content in files_to_update.items():
        write_file(path, content)

    print("\n🎉 Service and Listener updated successfully!")
    print("Please run './gradlew buildPlugin' to rebuild.")

if __name__ == "__main__":
    main()