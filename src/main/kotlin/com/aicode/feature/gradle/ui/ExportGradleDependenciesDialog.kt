package com.aicode.feature.gradle.ui

import com.aicode.feature.gradle.model.ExportScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.ButtonGroup
import javax.swing.JComponent

class ExportGradleDependenciesDialog(project: Project) : DialogWrapper(project, true) {
    private val runtime = JBRadioButton("Runtime", true)
    private val compileRuntime = JBRadioButton("Compile + Runtime")
    private val all = JBRadioButton("All resolvable configurations")
    private val includeTests = JBCheckBox("Include test configurations", true)
    private val includeBuildscript =
        JBCheckBox("Include buildscript classpath (Android/Gradle plugins)", true)

    init {
        ButtonGroup().also {
            it.add(runtime)
            it.add(compileRuntime)
            it.add(all)
        }
        title = "Export Gradle Dependencies"
        setOKButtonText("Select Repository Directory")
        init()
    }

    fun selectedScope(): ExportScope = when {
        all.isSelected -> ExportScope.ALL
        compileRuntime.isSelected -> ExportScope.COMPILE_AND_RUNTIME
        else -> ExportScope.RUNTIME
    }

    fun includeTests(): Boolean = includeTests.isSelected

    fun includeBuildscriptClasspath(): Boolean = includeBuildscript.isSelected

    override fun createCenterPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addComponent(JBLabel("Export dependencies resolved by Gradle for:"))
            .addVerticalGap(8)
            .addComponent(runtime)
            .addComponent(compileRuntime)
            .addComponent(all)
            .addVerticalGap(8)
            .addComponent(includeTests)
            .addComponent(includeBuildscript)
            .addVerticalGap(8)
            .addComponent(JBLabel("Project dependencies and local file dependencies are not exported."))
            .panel.apply {
                border = JBUI.Borders.empty(8, 12)
                preferredSize = JBUI.size(500, 210)
            }
}
