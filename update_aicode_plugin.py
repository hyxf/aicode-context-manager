import os

# 定义文件路径
FILE_PATHS = {
    "config_model": os.path.join("src", "main", "java", "com", "aicode", "model", "AICodeConfig.java"),
    "service": os.path.join("src", "main", "java", "com", "aicode", "service", "AICodeFileService.java"),
    "panel": os.path.join("src", "main", "java", "com", "aicode", "ui", "AICodePanel.java")
}

# 1. AICodeConfig.java (保持不变: LinkedHashMap 优化)
# ------------------------------------------------------------------
config_model_content = """package com.aicode.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data model for .aicode.json
 * Supports multiple context groups.
 * Optimized with LinkedHashMap to preserve order in JSON.
 */
public class AICodeConfig {
    public static final String DEFAULT_GROUP = "Default";

    private String activeGroup = DEFAULT_GROUP;
    private Map<String, List<String>> groups = new LinkedHashMap<>();

    public AICodeConfig() {
        groups.put(DEFAULT_GROUP, new ArrayList<>());
    }

    public String getActiveGroup() {
        if (activeGroup == null || activeGroup.isEmpty()) {
            activeGroup = DEFAULT_GROUP;
        }
        return activeGroup;
    }

    public void setActiveGroup(String activeGroup) {
        this.activeGroup = activeGroup;
    }

    public Map<String, List<String>> getGroups() {
        if (groups == null) {
            groups = new LinkedHashMap<>();
        }
        return groups;
    }

    public void setGroups(Map<String, List<String>> groups) {
        this.groups = groups;
    }

    public List<String> getActivePaths() {
        return getGroups().computeIfAbsent(getActiveGroup(), k -> new ArrayList<>());
    }

    public void setActivePaths(List<String> paths) {
        getGroups().put(getActiveGroup(), paths != null ? paths : new ArrayList<>());
    }
}
"""

# 2. AICodeFileService.java (保持不变: 支持 rename)
# ------------------------------------------------------------------
service_content = """package com.aicode.service;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for managing .aicode.json file operations with Context Groups support.
 */
public class AICodeFileService {
    private static final String AICODE_FILE_NAME = ".aicode.json";
    private final Project project;
    private final Gson gson;

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

    @Nullable
    public VirtualFile getOrCreateAICodeFile() {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) return null;

        VirtualFile aiCodeFile = baseDir.findChild(AICODE_FILE_NAME);
        if (aiCodeFile != null) return aiCodeFile;

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

    @NotNull
    public AICodeConfig readConfig() {
        VirtualFile aiCodeFile = getOrCreateAICodeFile();
        if (aiCodeFile == null) return new AICodeConfig();

        try {
            String content = new String(aiCodeFile.contentsToByteArray(), StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) return new AICodeConfig();

            try {
                AICodeConfig config = gson.fromJson(content, AICodeConfig.class);
                if (config != null && config.getGroups() != null && !config.getGroups().isEmpty()) {
                    return config;
                }
            } catch (JsonSyntaxException ignored) {}

            try {
                Type listType = new TypeToken<ArrayList<String>>() {}.getType();
                List<String> oldPaths = gson.fromJson(content, listType);
                if (oldPaths != null) {
                    AICodeConfig config = new AICodeConfig();
                    config.setActiveGroup(AICodeConfig.DEFAULT_GROUP);
                    config.getGroups().put(AICodeConfig.DEFAULT_GROUP, oldPaths);
                    return config;
                }
            } catch (JsonSyntaxException ignored) {}

            return new AICodeConfig();
        } catch (Exception e) {
            return new AICodeConfig();
        }
    }

    public void saveConfig(@NotNull AICodeConfig config) {
        VirtualFile aiCodeFile = getOrCreateAICodeFile();
        if (aiCodeFile == null) return;

        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                String json = gson.toJson(config);
                aiCodeFile.setBinaryContent(json.getBytes(StandardCharsets.UTF_8));
                notifyChange();
            } catch (IOException e) {}
        });
    }

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
            config.setActiveGroup(groupName);
            saveConfig(config);
        }
    }

    public void renameGroup(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) return;
        AICodeConfig config = readConfig();
        Map<String, List<String>> groups = config.getGroups();

        if (groups.containsKey(oldName) && !groups.containsKey(newName)) {
            List<String> paths = groups.remove(oldName);
            groups.put(newName, paths);
            if (oldName.equals(config.getActiveGroup())) {
                config.setActiveGroup(newName);
            }
            saveConfig(config);
        }
    }

    public void removeGroup(String groupName) {
        AICodeConfig config = readConfig();
        if (config.getGroups().size() <= 1 && config.getGroups().containsKey(groupName)) {
            config.getGroups().remove(groupName);
            config.getGroups().put(AICodeConfig.DEFAULT_GROUP, new ArrayList<>());
            config.setActiveGroup(AICodeConfig.DEFAULT_GROUP);
        } else {
            config.getGroups().remove(groupName);
            if (groupName.equals(config.getActiveGroup())) {
                String nextGroup = config.getGroups().keySet().iterator().next();
                config.setActiveGroup(nextGroup);
            }
        }
        saveConfig(config);
    }

    @NotNull
    public List<String> readFilePaths() {
        return readConfig().getActivePaths();
    }

    public void writeFilePaths(@NotNull List<String> paths) {
        AICodeConfig config = readConfig();
        config.setActivePaths(paths);
        saveConfig(config);
    }

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
        if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
        return relativePath;
    }

    @Nullable
    public VirtualFile getFileFromPath(@NotNull String relativePath) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) return null;
        return baseDir.findFileByRelativePath(relativePath);
    }
}
"""

