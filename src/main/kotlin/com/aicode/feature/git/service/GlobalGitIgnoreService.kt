package com.aicode.feature.git.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import java.io.File
import java.nio.file.Path

class GlobalGitIgnoreService {
    @Throws(VcsException::class)
    fun locate(project: Project): Path {
        val baseDir = project.basePath?.let(::File)
            ?: throw VcsException("The project base directory is unavailable.")
        val handler = GitLineHandler(project, baseDir, GitCommand.CONFIG)
        handler.addParameters("--global", "--show-origin", "--type=path", "--get", CONFIG_KEY)
        val result = Git.getInstance().runCommand(handler)

        if (result.success()) {
            val entry = result.output.firstOrNull { it.isNotBlank() }
                ?: return defaultPath(environment(), System.getProperty("user.home"))
            return resolveConfiguredEntry(entry)
                ?: defaultPath(environment(), System.getProperty("user.home"))
        }
        if (result.exitCode == 1) {
            return defaultPath(environment(), System.getProperty("user.home"))
        }
        result.throwOnError()
        throw VcsException("Git did not return a global ignore path.")
    }

    companion object {
        private const val CONFIG_KEY = "core.excludesFile"

        internal fun resolveConfiguredEntry(entry: String): Path? {
            val separator = entry.indexOf('\t')
            if (separator < 0) throw VcsException("Git returned an invalid $CONFIG_KEY value.")

            val origin = entry.substring(0, separator).removePrefix("file:")
            val value = entry.substring(separator + 1).trim()
            if (value.isEmpty()) return null

            val configured = Path.of(value)
            if (configured.isAbsolute) return configured.normalize()

            val configFile = Path.of(origin)
            val configDirectory = configFile.toAbsolutePath().parent
                ?: throw VcsException("Git returned an invalid global config location: $origin")
            return configDirectory.resolve(configured).normalize()
        }

        internal fun defaultPath(environment: Map<String, String>, userHome: String?): Path {
            val xdgHome = environment["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() }
            if (xdgHome != null) return Path.of(xdgHome).resolve("git").resolve("ignore").normalize()

            val home = environment["HOME"]?.takeIf { it.isNotBlank() }
                ?: userHome?.takeIf { it.isNotBlank() }
                ?: throw VcsException(
                    "Git global ignore file is not configured and the user home directory cannot be determined."
                )
            return Path.of(home).resolve(".config").resolve("git").resolve("ignore").normalize()
        }

        private fun environment(): Map<String, String> = System.getenv()
    }
}
