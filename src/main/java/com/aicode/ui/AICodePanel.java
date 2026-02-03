package com.aicode.ui;

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