# 3. AICodePanel.java (更新: 菜单带图标，Dialog无图标)
# ------------------------------------------------------------------
panel_content = """package com.aicode.ui;

import com.aicode.service.AICodeFileService;
import com.aicode.util.ClipboardService;
import com.aicode.util.MarkdownBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Panel for AICode Tool Window with Tree View and Context Groups
 */
public class AICodePanel extends JPanel implements Disposable {
    private final Project project;
    private final Tree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;

    public AICodePanel(@NotNull Project project) {
        this.project = project;
        this.rootNode = new DefaultMutableTreeNode(new AICodeNodeData("Project", null, true));
        this.treeModel = new DefaultTreeModel(rootNode);
        this.tree = new Tree(treeModel);

        setLayout(new BorderLayout());
        setupUI();
        setupListeners();
        refreshTree();

        project.getMessageBus().connect(this).subscribe(
                AICodeFileService.AICODE_TOPIC,
                new AICodeFileService.AICodeStateListener() {
                    @Override
                    public void onContextChanged() {
                        refreshTree();
                    }
                }
        );
    }

    private void setupUI() {
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new AICodeTreeCellRenderer());
        tree.getEmptyText().setText("No files in this context group.");

        JBScrollPane scrollPane = new JBScrollPane(tree);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        add(createToolbar(), BorderLayout.NORTH);
    }

    private JComponent createToolbar() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        actionGroup.add(new GroupSelectorAction());
        actionGroup.addSeparator();

        actionGroup.add(new AnAction("Open Configuration", "Open .aicode.json configuration file", AllIcons.General.Settings) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                openAICodeFile();
            }
        });

        actionGroup.add(new AnAction("Copy as Markdown", "Export current context group as Markdown", AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                copyMarkdownToClipboard();
            }
        });

        actionGroup.addSeparator();

        TreeExpander treeExpander = new TreeExpander() {
            @Override public void expandAll() { TreeUtil.expandAll(tree); }
            @Override public boolean canExpand() { return true; }
            @Override public void collapseAll() { TreeUtil.collapseAll(tree, 1); }
            @Override public boolean canCollapse() { return true; }
        };
        CommonActionsManager actionsManager = CommonActionsManager.getInstance();
        actionGroup.add(actionsManager.createExpandAllAction(treeExpander, tree));
        actionGroup.add(actionsManager.createCollapseAllAction(treeExpander, tree));

        actionGroup.addSeparator();
        actionGroup.add(new AnAction("Refresh", "Refresh tree", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                refreshTree();
            }
        });

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("AICodeToolbar", actionGroup, true);
        toolbar.setTargetComponent(this);
        return toolbar.getComponent();
    }

    // ============================================================
    // Group Selector Logic
    // ============================================================

    private class GroupSelectorAction extends ComboBoxAction {
        @Override
        public void update(@NotNull AnActionEvent e) {
            Project p = e.getProject();
            if (p != null) {
                String activeGroup = AICodeFileService.getInstance(p).getActiveGroupName();
                e.getPresentation().setText(activeGroup);
                e.getPresentation().setDescription("Current Context Group: " + activeGroup);
                e.getPresentation().setIcon(AllIcons.Nodes.ModuleGroup);
            }
        }

        @NotNull
        @Override
        protected DefaultActionGroup createPopupActionGroup(JComponent button) {
            DefaultActionGroup group = new DefaultActionGroup();
            AICodeFileService service = AICodeFileService.getInstance(project);
            String activeGroup = service.getActiveGroupName();
            Set<String> allGroups = service.getGroupNames();

            List<String> sortedGroups = new ArrayList<>(allGroups);
            Collections.sort(sortedGroups);

            // 1. Switch Group
            for (String groupName : sortedGroups) {
                boolean isSelected = groupName.equals(activeGroup);
                group.add(new AnAction(groupName, "Switch to " + groupName, isSelected ? AllIcons.Actions.Checked : null) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        service.setActiveGroup(groupName);
                    }
                });
            }

            group.addSeparator();

            // 2. New Group (Icon in Menu: Yes; Icon in Dialog: No)
            group.add(new AnAction("New Group...", "Create a new empty context group", AllIcons.General.Add) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    String name = Messages.showInputDialog(project,
                        "Enter name for new context group:",
                        "New Group",
                        null); // Set Icon to null for Dialog
                    if (name != null && !name.trim().isEmpty()) {
                        service.addGroup(name.trim());
                    }
                }
            });

            // 3. Rename Group (Icon in Menu: Yes; Icon in Dialog: No)
            group.add(new AnAction("Rename Current Group...", "Rename the currently active group", AllIcons.Actions.Edit) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    String current = service.getActiveGroupName();
                    String newName = Messages.showInputDialog(project,
                        "Rename group '" + current + "' to:",
                        "Rename Group",
                        null, // Set Icon to null for Dialog
                        current,
                        null);

                    if (newName != null && !newName.trim().isEmpty() && !newName.equals(current)) {
                        if (service.getGroupNames().contains(newName)) {
                            Messages.showErrorDialog(project, "Group '" + newName + "' already exists.", "Rename Error");
                        } else {
                            service.renameGroup(current, newName.trim());
                        }
                    }
                }
            });

            // 4. Delete Group
            group.add(new AnAction("Delete Current Group", "Delete the currently active group", AllIcons.General.Remove) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    String current = service.getActiveGroupName();
                    int result = Messages.showYesNoDialog(project,
                        "Are you sure you want to delete context group '" + current + "'?",
                        "Delete Group", Messages.getWarningIcon());
                    if (result == Messages.YES) {
                        service.removeGroup(current);
                    }
                }
                @Override
                public void update(@NotNull AnActionEvent e) {
                    e.getPresentation().setEnabled(service.getGroupNames().size() > 1);
                }
            });

            return group;
        }
    }

    private void copyMarkdownToClipboard() {
        AICodeFileService service = AICodeFileService.getInstance(project);
        List<String> filePaths = service.readFilePaths();
        String group = service.getActiveGroupName();

        if (filePaths.isEmpty()) {
            showNotification("Group '" + group + "' is empty.", NotificationType.WARNING);
            return;
        }

        try {
            String markdown = MarkdownBuilder.buildMarkdown(project, filePaths, service::getFileFromPath);
            ClipboardService.copyToClipboard(markdown);
            showNotification("Copied group '" + group + "' (" + filePaths.size() + " files) to clipboard.", NotificationType.INFORMATION);
        } catch (Exception ex) {
            showNotification("Failed to export: " + ex.getMessage(), NotificationType.ERROR);
        }
    }

    private void showNotification(String content, NotificationType type) {
        Notification notification = new Notification("AICode", "AICode Context", content, type);
        Notifications.Bus.notify(notification, project);
    }

    private void setupListeners() {
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        openFileFromNode(node);
                    }
                }
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        tree.setSelectionPath(path);
                        showContextMenu(e, (DefaultMutableTreeNode) path.getLastPathComponent());
                    }
                }
            }
        });
    }

    private void showContextMenu(MouseEvent e, DefaultMutableTreeNode node) {
        if (node == null || node.getUserObject() == null) return;
        AICodeNodeData data = (AICodeNodeData) node.getUserObject();
        JPopupMenu menu = new JPopupMenu();
        String removeText = data.isDirectory ? "Remove Directory from Context" : "Remove File from Context";
        JMenuItem removeItem = new JMenuItem(removeText);
        removeItem.setIcon(AllIcons.Actions.Cancel);
        removeItem.addActionListener(actionEvent -> removeNodeContext(node));
        menu.add(removeItem);
        menu.show(tree, e.getX(), e.getY());
    }

    private void removeNodeContext(DefaultMutableTreeNode node) {
        AICodeFileService service = AICodeFileService.getInstance(project);
        List<String> pathsToRemove = new ArrayList<>();
        collectPaths(node, pathsToRemove);
        for (String path : pathsToRemove) {
            service.removeFilePath(path);
        }
    }

    private void collectPaths(DefaultMutableTreeNode node, List<String> collector) {
        Object userObject = node.getUserObject();
        if (userObject instanceof AICodeNodeData) {
            AICodeNodeData data = (AICodeNodeData) userObject;
            if (!data.isDirectory && data.fullRelativePath != null) {
                collector.add(data.fullRelativePath);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectPaths((DefaultMutableTreeNode) node.getChildAt(i), collector);
        }
    }

    private void openFileFromNode(DefaultMutableTreeNode node) {
        Object userObject = node.getUserObject();
        if (userObject instanceof AICodeNodeData) {
            AICodeNodeData data = (AICodeNodeData) userObject;
            if (!data.isDirectory && data.virtualFile != null) {
                FileEditorManager.getInstance(project).openFile(data.virtualFile, true);
            }
        }
    }

    public void refreshTree() {
        SwingUtilities.invokeLater(() -> {
            rootNode.removeAllChildren();
            AICodeFileService service = AICodeFileService.getInstance(project);
            AICodeNodeData rootData = (AICodeNodeData) rootNode.getUserObject();
            String groupName = service.getActiveGroupName();
            rootData.displayName = "Group: " + groupName;
            rootData.virtualFile = project.getBaseDir();

            List<String> paths = service.readFilePaths();
            Collections.sort(paths);
            buildTreeStructure(paths, service);
            treeModel.reload();
            TreeUtil.expandAll(tree);
        });
    }

    private void buildTreeStructure(List<String> paths, AICodeFileService service) {
        Map<String, DefaultMutableTreeNode> directoryNodes = new HashMap<>();
        for (String path : paths) {
            String[] parts = path.split("/");
            DefaultMutableTreeNode currentNode = rootNode;
            String currentPathAccumulator = "";
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean isLast = (i == parts.length - 1);
                if (!currentPathAccumulator.isEmpty()) currentPathAccumulator += "/";
                currentPathAccumulator += part;

                if (isLast) {
                    VirtualFile file = service.getFileFromPath(path);
                    AICodeNodeData fileData = new AICodeNodeData(part, path, false);
                    fileData.virtualFile = file;
                    currentNode.add(new DefaultMutableTreeNode(fileData));
                } else {
                    String dirPath = currentPathAccumulator;
                    if (directoryNodes.containsKey(dirPath)) {
                        currentNode = directoryNodes.get(dirPath);
                    } else {
                        VirtualFile dirFile = service.getFileFromPath(dirPath);
                        AICodeNodeData dirData = new AICodeNodeData(part, dirPath, true);
                        dirData.virtualFile = dirFile;
                        DefaultMutableTreeNode dirNode = new DefaultMutableTreeNode(dirData);
                        directoryNodes.put(dirPath, dirNode);
                        currentNode.add(dirNode);
                        currentNode = dirNode;
                    }
                }
            }
        }
    }

    private void openAICodeFile() {
        AICodeFileService service = AICodeFileService.getInstance(project);
        VirtualFile aiCodeFile = service.getOrCreateAICodeFile();
        if (aiCodeFile != null) {
            FileEditorManager.getInstance(project).openFile(aiCodeFile, true);
        }
    }

    @Override
    public void dispose() {}

    private static class AICodeNodeData {
        String displayName;
        String fullRelativePath;
        boolean isDirectory;
        VirtualFile virtualFile;
        public AICodeNodeData(String displayName, String fullRelativePath, boolean isDirectory) {
            this.displayName = displayName;
            this.fullRelativePath = fullRelativePath;
            this.isDirectory = isDirectory;
        }
        @Override public String toString() { return displayName; }
    }

    private static class AICodeTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode)) return;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();
            if (userObject instanceof AICodeNodeData) {
                AICodeNodeData data = (AICodeNodeData) userObject;
                if (data.displayName.startsWith("Group: ")) {
                    setIcon(AllIcons.Nodes.ModuleGroup);
                } else if (data.virtualFile != null) {
                    setIcon(data.isDirectory ? AllIcons.Nodes.Folder : data.virtualFile.getFileType().getIcon());
                } else {
                    setIcon(data.isDirectory ? AllIcons.Nodes.Folder : AllIcons.FileTypes.Unknown);
                }
                if (data.virtualFile == null && !data.isDirectory && !data.displayName.startsWith("Group: ")) {
                    append(data.displayName, SimpleTextAttributes.ERROR_ATTRIBUTES);
                    append(" (missing)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                } else {
                    append(data.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }
            }
        }
    }
}
"""

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Updated: {path}")

write_file(FILE_PATHS["config_model"], config_model_content)
write_file(FILE_PATHS["service"], service_content)
write_file(FILE_PATHS["panel"], panel_content)

print("AICode Context Groups UI updated (Dialog icons removed, Menu icons restored).")