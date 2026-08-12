package com.aicode.ui;

import com.aicode.service.GitTagVersionService.VersionCandidates;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Native release version chooser. Selecting a version does not modify Git state. */
public final class GitTagVersionDialog extends DialogWrapper {
    private final VersionCandidates candidates;
    private final JBRadioButton majorButton;
    private final JBRadioButton minorButton;
    private final JBRadioButton patchButton;

    public GitTagVersionDialog(@NotNull Project project, @NotNull VersionCandidates candidates) {
        super(project, true);
        this.candidates = candidates;
        majorButton = new JBRadioButton(candidates.major().toTag() + "  —  Major (breaking changes)");
        minorButton = new JBRadioButton(candidates.minor().toTag() + "  —  Minor (new functionality)");
        patchButton = new JBRadioButton(candidates.patch().toTag() + "  —  Patch (bug fixes)", true);

        ButtonGroup group = new ButtonGroup();
        group.add(majorButton);
        group.add(minorButton);
        group.add(patchButton);

        setTitle("Create Release Tag");
        setOKButtonText("Continue");
        init();
    }

    public @NotNull String getSelectedTag() {
        if (majorButton.isSelected()) {
            return candidates.major().toTag();
        }
        if (minorButton.isSelected()) {
            return candidates.minor().toTag();
        }
        return candidates.patch().toTag();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("Choose the semantic version increment for the new Git tag."))
                .addVerticalGap(12)
                .addComponent(new JBLabel("Current version:  " + candidates.current().toTag()))
                .addVerticalGap(12)
                .addComponent(new TitledSeparator("Next version"))
                .addVerticalGap(6)
                .addComponent(majorButton)
                .addVerticalGap(4)
                .addComponent(minorButton)
                .addVerticalGap(4)
                .addComponent(patchButton)
                .getPanel();
        panel.setBorder(JBUI.Borders.empty(8, 12, 4, 12));
        panel.setPreferredSize(JBUI.size(520, 210));
        return panel;
    }
}
