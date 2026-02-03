package com.aicode.ui;

import com.aicode.service.AICodeFileService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
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

                // Icon Logic
                if (file != null) {
                    setIcon(file.getFileType().getIcon());
                } else {
                    // Show warning icon if file is missing
                    setIcon(AllIcons.General.Warning);
                }

                // Text Logic
                if (file == null) {
                    append(value.getPath(), SimpleTextAttributes.ERROR_ATTRIBUTES);
                    append(" (missing)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                } else {
                    // Show full relative path as requested
                    append(value.getPath(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

                    // Show module name at the end in gray
                    if (value.getModuleName() != null) {
                        append(" [" + value.getModuleName() + "]", SimpleTextAttributes.GRAY_ATTRIBUTES);
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

        // Use system icons: AllIcons.General.Settings
        actionGroup.add(new AnAction("Open Configuration", "Open .aicode.json configuration file", AllIcons.General.Settings) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                openAICodeFile();
            }
        });

        // Use system icons: AllIcons.Actions.Refresh
        actionGroup.add(new AnAction("Refresh", "Refresh file list", AllIcons.Actions.Refresh) {
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
        });
        menu.add(removeItem);
        menu.show(fileList, e.getX(), e.getY());
    }

    public void refreshList() {
        // Run on EDT
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
        // Required by Disposable interface
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
