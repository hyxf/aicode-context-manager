package com.aicode.ui;

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

        // Init Tree
        this.rootNode = new DefaultMutableTreeNode(new AICodeNodeData("Project", null, true));
        this.treeModel = new DefaultTreeModel(rootNode);
        this.tree = new Tree(treeModel);

        setLayout(new BorderLayout());
        setupUI();
        setupListeners();

        // Initial load
        refreshTree();

        // Subscribe to changes
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
        // 1. Configure Tree Appearance
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new AICodeTreeCellRenderer());
        tree.getEmptyText().setText("No files in this context group.");

        // 2. Scroll Pane
        JBScrollPane scrollPane = new JBScrollPane(tree);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        // 3. Toolbar
        add(createToolbar(), BorderLayout.NORTH);
    }

    private JComponent createToolbar() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        // Group Selector
        actionGroup.add(new GroupSelectorAction());
        actionGroup.addSeparator();

        // Open Config
        actionGroup.add(new AnAction("Open Configuration", "Open .aicode.json configuration file", AllIcons.General.Settings) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                openAICodeFile();
            }
        });

        // Copy as Markdown
        actionGroup.add(new AnAction("Copy as Markdown", "Export current context group as Markdown", AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                copyMarkdownToClipboard();
            }
        });

        actionGroup.addSeparator();

        // Expand/Collapse
        TreeExpander treeExpander = new TreeExpander() {
            @Override
            public void expandAll() {
                TreeUtil.expandAll(tree);
            }
            @Override
            public boolean canExpand() { return true; }
            @Override
            public void collapseAll() {
                TreeUtil.collapseAll(tree, 1);
            }
            @Override
            public boolean canCollapse() { return true; }
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
        @NotNull
        @Override
        public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
            JComponent component = super.createCustomComponent(presentation, place);
            // Optionally constrain width
            return component;
        }

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

            // 1. List existing groups
            List<String> sortedGroups = new ArrayList<>(allGroups);
            Collections.sort(sortedGroups);

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

            // 2. Add New Group
            group.add(new AnAction("New Group...", "Create a new empty context group", AllIcons.General.Add) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    String name = Messages.showInputDialog(project, "Enter name for new context group:", "New Group", Messages.getQuestionIcon());
                    if (name != null && !name.trim().isEmpty()) {
                        service.addGroup(name.trim());
                    }
                }
            });

            // 3. Remove Current Group
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
                    // Prevent deleting if it's the only one left
                    e.getPresentation().setEnabled(service.getGroupNames().size() > 1);
                }
            });

            return group;
        }
    }

    // ============================================================
    // Other Panel Logic
    // ============================================================

    private void copyMarkdownToClipboard() {
        AICodeFileService service = AICodeFileService.getInstance(project);
        List<String> filePaths = service.readFilePaths();
        String group = service.getActiveGroupName();

        if (filePaths.isEmpty()) {
            showNotification("Group '" + group + "' is empty.", NotificationType.WARNING);
            return;
        }

        try {
            String markdown = MarkdownBuilder.buildMarkdown(
                    project,
                    filePaths,
                    service::getFileFromPath
            );
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

            // Update Root Display with Group Name
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
