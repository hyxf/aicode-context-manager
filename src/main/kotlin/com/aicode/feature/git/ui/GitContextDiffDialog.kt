package com.aicode.feature.git.ui

import com.aicode.feature.git.service.GitContextDiffService
import com.aicode.feature.git.service.GitContextDiffService.FileDiffResult
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import git4idea.repo.GitRepository
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Action
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class GitContextDiffDialog(
    private val project: Project,
    private val repository: GitRepository,
    private val currentBranch: String,
    branches: List<String>,
    private val paths: List<String>,
    private val diffService: GitContextDiffService = GitContextDiffService(),
) : DialogWrapper(project, true) {
    private val compareBranchBox =
        JComboBox(DefaultComboBoxModel(branches.filter { it != currentBranch }.toTypedArray())).apply {
            isEditable = true
        }
    private val tableModel = DiffTableModel()
    private val table = JBTable(tableModel)
    private val statusLabel = JBLabel("Choose a branch and click Execute.")
    private var comparedBranch: String? = null

    init {
        title = "Compare AICode Context"
        setOKButtonText("Execute")
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction, cancelAction)

    override fun doOKAction() {
        val compareBranch = selectedBranch()
        if (compareBranch.isEmpty() || compareBranch == currentBranch) return
        saveContextDocuments()
        isOKActionEnabled = false
        statusLabel.text = "Comparing..."
        object : Task.Backgroundable(project, "Comparing AICode context files", false) {
            private var results: List<FileDiffResult> = emptyList()
            private var error: String? = null

            override fun run(indicator: ProgressIndicator) {
                try {
                    results =
                        diffService.compare(
                            project,
                            repository,
                            compareBranch,
                            paths,
                        )
                } catch (ex: ProcessCanceledException) {
                    throw ex
                } catch (ex: Exception) {
                    error = ex.message?.takeIf { it.isNotBlank() } ?: "Unexpected Git error"
                }
            }

            override fun onFinished() {
                if (project.isDisposed || this@GitContextDiffDialog.isDisposed) return
                ApplicationManager.getApplication().assertIsDispatchThread()
                isOKActionEnabled = true
                error?.let {
                    statusLabel.text = "Comparison failed: $it"
                    return
                }
                tableModel.setResults(results)
                comparedBranch = compareBranch
                val changedCount = results.count { it.changed }
                statusLabel.text = "Compared ${results.size} files; $changedCount changed."
            }
        }.queue()
    }

    override fun doValidate(): ValidationInfo? {
        val branch = selectedBranch()
        return when {
            branch.isEmpty() -> ValidationInfo("Select or enter a comparison branch.", compareBranchBox)
            branch == currentBranch ->
                ValidationInfo("The comparison branch must differ from the current branch.", compareBranchBox)
            else -> null
        }
    }

    override fun createCenterPanel(): JComponent {
        table.emptyText.text = "No comparison results."
        table.setShowGrid(false)
        table.columnModel.getColumn(0).cellRenderer = PathCellRenderer()
        table.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount != 2) return
                    val viewRow = table.rowAtPoint(event.point)
                    if (viewRow >= 0) openFileDiff(table.convertRowIndexToModel(viewRow))
                }
            }
        )
        table.columnModel.getColumn(1).preferredWidth = 120
        table.columnModel.getColumn(1).maxWidth = 160
        val header =
            FormBuilder.createFormBuilder()
                .addLabeledComponent("Current branch:", JBLabel(currentBranch))
                .addLabeledComponent("Compare branch:", compareBranchBox)
                .panel
        return JPanel(BorderLayout(0, JBUI.scale(10))).apply {
            border = JBUI.Borders.empty(8, 12)
            add(header, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
            preferredSize = Dimension(JBUI.scale(720), JBUI.scale(460))
        }
    }

    private fun selectedBranch() = compareBranchBox.editor.item?.toString()?.trim().orEmpty()

    private fun saveContextDocuments() {
        val projectRoot = project.baseDir ?: return
        val documentManager = FileDocumentManager.getInstance()
        paths.forEach { path ->
            val file = projectRoot.findFileByRelativePath(path) ?: return@forEach
            val document = documentManager.getCachedDocument(file) ?: return@forEach
            if (documentManager.isDocumentUnsaved(document)) documentManager.saveDocument(document)
        }
    }

    private fun openFileDiff(modelRow: Int) {
        val branch = comparedBranch ?: return
        val path = tableModel.getResult(modelRow)?.path ?: return
        object : Task.Backgroundable(project, "Loading file comparison", false) {
            private var branchContent: ByteArray? = null
            private var error: String? = null

            override fun run(indicator: ProgressIndicator) {
                try {
                    branchContent = diffService.readFileAtBranch(project, repository, branch, path)
                } catch (ex: ProcessCanceledException) {
                    throw ex
                } catch (ex: Exception) {
                    error = ex.message?.takeIf { it.isNotBlank() } ?: "Unexpected Git error"
                }
            }

            override fun onSuccess() {
                if (project.isDisposed || this@GitContextDiffDialog.isDisposed) return
                error?.let {
                    statusLabel.text = "Failed to load file comparison: $it"
                    return
                }
                val currentFile = project.baseDir?.findFileByRelativePath(path)
                val contentFactory = DiffContentFactory.getInstance()
                val branchDiffContent =
                    branchContent?.let {
                        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(path)
                        contentFactory.createFromBytes(project, it, fileType, path.substringAfterLast('/'))
                    } ?: contentFactory.createEmpty()
                val currentDiffContent =
                    currentFile?.let { contentFactory.create(project, it) }
                        ?: contentFactory.createEmpty()
                DiffManager.getInstance()
                    .showDiff(
                        project,
                        SimpleDiffRequest(
                            path,
                            branchDiffContent,
                            currentDiffContent,
                            branch,
                            "$currentBranch (Working Tree)",
                        ),
                    )
            }
        }.queue()
    }

    private class DiffTableModel : AbstractTableModel() {
        private var results: List<FileDiffResult> = emptyList()

        fun setResults(newResults: List<FileDiffResult>) {
            results = newResults
            fireTableDataChanged()
        }

        fun getResult(row: Int) = results.getOrNull(row)

        override fun getRowCount() = results.size

        override fun getColumnCount() = 2

        override fun getColumnName(column: Int) = if (column == 0) "File Path" else "Changed"

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any =
            if (columnIndex == 0) results[rowIndex].path
            else if (results[rowIndex].changed) "Changed" else "No changes"
    }

    private class PathCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            super.getTableCellRendererComponent(
                table,
                value,
                isSelected,
                hasFocus,
                row,
                column,
            )
            val path = value?.toString().orEmpty()
            toolTipText = path
            text = shortenPath(path, table.columnModel.getColumn(column).width - JBUI.scale(12))
            return this
        }

        private fun shortenPath(path: String, availableWidth: Int): String {
            val metrics = getFontMetrics(font)
            if (path.isEmpty() || availableWidth <= 0 || metrics.stringWidth(path) <= availableWidth)
                return path
            val separators = path.indices.filter { path[it] == '/' }
            val tailStart =
                when {
                    separators.size >= 2 -> separators[separators.lastIndex - 1] + 1
                    separators.isNotEmpty() -> separators.last() + 1
                    else -> 0
                }
            var tail = path.substring(tailStart)
            var candidate = "…/$tail"
            if (metrics.stringWidth(candidate) > availableWidth) {
                tail = path.substringAfterLast('/')
                candidate = "…/$tail"
            }
            if (metrics.stringWidth(candidate) > availableWidth) return candidate

            val prefixSource = path.substring(0, tailStart)
            var prefixLength = 0
            while (prefixLength < prefixSource.length) {
                val next = prefixSource.substring(0, prefixLength + 1) + candidate
                if (metrics.stringWidth(next) > availableWidth) break
                prefixLength++
            }
            return prefixSource.substring(0, prefixLength) + candidate
        }
    }
}
