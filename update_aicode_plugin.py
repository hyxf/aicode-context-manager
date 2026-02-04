import os

# 定义文件路径
file_action = "src/main/java/com/aicode/action/AddToAICodeAction.java"
file_panel = "src/main/java/com/aicode/ui/AICodePanel.java"

# 1. 更新 AddToAICodeAction.java
# 增加 isBinary() 判断
content_action = """package com.aicode.action;

import com.aicode.service.AICodeFileService;
import com.aicode.settings.AICodeIgnoreSettings;
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
 * Ignores binary files.
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
                        // Check Ignore List
                        if (AICodeIgnoreSettings.isIgnored(child.getName())) {
                            return false; // Skip directory contents
                        }

                        if (!child.isDirectory()) {
                            // SKIP BINARY FILES
                            if (child.getFileType().isBinary()) {
                                return true;
                            }

                            if (!".aicode.json".equals(child.getName())) {
                                String relativePath = service.getRelativePath(child);
                                if (relativePath != null && !existingSet.contains(relativePath)) {
                                    newPathsToAdd.add(relativePath);
                                }
                            }
                        }
                        return true;
                    }
                });
            } else {
                // Single file
                if (AICodeIgnoreSettings.isIgnored(file.getName())) {
                    continue;
                }
                // SKIP BINARY FILES
                if (file.getFileType().isBinary()) {
                    continue;
                }

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
                // 3. Exclude Ignored Files
                boolean isIgnored = AICodeIgnoreSettings.isIgnored(file.getName());
                // 4. Exclude Binary Files (unless directory)
                boolean isBinary = !file.isDirectory() && file.getFileType().isBinary();

                if (!isRoot && !isConfigFile && !isIgnored && !isBinary) {
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

# 2. 更新 AICodePanel.java
# 在 checkHasMissingFiles 和 addMissingFiles 中加入 isBinary() 判断
content_panel = """package com.aicode.ui;

