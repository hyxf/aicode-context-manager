package com.aicode.feature.git.service

import com.aicode.feature.git.model.GitMessageConfig
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.intellij.openapi.application.ApplicationManager
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class CommonCommitMessageService(
    private val configPath: Path = defaultConfigPath(),
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Synchronized
    fun getMessages(): List<String> {
        if (!Files.exists(configPath)) return emptyList()
        try {
            val content = Files.readString(configPath, StandardCharsets.UTF_8)
            if (content.isBlank()) return emptyList()
            val config = gson.fromJson(content, GitMessageConfig::class.java)
                ?: throw IllegalStateException("Commit message configuration is empty.")
            return normalize(config.messages)
        } catch (ex: JsonParseException) {
            throw IllegalStateException("Invalid JSON in $configPath: ${ex.message}", ex)
        } catch (ex: Exception) {
            if (ex is IllegalStateException) throw ex
            throw IllegalStateException("Failed to read $configPath: ${ex.message}", ex)
        }
    }

    @Synchronized
    fun saveMessages(messages: List<String>) {
        val normalized = normalize(messages)
        val parent = configPath.toAbsolutePath().parent
        try {
            if (parent != null) Files.createDirectories(parent)
            val temporaryFile = Files.createTempFile(parent, "gitmessage", ".tmp")
            try {
                Files.writeString(
                    temporaryFile,
                    gson.toJson(GitMessageConfig(normalized.toMutableList())),
                    StandardCharsets.UTF_8,
                )
                try {
                    Files.move(
                        temporaryFile,
                        configPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporaryFile, configPath, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporaryFile)
            }
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to write $configPath: ${ex.message}", ex)
        }
    }

    private fun normalize(messages: List<String>?): List<String> =
        messages.orEmpty().map(String::trim).filter(String::isNotEmpty).distinct()

    companion object {
        fun getInstance(): CommonCommitMessageService =
            ApplicationManager.getApplication().getService(CommonCommitMessageService::class.java)

        fun defaultConfigPath(): Path =
            Path.of(System.getProperty("user.home"), ".aicode", "gitmessage.json")
    }
}
