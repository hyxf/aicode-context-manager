package com.aicode.feature.git.settings

import com.aicode.feature.git.service.CommonCommitMessageService
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.Messages
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

class CommonCommitMessageConfigurable : Configurable {
    private val service = CommonCommitMessageService.getInstance()
    private var savedMessages: List<String> = emptyList()
    private val workingMessages = mutableListOf<String>()
    private var panel: JPanel? = null
    private var searchField: SearchTextField? = null
    private var messageList: JBList<String>? = null
    private var listModel: DefaultListModel<String>? = null

    override fun getDisplayName() = "Common Commit Messages"

    override fun createComponent(): JComponent {
        val model = DefaultListModel<String>()
        val list = JBList(model).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            fixedCellHeight = JBUI.scale(30)
            cellRenderer =
                object : ColoredListCellRenderer<String>() {
                    override fun customizeCellRenderer(
                        list: JList<out String>,
                        value: String,
                        index: Int,
                        selected: Boolean,
                        hasFocus: Boolean,
                    ) {
                        border = JBUI.Borders.empty(0, 10)
                        append(value, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    }
                }
            emptyText.text = "No commit messages."
        }
        val search = SearchTextField(false).apply {
            textEditor.emptyText.text = "Search commit messages"
            textEditor.document.addDocumentListener(
                object : DocumentAdapter() {
                    override fun textChanged(event: DocumentEvent) = refreshList()
                }
            )
        }
        val toolbarPanel =
            ToolbarDecorator.createDecorator(list)
                .setAddAction { addMessage() }
                .setEditAction { editMessage() }
                .setRemoveAction { removeMessage() }
                .disableUpDownActions()
                .createPanel()
        listModel = model
        messageList = list
        searchField = search
        panel =
            JPanel(BorderLayout(0, JBUI.scale(8))).apply {
                add(search, BorderLayout.NORTH)
                add(toolbarPanel, BorderLayout.CENTER)
            }
        reset()
        return panel!!
    }

    override fun isModified(): Boolean = workingMessages != savedMessages

    override fun apply() {
        try {
            service.saveMessages(workingMessages)
            savedMessages = workingMessages.toList()
        } catch (ex: IllegalStateException) {
            throw ConfigurationException(ex.message ?: "Failed to save commit messages.")
        }
    }

    override fun reset() {
        savedMessages =
            try {
                service.getMessages()
            } catch (ex: IllegalStateException) {
                Messages.showErrorDialog(
                    ex.message ?: "Failed to load commit messages.",
                    "Common Commit Messages",
                )
                emptyList()
            }
        workingMessages.clear()
        workingMessages.addAll(savedMessages)
        refreshList()
    }

    override fun disposeUIResources() {
        panel = null
        searchField = null
        messageList = null
        listModel = null
        workingMessages.clear()
        savedMessages = emptyList()
    }

    private fun addMessage() {
        val value = promptForMessage("Add Commit Message", null) ?: return
        if (!validateUnique(value, null)) return
        workingMessages.add(value)
        refreshList(value)
    }

    private fun editMessage() {
        val oldValue = messageList?.selectedValue ?: return
        val value = promptForMessage("Edit Commit Message", oldValue) ?: return
        if (!validateUnique(value, oldValue)) return
        val index = workingMessages.indexOf(oldValue)
        if (index >= 0) workingMessages[index] = value
        refreshList(value)
    }

    private fun removeMessage() {
        val selected = messageList?.selectedValue ?: return
        workingMessages.remove(selected)
        refreshList()
    }

    private fun promptForMessage(title: String, initialValue: String?): String? =
        Messages.showInputDialog(
            "Enter a commit message:",
            title,
            null,
            initialValue,
            null,
        )?.trim()?.takeIf(String::isNotEmpty)

    private fun validateUnique(value: String, oldValue: String?): Boolean {
        if (value != oldValue && value in workingMessages) {
            Messages.showErrorDialog("This commit message already exists.", "Duplicate Commit Message")
            return false
        }
        return true
    }

    private fun refreshList(selectedValue: String? = null) {
        val model = listModel ?: return
        val keyword = searchField?.text.orEmpty().trim()
        val filtered =
            if (keyword.isEmpty()) workingMessages
            else workingMessages.filter { it.contains(keyword, ignoreCase = true) }
        model.clear()
        filtered.forEach(model::addElement)
        val selection = selectedValue?.let(filtered::indexOf) ?: -1
        if (selection >= 0) messageList?.selectedIndex = selection
    }
}
