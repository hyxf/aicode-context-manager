package com.aicode.ui;

import com.aicode.service.AICodeFileService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 * Panel for AICode Tool Window with Tree View
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
            this::refreshTree
        );
    }

    private void setupUI() {
        // 1. Configure Tree Appearance
        tree.setRootVisible(true); // Show project root
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new AICodeTreeCellRenderer());

        // Empty text
        tree.getEmptyText().setText("No context files. Right-click files in Project View to add.");

        // 2. Scroll Pane
        JBScrollPane scrollPane = new JBScrollPane(tree);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        // 3. Toolbar
        add(createToolbar(), BorderLayout.NORTH);
    }

    private JComponent createToolbar() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        // Open Config
        actionGroup.add(new AnAction("Open Configuration", "Open .aicode.json configuration file", AllIcons.General.Settings) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                openAICodeFile();
            }
        });

        actionGroup.addSeparator();

        // Expand All / Collapse All
        TreeExpander treeExpander = new TreeExpander() {
            @Override
            public void expandAll() {
                TreeUtil.expandAll(tree);
            }

            @Override
            public boolean canExpand() {
                return true;
            }

            @Override
            public void collapseAll() {
                TreeUtil.collapseAll(tree, 1); // Keep root expanded
            }

            @Override
            public boolean canCollapse() {
                return true;
            }
        };

        CommonActionsManager actionsManager = CommonActionsManager.getInstance();
        AnAction expandAllAction = actionsManager.createExpandAllAction(treeExpander, tree);
        AnAction collapseAllAction = actionsManager.createCollapseAllAction(treeExpander, tree);

        actionGroup.add(expandAllAction);
        actionGroup.add(collapseAllAction);

        actionGroup.addSeparator();

        // Refresh
        actionGroup.add(new AnAction("Refresh", "Refresh tree", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                refreshTree();
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

        // Right-click menu
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

        // Determine text based on node type
        String removeText = data.isDirectory ? "Remove Directory from AICode" : "Remove File from AICode";

        JMenuItem removeItem = new JMenuItem(removeText);
        removeItem.setIcon(AllIcons.Actions.Cancel);
        removeItem.addActionListener(actionEvent -> {
            removeNodeContext(node);
        });

        menu.add(removeItem);
        menu.show(tree, e.getX(), e.getY());
    }

    private void removeNodeContext(DefaultMutableTreeNode node) {
        AICodeFileService service = AICodeFileService.getInstance(project);
        List<String> pathsToRemove = new ArrayList<>();
        collectPaths(node, pathsToRemove);

        if (!pathsToRemove.isEmpty()) {
            for (String path : pathsToRemove) {
                service.removeFilePath(path);
            }
        }
    }

    private void collectPaths(DefaultMutableTreeNode node, List<String> collector) {
        Object userObject = node.getUserObject();
        if (userObject instanceof AICodeNodeData) {
            AICodeNodeData data = (AICodeNodeData) userObject;
            // If it's a leaf (file) and has a valid path, add it
            if (!data.isDirectory && data.fullRelativePath != null) {
                collector.add(data.fullRelativePath);
            }
        }

        // Recursively check children
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
        // Run on EDT
        SwingUtilities.invokeLater(() -> {
            // Save expansion state (simple version: list of expanded paths)
            // For now, we will just fully expand or keep simple.
            // Rebuilding tree invalidates objects, so preserving state needs path-based logic.
            // Let's implement a simple rebuild first.

            rootNode.removeAllChildren();

            AICodeFileService service = AICodeFileService.getInstance(project);
            List<String> paths = service.readFilePaths();
            Collections.sort(paths); // Sort to ensure folders come in order

            // Update Root Display
            AICodeNodeData rootData = (AICodeNodeData) rootNode.getUserObject();
            rootData.displayName = project.getName();
            rootData.virtualFile = project.getBaseDir();

            buildTreeStructure(paths, service);

            treeModel.reload();
            TreeUtil.expandAll(tree); // Auto expand all on refresh
        });
    }

    /**
     * Reconstruct the tree from flat paths
     */
    private void buildTreeStructure(List<String> paths, AICodeFileService service) {
        Map<String, DefaultMutableTreeNode> directoryNodes = new HashMap<>();

        for (String path : paths) {
            String[] parts = path.split("/");
            DefaultMutableTreeNode currentNode = rootNode;
            String currentPathAccumulator = "";

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean isLast = (i == parts.length - 1);

                if (!currentPathAccumulator.isEmpty()) {
                    currentPathAccumulator += "/";
                }
                currentPathAccumulator += part;

                if (isLast) {
                    // It's the file itself
                    VirtualFile file = service.getFileFromPath(path);
                    AICodeNodeData fileData = new AICodeNodeData(part, path, false);
                    fileData.virtualFile = file;

                    DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(fileData);
                    currentNode.add(fileNode);
                } else {
                    // It's a directory
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
    public void dispose() {
        // Required by Disposable interface
    }

    /**
     * Data holder for Tree Nodes
     */
    private static class AICodeNodeData {
        String displayName;
        String fullRelativePath;
        boolean isDirectory;
        VirtualFile virtualFile; // Can be null if file deleted

        public AICodeNodeData(String displayName, String fullRelativePath, boolean isDirectory) {
            this.displayName = displayName;
            this.fullRelativePath = fullRelativePath;
            this.isDirectory = isDirectory;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Custom Renderer to mimic Project View
     */
    private static class AICodeTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode)) return;

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof AICodeNodeData) {
                AICodeNodeData data = (AICodeNodeData) userObject;

                // Icon
                if (data.virtualFile != null) {
                    if (data.isDirectory) {
                        setIcon(AllIcons.Nodes.Folder);
                    } else {
                        setIcon(data.virtualFile.getFileType().getIcon());
                    }
                } else {
                    // File missing or virtual file not resolved
                    setIcon(data.isDirectory ? AllIcons.Nodes.Folder : AllIcons.FileTypes.Unknown);
                }

                // Text
                if (data.virtualFile == null && !data.displayName.equals("Project")) {
                    // Missing file/dir
                    append(data.displayName, SimpleTextAttributes.ERROR_ATTRIBUTES);
                    append(" (missing)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                } else {
                    append(data.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }

                // Gray text for relative path hint on leaves (optional, maybe too cluttered)
                // if (!data.isDirectory && selected) {
                //    append("  " + data.fullRelativePath, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                // }
            }
        }
    }
}
