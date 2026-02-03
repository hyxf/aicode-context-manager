import os
import sys

# Define base paths
BASE_DIR = os.getcwd()
SRC_MAIN_JAVA = os.path.join(BASE_DIR, "aicode-context-manager", "src", "main", "java", "com", "aicode")

def update_file(file_path, content):
    """Writes content to a file."""
    full_path = os.path.join(SRC_MAIN_JAVA, file_path)
    try:
        with open(full_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"✅ Successfully updated: {file_path}")
    except Exception as e:
        print(f"❌ Error updating {file_path}: {e}")
        sys.exit(1)

# -----------------------------------------------------------------------------
# 1. Update AddToAICodeAction.java
# Requirement: Don't show "Add to AICode" for .aicode.json
# -----------------------------------------------------------------------------
add_action_content = r"""package com.aicode.action;

import com.aicode.service.AICodeFileService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Action to add file to AICode context
 */
public class AddToAICodeAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (project == null || file == null || file.isDirectory()) {
            return;
        }

        AICodeFileService service = AICodeFileService.getInstance(project);
        service.addFile(file);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        boolean visible = false;
        if (project != null && file != null && !file.isDirectory()) {
            // Prevent adding the configuration file itself
            if (!".aicode.json".equals(file.getName())) {
                AICodeFileService service = AICodeFileService.getInstance(project);
                // Only show "Add" if file is not already in the list
                visible = !service.containsFile(file);
            }
        }

        e.getPresentation().setEnabledAndVisible(visible);
    }
}
"""

# -----------------------------------------------------------------------------
# 2. Update AICodeFileService.java
# Requirement: Publish events when state changes so UI can auto-refresh
# -----------------------------------------------------------------------------
service_content = r"""package com.aicode.service;

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

    private void notifyChange() {
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

# -----------------------------------------------------------------------------
# 3. Update AICodePanel.java
# Requirement: Listen to events, beautify UI (ColoredListCellRenderer, Icons)
# -----------------------------------------------------------------------------
panel_content = r"""package com.aicode.ui;

import com.aicode.service.AICodeFileService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Panel for AICode Tool Window
 */
public class AICodePanel extends JPanel implements Disposable {
    private final Project project;
    private final JBList<FileListItem> fileList;
    private final DefaultListModel<FileListItem> listModel;

    public AICodePanel(@NotNull Project project) {
        this.project = project;
        this.listModel = new DefaultListModel<>();
        this.fileList = new JBList<>(listModel);

        setLayout(new BorderLayout());
        setupUI();
        setupListeners();

        // Initial load
        refreshList();

        // Subscribe to changes
        project.getMessageBus().connect(this).subscribe(
            AICodeFileService.AICODE_TOPIC,
            this::refreshList
        );
    }

