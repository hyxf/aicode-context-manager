package com.aicode.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/** Lets the user review and adjust all content before CHANGELOG.md is written. */
public final class ChangelogPreviewDialog extends DialogWrapper {
    private final JBTextArea editor;
    private final boolean replacingUnmanagedFile;

    public ChangelogPreviewDialog(
            @NotNull Project project,
            @NotNull String content,
            boolean replacingUnmanagedFile
    ) {
        super(project);
        this.replacingUnmanagedFile = replacingUnmanagedFile;
        editor = new JBTextArea(content);
        editor.setLineWrap(false);
        setTitle("Preview CHANGELOG.md");
        setOKButtonText(replacingUnmanagedFile ? "Replace File" : "Write File");
        init();
    }

    public @NotNull String getContent() {
        return editor.getText();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        if (replacingUnmanagedFile) {
            panel.add(new JBLabel(
                    "This existing file has no AICode markers. Confirming will replace its entire content."
            ), BorderLayout.NORTH);
        }
        JBScrollPane scrollPane = new JBScrollPane(editor);
        scrollPane.setPreferredSize(new Dimension(780, 560));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }
}
