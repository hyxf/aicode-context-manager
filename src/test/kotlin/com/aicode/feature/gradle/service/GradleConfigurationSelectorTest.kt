package com.aicode.feature.gradle.service

import com.aicode.feature.gradle.model.ExportScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleConfigurationSelectorTest {
    @Test
    fun selectsAndroidBuildVariantClasspaths() {
        assertTrue(GradleConfigurationSelector.isSelected("debugRuntimeClasspath", true, ExportScope.RUNTIME, false))
        assertTrue(GradleConfigurationSelector.isSelected("freeReleaseCompileClasspath", true, ExportScope.COMPILE_AND_RUNTIME, false))
        assertFalse(GradleConfigurationSelector.isSelected("debugCompileClasspath", true, ExportScope.RUNTIME, false))
    }

    @Test
    fun handlesAndroidAndUnitTestVariants() {
        assertFalse(GradleConfigurationSelector.isSelected("debugAndroidTestRuntimeClasspath", true, ExportScope.RUNTIME, false))
        assertFalse(GradleConfigurationSelector.isSelected("debugUnitTestCompileClasspath", true, ExportScope.COMPILE_AND_RUNTIME, false))
        assertTrue(GradleConfigurationSelector.isSelected("debugAndroidTestRuntimeClasspath", true, ExportScope.RUNTIME, true))
    }

    @Test
    fun neverSelectsNonResolvableConfigurations() {
        assertFalse(GradleConfigurationSelector.isSelected("releaseRuntimeClasspath", false, ExportScope.ALL, true))
    }
}
