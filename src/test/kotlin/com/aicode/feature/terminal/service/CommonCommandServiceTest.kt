package com.aicode.feature.terminal.service

import com.aicode.feature.terminal.data.DefaultCommonCommands
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CommonCommandServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `creates default configuration when missing`() {
        val path = temporaryFolder.root.toPath().resolve("commoncmd.json")

        assertEquals(DefaultCommonCommands.commands, CommonCommandService(path).getCommands())
        assertTrue(Files.readString(path).contains("git status"))
    }

    @Test
    fun `normalizes blank and duplicate commands when saving`() {
        val path = temporaryFolder.root.toPath().resolve("nested/commoncmd.json")
        val service = CommonCommandService(path)

        service.saveCommands(listOf(" git status ", "", "git status", "git log --oneline"))

        assertEquals(listOf("git status", "git log --oneline"), service.getCommands())
    }

    @Test
    fun `reports invalid json`() {
        val path = temporaryFolder.newFile("commoncmd.json").toPath()
        Files.writeString(path, "not-json")

        assertThrows(IllegalStateException::class.java) { CommonCommandService(path).getCommands() }
    }
}
