package com.aicode.feature.git.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class CommonCommitMessageServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing configuration returns an empty list`() {
        val path = temporaryFolder.root.toPath().resolve("nested/gitmessage.json")

        assertTrue(CommonCommitMessageService(path).getMessages().isEmpty())
        assertFalse(Files.exists(path))
    }

    @Test
    fun `messages are normalized and persisted as json`() {
        val path = temporaryFolder.root.toPath().resolve(".aicode/gitmessage.json")
        val service = CommonCommitMessageService(path)

        service.saveMessages(listOf(" feat: add feature ", "fix: issue", "feat: add feature", ""))

        assertEquals(listOf("feat: add feature", "fix: issue"), service.getMessages())
        val json = Files.readString(path, StandardCharsets.UTF_8)
        assertTrue(json.contains("\"messages\""))
        assertTrue(json.contains("feat: add feature"))
    }

    @Test(expected = IllegalStateException::class)
    fun `malformed json reports an error`() {
        val path = temporaryFolder.root.toPath().resolve("gitmessage.json")
        Files.writeString(path, "{invalid", StandardCharsets.UTF_8)

        CommonCommitMessageService(path).getMessages()
    }

    @Test
    fun `saving an empty list keeps a valid empty configuration`() {
        val path = temporaryFolder.root.toPath().resolve("gitmessage.json")
        val service = CommonCommitMessageService(path)

        service.saveMessages(emptyList())

        assertEquals(emptyList<String>(), service.getMessages())
    }
}
