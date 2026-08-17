package com.aicode.feature.gradle.service

import com.aicode.feature.gradle.model.ExportScope
import java.util.Locale

/** Mirrors Gradle source-set and Android variant classpath naming without resolving dependencies itself. */
object GradleConfigurationSelector {
    fun isSelected(name: String, resolvable: Boolean, scope: ExportScope, includeTests: Boolean): Boolean {
        if (!resolvable) return false
        val normalized = name.lowercase(Locale.ROOT)
        if (!includeTests && isTestConfiguration(normalized)) return false
        if (scope == ExportScope.ALL) return true
        val runtime = normalized == "runtimeclasspath" || normalized.endsWith("runtimeclasspath")
        if (scope == ExportScope.RUNTIME) return runtime
        return runtime || normalized == "compileclasspath" || normalized.endsWith("compileclasspath")
    }

    private fun isTestConfiguration(name: String): Boolean =
        name.contains("test") || name.contains("androidtest")
}