import com.aicode.service.AICodeFileService;
import com.aicode.settings.AICodeIgnoreSettings;
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
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
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
        // Root node initialization
        this.rootNode = new DefaultMutableTreeNode(new AICodeNodeData("Project", null, true, false));
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

            // 2. New Group (Validation -> Notification)
            group.add(new AnAction("New Group...", "Create a new empty context group", AllIcons.General.Add) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    String name = Messages.showInputDialog(project,
                        "Enter name for new context group:",
                        "New Group",
                        null);

                    if (name != null && !name.trim().isEmpty()) {
                        String trimmedName = name.trim();
                        if (service.getGroupNames().contains(trimmedName)) {
                            showNotification("Group '" + trimmedName + "' already exists.", NotificationType.ERROR);
                        } else {
                            service.addGroup(trimmedName);
                        }
                    }
                }
            });

            // 3. Rename Group (Validation -> Notification)
            group.add(new AnAction("Rename Current Group...", "Rename the currently active group", AllIcons.Actions.Edit) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    String current = service.getActiveGroupName();
                    String newName = Messages.showInputDialog(project,
                        "Rename group '" + current + "' to:",
                        "Rename Group",
                        null,
                        current,
                        null);

                    if (newName != null && !newName.trim().isEmpty() && !newName.equals(current)) {
                        String trimmedName = newName.trim();
                        if (service.getGroupNames().contains(trimmedName)) {
                            showNotification("Group '" + trimmedName + "' already exists.", NotificationType.ERROR);
                        } else {
                            service.renameGroup(current, trimmedName);
                        }
                    }
                }
            });

            // 4. Duplicate Group (Validation -> Notification)
            group.add(new AnAction("Duplicate Current Group...", "Create a copy of the current group", AllIcons.Actions.Copy) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    String current = service.getActiveGroupName();
                    String newName = Messages.showInputDialog(project,
                            "Enter name for the new group copy:",
                            "Duplicate Group",
                            null,
                            current + " Copy",
                            null);

                    if (newName != null && !newName.trim().isEmpty()) {
                        String trimmedName = newName.trim();
                        if (service.getGroupNames().contains(trimmedName)) {
                            showNotification("Group '" + trimmedName + "' already exists.", NotificationType.ERROR);
                        } else {
                            service.duplicateGroup(current, trimmedName);
                        }
                    }
                }
            });

            // 5. Delete Group
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

        // [Feature: Add Missing Files]
        // Only show if directory AND has missing files
        if (data.isDirectory && data.hasMissingFiles && data.virtualFile != null) {
            JMenuItem addMissingItem = new JMenuItem("Add Missing Files");
            addMissingItem.setIcon(AllIcons.General.Add);
            addMissingItem.addActionListener(actionEvent -> addMissingFiles(data.virtualFile));
            menu.add(addMissingItem);
            menu.addSeparator();
        }

        String removeText = data.isDirectory ? "Remove Directory from Context" : "Remove File from Context";
        JMenuItem removeItem = new JMenuItem(removeText);
        removeItem.setIcon(AllIcons.Actions.Cancel);
        removeItem.addActionListener(actionEvent -> removeNodeContext(node));
        menu.add(removeItem);

        menu.show(tree, e.getX(), e.getY());
    }

    private void addMissingFiles(@NotNull VirtualFile dir) {
        AICodeFileService service = AICodeFileService.getInstance(project);
        Set<String> currentPaths = new HashSet<>(service.readFilePaths());
        List<String> newPathsToAdd = new ArrayList<>();

        VfsUtilCore.visitChildrenRecursively(dir, new VirtualFileVisitor<Void>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                // 1. Ignore Check
                if (AICodeIgnoreSettings.isIgnored(file.getName())) {
                    return false; // Skip directory contents
                }

                // 2. Binary Check (SKIP BINARY)
                if (file.getFileType().isBinary()) {
                    return true;
                }

                // 3. Add file if not already tracked
                if (!file.isDirectory() && !".aicode.json".equals(file.getName())) {
                    String relativePath = service.getRelativePath(file);
                    if (relativePath != null && !currentPaths.contains(relativePath)) {
                        newPathsToAdd.add(relativePath);
                    }
                }
                return true;
            }
        });

        if (!newPathsToAdd.isEmpty()) {
            List<String> allPaths = new ArrayList<>(currentPaths);
            allPaths.addAll(newPathsToAdd);
            service.writeFilePaths(allPaths);
            showNotification("Added " + newPathsToAdd.size() + " missing files.", NotificationType.INFORMATION);
        }
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
            // We don't mark root as missing to keep UI clean, per requirements
            rootData.hasMissingFiles = false;

            List<String> paths = service.readFilePaths();
            Collections.sort(paths);

            // Convert list to Set for fast O(1) lookups during tree building
            Set<String> pathSet = new HashSet<>(paths);

            buildTreeStructure(paths, pathSet, service);
            treeModel.reload();
            TreeUtil.expandAll(tree);
        });
    }

    private void buildTreeStructure(List<String> paths, Set<String> pathSet, AICodeFileService service) {
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
                    // Leaf node (File)
                    AICodeNodeData fileData = new AICodeNodeData(part, path, false, false);
                    fileData.virtualFile = file;
                    currentNode.add(new DefaultMutableTreeNode(fileData));
                } else {
                    String dirPath = currentPathAccumulator;
                    if (directoryNodes.containsKey(dirPath)) {
                        currentNode = directoryNodes.get(dirPath);
                    } else {
                        VirtualFile dirFile = service.getFileFromPath(dirPath);

                        // Check if this directory has missing files (files on disk but not in context)
                        boolean hasMissing = checkHasMissingFiles(dirFile, pathSet, service);

                        AICodeNodeData dirData = new AICodeNodeData(part, dirPath, true, hasMissing);
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

    /**
     * Checks if a directory contains any file that is NOT in the tracked paths set.
     * Uses recursion but respects Ignore Settings AND Binary Check to be efficient.
     */
    private boolean checkHasMissingFiles(VirtualFile dir, Set<String> trackedPaths, AICodeFileService service) {
        if (dir == null || !dir.isValid()) return false;

        // Use a 1-element array to allow modification inside inner class
        final boolean[] missingFound = {false};

        VfsUtilCore.visitChildrenRecursively(dir, new VirtualFileVisitor<Void>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (missingFound[0]) return false; // Stop checking if already found

                // 1. Ignore check (stop recursing if directory is ignored)
                if (AICodeIgnoreSettings.isIgnored(file.getName())) {
                    return false;
                }

                // 2. Binary Check (Ignore binary files for "missing" status)
                if (file.getFileType().isBinary()) {
                    return true; // Skip checking this file, but continue
                }

                if (!file.isDirectory()) {
                    // 3. File Check
                    if (!".aicode.json".equals(file.getName())) {
                         String relativePath = service.getRelativePath(file);
                         if (relativePath != null && !trackedPaths.contains(relativePath)) {
                             // Found a file on disk that is NOT tracked
                             missingFound[0] = true;
                             return false; // Stop visiting
                         }
                    }
                }
                return true; // Continue visiting
            }
        });

        return missingFound[0];
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
        boolean hasMissingFiles; // True if this directory has children not in context
        VirtualFile virtualFile;

        public AICodeNodeData(String displayName, String fullRelativePath, boolean isDirectory, boolean hasMissingFiles) {
            this.displayName = displayName;
            this.fullRelativePath = fullRelativePath;
            this.isDirectory = isDirectory;
            this.hasMissingFiles = hasMissingFiles;
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

                // Icon
                if (data.displayName.startsWith("Group: ")) {
                    setIcon(AllIcons.Nodes.ModuleGroup);
                } else if (data.virtualFile != null) {
                    setIcon(data.isDirectory ? AllIcons.Nodes.Folder : data.virtualFile.getFileType().getIcon());
                } else {
                    setIcon(data.isDirectory ? AllIcons.Nodes.Folder : AllIcons.FileTypes.Unknown);
                }

                // Text
                if (data.virtualFile == null && !data.isDirectory && !data.displayName.startsWith("Group: ")) {
                    append(data.displayName, SimpleTextAttributes.ERROR_ATTRIBUTES);
                    append(" (missing)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                } else {
                    append(data.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    // Add partial selection marker
                    if (data.isDirectory && data.hasMissingFiles) {
                        append(" (*)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    }
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

if __name__ == "__main__":
    write_file(file_action, content_action)
    write_file(file_panel, content_panel)
    print("Code updated to ignore binary files.")