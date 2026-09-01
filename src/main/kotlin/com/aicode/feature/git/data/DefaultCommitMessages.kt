package com.aicode.feature.git.data

import com.aicode.feature.git.model.CommitMessageTemplate

object DefaultCommitMessages {
    val templates: List<CommitMessageTemplate> =
        listOf(
            CommitMessageTemplate(type = "docs", scope = "readme", subject = "修改 README.md"),
            CommitMessageTemplate(type = "style", subject = "格式化代码"),
            CommitMessageTemplate(type = "refactor", subject = "优化代码结构"),
            CommitMessageTemplate(type = "fix", subject = "修复已知问题"),
            CommitMessageTemplate(type = "feat", subject = "新增功能"),
            CommitMessageTemplate(type = "perf", subject = "优化性能"),
            CommitMessageTemplate(type = "test", subject = "补充测试用例"),
            CommitMessageTemplate(type = "build", subject = "更新构建配置和依赖"),
            CommitMessageTemplate(type = "chore", subject = "更新项目配置"),
        )

    fun missingFrom(messages: Collection<CommitMessageTemplate>): List<CommitMessageTemplate> =
        templates.filterNot(messages::contains)
}
