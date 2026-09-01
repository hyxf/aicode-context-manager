package com.aicode.feature.git.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

class CommonCommitMessageDialog(
    project: Project,
    private val messages: List<String>,
) : DialogWrapper(project, true) {
    private val listModel = DefaultListModel<String>()
    private val messageList = JBList(listModel)
    private val searchField = SearchTextField(false)

    val selectedMessage: String?
        get() = messageList.selectedValue

    init {
        title = "Select Common Commit Message"
        setOKButtonText("Select")
        searchField.textEditor.emptyText.text = "Search commit messages"
        searchField.textEditor.document.addDocumentListener(
            object : DocumentAdapter() {
                override fun textChanged(event: DocumentEvent) = refreshMessages()
            }
        )
        messageList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        messageList.emptyText.text = "No commit messages match your search."
        messageList.addListSelectionListener { isOKActionEnabled = selectedMessage != null }
        messageList.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount != 2) return
                    val index = messageList.locationToIndex(event.point)
                    val bounds = index.takeIf { it >= 0 }?.let { messageList.getCellBounds(it, it) }
                    if (bounds?.contains(event.point) == true) doOKAction()
                }
            }
        )
        refreshMessages()
        init()
    }

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            preferredSize = Dimension(JBUI.scale(520), JBUI.scale(300))
            add(searchField, BorderLayout.NORTH)
            add(JBScrollPane(messageList), BorderLayout.CENTER)
        }

    override fun getPreferredFocusedComponent(): JComponent = searchField.textEditor

    private fun refreshMessages() {
        val keyword = searchField.text.trim()
        val filtered =
            if (keyword.isEmpty()) messages
            else messages.filter { it.contains(keyword, ignoreCase = true) }
        listModel.clear()
        filtered.forEach(listModel::addElement)
        if (listModel.size > 0) messageList.selectedIndex = 0
        isOKActionEnabled = selectedMessage != null
    }
}
