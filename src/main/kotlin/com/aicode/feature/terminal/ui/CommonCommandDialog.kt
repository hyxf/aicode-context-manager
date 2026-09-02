package com.aicode.feature.terminal.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

object CommonCommandDialog {
    fun showAdd(initialValue: String = ""): String? =
        CommandEditorDialog("Add Command", initialValue).showAndGetValue()

    fun showEdit(initialValue: String): String? =
        CommandEditorDialog("Edit Command", initialValue).showAndGetValue()
}

private class CommandEditorDialog(
    title: String,
    initialValue: String,
) : DialogWrapper(true) {
    private val commandEditor =
        JBTextArea(initialValue, 6, 56).apply {
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(8)
        }

    init {
        this.title = title
        setOKButtonText("Save")
        init()
    }

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout()).apply {
            minimumSize = Dimension(JBUI.scale(420), JBUI.scale(140))
            preferredSize = Dimension(JBUI.scale(520), JBUI.scale(180))
            maximumSize = Dimension(JBUI.scale(640), JBUI.scale(260))
            add(
                JBScrollPane(commandEditor).apply {
                    border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
                },
                BorderLayout.CENTER,
            )
        }

    override fun getPreferredFocusedComponent(): JComponent = commandEditor

    override fun doValidate(): ValidationInfo? =
        if (commandEditor.text.isBlank()) ValidationInfo("Command is required.", commandEditor) else null

    fun showAndGetValue(): String? {
        if (!showAndGet()) return null
        return commandEditor.text.trim().takeIf(String::isNotEmpty)
    }
}
