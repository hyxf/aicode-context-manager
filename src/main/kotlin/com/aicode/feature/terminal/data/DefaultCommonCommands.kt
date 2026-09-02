package com.aicode.feature.terminal.data

object DefaultCommonCommands {
    val commands =
        listOf(
            "git status",
            "git pull",
            "git push",
            "./gradlew clean build",
            "./gradlew test",
        )

    fun missingFrom(commands: Collection<String>): List<String> = this.commands.filterNot(commands::contains)
}
