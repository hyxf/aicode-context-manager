package com.aicode.feature.terminal.service

import com.aicode.feature.terminal.data.DefaultCommonCommands
import com.aicode.feature.terminal.model.CommonCommandConfig
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class CommonCommandService(
    private val configPath: Path = defaultConfigPath(),
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Synchronized
    fun getCommands(): List<String> {
        if (!Files.exists(configPath)) {
            saveCommands(DefaultCommonCommands.commands)
            return DefaultCommonCommands.commands
        }
        try {
            val content = Files.readString(configPath, StandardCharsets.UTF_8)
            if (content.isBlank()) return emptyList()
            val root = JsonParser.parseString(content)
            if (!root.isJsonObject) throw IllegalStateException("Common command configuration must be a JSON object.")
            val commands = root.asJsonObject.get("commands") ?: return emptyList()
            if (!commands.isJsonArray) {
                throw IllegalStateException("Common command configuration field 'commands' must be an array.")
            }
            return normalize(commands.asJsonArray.mapNotNull { element ->
                element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            })
        } catch (ex: JsonParseException) {
            throw IllegalStateException("Invalid JSON in $configPath: ${ex.message}", ex)
        } catch (ex: Exception) {
            if (ex is IllegalStateException) throw ex
            throw IllegalStateException("Failed to read $configPath: ${ex.message}", ex)
        }
    }

    @Synchronized
    fun saveCommands(commands: List<String>) {
        val parent = configPath.toAbsolutePath().parent
        try {
            if (parent != null) Files.createDirectories(parent)
            val temporaryFile = Files.createTempFile(parent, "commoncmd", ".tmp")
            try {
                Files.writeString(
                    temporaryFile,
                    gson.toJson(CommonCommandConfig(normalize(commands).toMutableList())),
                    StandardCharsets.UTF_8,
                )
                try {
                    Files.move(temporaryFile, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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

    @Synchronized
    fun addCommand(command: String): Boolean {
        val normalized = command.trim()
        if (normalized.isEmpty()) return false
        val commands = getCommands()
        if (normalized in commands) return false
        saveCommands(commands + normalized)
        return true
    }

    private fun normalize(commands: List<String>): List<String> =
        commands.map(String::trim).filter(String::isNotEmpty).distinct()

    companion object {
        fun getInstance(): CommonCommandService =
            ApplicationManager.getApplication().getService(CommonCommandService::class.java)

        fun defaultConfigPath(): Path = Path.of(System.getProperty("user.home"), ".aicode", "commoncmd.json")
    }
}
