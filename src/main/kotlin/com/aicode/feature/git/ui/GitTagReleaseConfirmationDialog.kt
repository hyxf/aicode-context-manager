package com.aicode.feature.git.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent

class GitTagReleaseConfirmationDialog(project: Project, message: String) :
    DialogWrapper(project, true) {
    private val messageArea =
        JBTextArea(message).apply {
            isEditable = false
            isFocusable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(8, 12, 8, 12)
        }

    init {
        title = "Confirm Git Tag Release"
        setOKButtonText("Confirm Release")
        init()
    }

    override fun createCenterPanel(): JComponent =
        messageArea.apply {
            // Keep all release details and warnings visible without requiring scrolling.
            preferredSize = Dimension(JBUI.scale(680), JBUI.scale(380))
        }
}