    private void setupUI() {
        // Beautify List Renderer
        fileList.setCellRenderer(new ColoredListCellRenderer<FileListItem>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends FileListItem> list,
                                                 FileListItem value,
                                                 int index,
                                                 boolean selected,
                                                 boolean hasFocus) {
                if (value == null) return;

                VirtualFile file = value.getFile();

                // Icon
                if (file != null) {
                    setIcon(file.getFileType().getIcon());
                } else {
                    // Fallback icon if file missing
                    setIcon(IconUtil.getEmptyIcon(true));
                }

                // Text
                if (file == null) {
                    append("⚠️ " + value.getPath(), SimpleTextAttributes.ERROR_ATTRIBUTES);
                    append(" (missing)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                } else {
                    append(file.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

                    // Module info or directory info in gray
                    String extraInfo = "";
                    if (value.getModuleName() != null) {
                        extraInfo += "[" + value.getModuleName() + "] ";
                    }

                    // Show parent directory for context
                    String parentPath = value.getPath().substring(0, value.getPath().length() - file.getName().length());
                    if (parentPath.endsWith("/")) {
                        parentPath = parentPath.substring(0, parentPath.length() - 1);
                    }
                    if (!parentPath.isEmpty()) {
                        extraInfo += parentPath;
                    }

                    if (!extraInfo.isEmpty()) {
                        append("  " + extraInfo, SimpleTextAttributes.GRAY_ATTRIBUTES);
                    }
                }
            }
        });

        fileList.setEmptyText("No context files. Right-click files in Project View to add.");

        JBScrollPane scrollPane = new JBScrollPane(fileList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        // Add toolbar
        add(createToolbar(), BorderLayout.NORTH);
    }

    private JComponent createToolbar() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        actionGroup.add(new AnAction("Open Configuration", "Open .aicode.json configuration file", null) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                openAICodeFile();
            }
        });

        actionGroup.add(new AnAction("Refresh", "Refresh file list", null) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                refreshList();
            }
        });

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
                "AICodeToolbar",
                actionGroup,
                true
        );
        toolbar.setTargetComponent(this);

        return toolbar.getComponent();
    }

    private void setupListeners() {
        // Double-click to open file
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    FileListItem item = fileList.getSelectedValue();
                    if (item != null && item.getFile() != null) {
                        FileEditorManager.getInstance(project).openFile(item.getFile(), true);
                    }
                }
            }
        });

        // Right-click menu
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = fileList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        fileList.setSelectedIndex(index);
                        showContextMenu(e);
                    }
                }
            }
        });
    }

    private void showContextMenu(MouseEvent e) {
        FileListItem item = fileList.getSelectedValue();
        if (item == null) {
            return;
        }

        JPopupMenu menu = new JPopupMenu();
        JMenuItem removeItem = new JMenuItem("Remove from AICode");
        removeItem.addActionListener(actionEvent -> {
            if (item.getFile() != null) {
                AICodeFileService.getInstance(project).removeFile(item.getFile());
            } else {
                // Remove by path if file is missing
                AICodeFileService.getInstance(project).removeFilePath(item.getPath());
            }
            // No manual refresh needed anymore, listener handles it
        });
        menu.add(removeItem);
        menu.show(fileList, e.getX(), e.getY());
    }

    public void refreshList() {
        // Run on EDT to ensure thread safety for UI updates
        SwingUtilities.invokeLater(() -> {
            listModel.clear();

            AICodeFileService service = AICodeFileService.getInstance(project);
            List<String> paths = service.readFilePaths();

            for (String path : paths) {
                VirtualFile file = service.getFileFromPath(path);
                String moduleName = getModuleName(file);
                listModel.addElement(new FileListItem(path, file, moduleName));
            }
        });
    }

    @Nullable
    private String getModuleName(@Nullable VirtualFile file) {
        if (file == null) {
            return null;
        }
        Module module = ModuleUtil.findModuleForFile(file, project);
        return module != null ? module.getName() : null;
    }

    private void openAICodeFile() {
        AICodeFileService service = AICodeFileService.getInstance(project);
        VirtualFile aiCodeFile = service.getOrCreateAICodeFile();
        if (aiCodeFile != null) {
            FileEditorManager.getInstance(project).openFile(aiCodeFile, true);
        }
    }

    @Override
    public void dispose() {
        // Resources are disposed automatically by the platform,
        // but this method is required by Disposable interface
    }

    /**
     * Data class for file list items
     */
    private static class FileListItem {
        private final String path;
        private final VirtualFile file;
        private final String moduleName;

        public FileListItem(String path, VirtualFile file, String moduleName) {
            this.path = path;
            this.file = file;
            this.moduleName = moduleName;
        }

        public VirtualFile getFile() {
            return file;
        }

        public String getPath() {
            return path;
        }

        public String getModuleName() {
            return moduleName;
        }
    }
}
"""

# -----------------------------------------------------------------------------
# Execution
# -----------------------------------------------------------------------------
print("🚀 Starting AICode Plugin Update...")

# Ensure directory exists (sanity check)
if not os.path.exists(SRC_MAIN_JAVA):
    print(f"❌ Could not find source directory: {SRC_MAIN_JAVA}")
    print("Please make sure you run this script from the project root.")
    sys.exit(1)

update_file("action/AddToAICodeAction.java", add_action_content)
update_file("service/AICodeFileService.java", service_content)
update_file("ui/AICodePanel.java", panel_content)

print("✨ Update complete! Please reload Gradle/Maven project and build.")