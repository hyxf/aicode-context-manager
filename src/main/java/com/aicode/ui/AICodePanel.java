package com.aicode.ui;

import com.aicode.service.AICodeFileService;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel for AICode Tool Window
 */
public class AICodePanel extends JPanel {
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
        refreshList();
    }

    private void setupUI() {
        // Setup list renderer
        fileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FileListItem) {
                    FileListItem item = (FileListItem) value;
                    setText(item.getDisplayText());
                }
                return this;
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(fileList);
        add(scrollPane, BorderLayout.CENTER);

        // Add toolbar
        add(createToolbar(), BorderLayout.NORTH);
    }

    private JComponent createToolbar() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        
        actionGroup.add(new AnAction("Open .aicode.json", "Open AICode configuration file", null) {
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
        if (item == null || item.getFile() == null) {
            return;
        }

        JPopupMenu menu = new JPopupMenu();
        JMenuItem removeItem = new JMenuItem("Remove from AICode");
        removeItem.addActionListener(actionEvent -> {
            AICodeFileService service = AICodeFileService.getInstance(project);
            service.removeFile(item.getFile());
            refreshList();
        });
        menu.add(removeItem);
        menu.show(fileList, e.getX(), e.getY());
    }

    public void refreshList() {
        listModel.clear();
        
        AICodeFileService service = AICodeFileService.getInstance(project);
        List<String> paths = service.readFilePaths();

        for (String path : paths) {
            VirtualFile file = service.getFileFromPath(path);
            String moduleName = getModuleName(file);
            listModel.addElement(new FileListItem(path, file, moduleName));
        }
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

        public String getDisplayText() {
            if (file == null) {
                return "⚠️ " + path + " (missing)";
            }
            if (moduleName != null) {
                return "[" + moduleName + "] " + file.getName();
            }
            return file.getName();
        }
    }
}
