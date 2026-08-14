package com.aicode.feature.git.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Path

class GlobalGitIgnoreServiceTest {
    @Test
    fun `resolves an absolute configured path`() {
        val path = GlobalGitIgnoreService.resolveConfiguredEntry("file:/home/test/.gitconfig\t/opt/git/ignore")

        assertEquals(Path.of("/opt/git/ignore"), path)
    }

    @Test
    fun `resolves a relative configured path from the config file directory`() {
        val path = GlobalGitIgnoreService.resolveConfiguredEntry("file:/home/test/.gitconfig\tconfig/ignore")

        assertEquals(Path.of("/home/test/config/ignore"), path)
    }

    @Test
    fun `uses XDG config home for the Git default`() {
        val path = GlobalGitIgnoreService.defaultPath(mapOf("XDG_CONFIG_HOME" to "/custom/xdg"), "/home/test")

        assertEquals(Path.of("/custom/xdg/git/ignore"), path)
    }

    @Test
    fun `uses home config directory when XDG config home is absent`() {
        val path = GlobalGitIgnoreService.defaultPath(mapOf("HOME" to "/home/test"), null)

        assertEquals(Path.of("/home/test/.config/git/ignore"), path)
    }

    @Test
    fun `treats an explicitly empty configured path as unconfigured`() {
        assertNull(GlobalGitIgnoreService.resolveConfiguredEntry("file:/home/test/.gitconfig\t"))
    }
}
