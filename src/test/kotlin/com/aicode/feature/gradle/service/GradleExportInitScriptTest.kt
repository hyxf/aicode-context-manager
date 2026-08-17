package com.aicode.feature.gradle.service

import com.aicode.feature.gradle.model.ExportOptions
import com.aicode.feature.gradle.model.ExportScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class GradleExportInitScriptTest {
    @Test
    fun resolvesAndroidVariantsAndBuildscriptWithoutReadingGradleCache() {
        val script = GradleExportInitScript.create(
            ExportOptions(Path.of("repository"), ExportScope.COMPILE_AND_RUNTIME, true, true),
            Path.of("result.json"),
        )

        assertTrue(script.contains("endsWith('runtimeclasspath')"))
        assertTrue(script.contains("endsWith('compileclasspath')"))
        assertTrue(script.contains("candidateProject.buildscript.configurations"))
        assertTrue(script.contains("ModuleComponentIdentifier"))
        assertFalse(script.contains("caches/modules-2"))
    }

    @Test
    fun protectsExistingArtifactsAndWritesIncrementalResults() {
        val script = GradleExportInitScript.create(
            ExportOptions(Path.of("repository"), ExportScope.RUNTIME, false, false),
            Path.of("result.json"),
        )

        assertTrue(script.contains("Existing file has different content"))
        assertTrue(script.contains("StandardCopyOption.ATOMIC_MOVE"))
        assertTrue(script.contains("id.group.replace('.', '/')"))
        assertTrue(script.contains("aicodeWriteResult(false)"))
    }
}
