import os
import sys

# 1. 修改 plugin.xml
# 变更：将 group 的 add-to-group 修改为 CutCopyPasteGroup，anchor="last"
PLUGIN_XML_CONTENT = r"""<idea-plugin>
    <id>com.aicode.context-manager</id>
    <name>AICode Context Manager</name>
    <vendor email="support@aicode.com" url="https://aicode.com">AICode</vendor>

    <description><![CDATA[
    <h2>AICode Context Manager</h2>
    <p>Manage code context files for AI assistance with one-click Markdown export.</p>
    <br/>
    <h3>Features:</h3>
    <ul>
      <li>Add/Remove files to AI context via right-click menu</li>
      <li>Visualize context files in Tool Window</li>
      <li>Support multi-module projects</li>
      <li>Auto-sync file changes (rename, move, delete)</li>
      <li>Export all context files as Markdown code package</li>
      <li>Undo support for all operations</li>
    </ul>
    ]]></description>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.java</depends>

    <extensions defaultExtensionNs="com.intellij">
        <!-- Tool Window -->
        <toolWindow
                id="AICode Context"
                anchor="right"
                icon="/icons/aicode.svg"
                factoryClass="com.aicode.ui.AICodeToolWindowFactory"/>

        <!-- Project Service -->
        <projectService
                serviceImplementation="com.aicode.service.AICodeFileService"/>
    </extensions>

    <actions>
        <!-- Project View Context Menu Group -->
        <!-- Moved to CutCopyPasteGroup as requested -->
        <group id="AICodeGroup" text="AICode" popup="true">
            <add-to-group group-id="CutCopyPasteGroup" anchor="last"/>

            <action id="com.aicode.action.AddToAICodeAction"
                    class="com.aicode.action.AddToAICodeAction"
                    text="Add to AICode"
                    description="Add file to AICode context">
            </action>

            <action id="com.aicode.action.RemoveFromAICodeAction"
                    class="com.aicode.action.RemoveFromAICodeAction"
                    text="Remove from AICode"
                    description="Remove file from AICode context">
            </action>

            <action id="com.aicode.action.CopyMarkdownAction"
                    class="com.aicode.action.CopyMarkdownAction"
                    text="Copy as Markdown"
                    description="Export AICode context as Markdown">
            </action>
        </group>
    </actions>

    <projectListeners>
        <listener class="com.aicode.listener.AICodeFileListener"
                  topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"/>
    </projectListeners>
</idea-plugin>
"""

# 2. 修改 AICodePanel.java
# 变更：
# - 导入 MarkdownBuilder, ClipboardService, Notification 相关类
# - 在 createToolbar 中添加 Copy Action
AICODE_PANEL_CONTENT = r"""package com.aicode.ui;

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

        // 1. Open Config
        actionGroup.add(new AnAction("Open Configuration", "Open .aicode.json configuration file", AllIcons.General.Settings) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                openAICodeFile();
            }
        });

        // 2. Copy as Markdown (NEW)
        actionGroup.add(new AnAction("Copy as Markdown", "Export all context files as Markdown to clipboard", AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                copyMarkdownToClipboard();
            }
        });

        actionGroup.addSeparator();

        // 3. Expand All / Collapse All
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

        // 4. Refresh
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

    private void copyMarkdownToClipboard() {
        AICodeFileService service = AICodeFileService.getInstance(project);
        List<String> filePaths = service.readFilePaths();

        if (filePaths.isEmpty()) {
            showNotification("No files in AICode context", NotificationType.WARNING);
            return;
        }

        try {
            // Run expensive operation in background (though reading small files is fast)
            // For simplicity in UI action, direct call is acceptable if files aren't huge.
            String markdown = MarkdownBuilder.buildMarkdown(
                    project,
                    filePaths,
                    service::getFileFromPath
            );

            ClipboardService.copyToClipboard(markdown);

            String message = String.format("AICode Markdown copied to clipboard (%d files)", filePaths.size());
            showNotification(message, NotificationType.INFORMATION);

        } catch (Exception ex) {
            showNotification("Failed to export Markdown: " + ex.getMessage(), NotificationType.ERROR);
        }
    }

    private void showNotification(String content, NotificationType type) {
        Notification notification = new Notification(
                "AICode",
                "AICode Context Manager",
                content,
                type
        );
        Notifications.Bus.notify(notification, project);
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
        VirtualFile virtualFile;

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
                    setIcon(data.isDirectory ? AllIcons.Nodes.Folder : AllIcons.FileTypes.Unknown);
                }

                // Text
                if (data.virtualFile == null && !data.displayName.equals("Project")) {
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

UPDATES = {
    "src/main/resources/META-INF/plugin.xml": PLUGIN_XML_CONTENT,
    "src/main/java/com/aicode/ui/AICodePanel.java": AICODE_PANEL_CONTENT
}

def main():
    for relative_path, content in UPDATES.items():
        # 路径跨平台处理
        full_path = os.path.join(*relative_path.split("/"))

        # 确保目录存在
        if not os.path.exists(os.path.dirname(full_path)):
            print(f"错误: 找不到目录 {os.path.dirname(full_path)}")
            continue

        try:
            with open(full_path, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"成功更新: {full_path}")
        except IOError as e:
            print(f"写入失败 {full_path}: {e}")

    print("\n完成。")
    print("1. 右键菜单 AICode 组已移动到 'Cut/Copy/Paste' 组的底部。")
    print("2. 工具栏增加了 'Copy as Markdown' 图标。")

if __name__ == "__main__":
    main()