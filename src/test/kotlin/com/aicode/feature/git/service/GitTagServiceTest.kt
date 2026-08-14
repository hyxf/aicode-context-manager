package com.aicode.feature.git.service

import org.junit.Assert.*
import org.junit.Test

class GitTagServiceTest {
    @Test
    fun matchesOnlyTheExactRemoteTagRef() {
        assertTrue(GitTagService.hasRef("0123456789abcdef\trefs/tags/v1.6.4", "refs/tags/v1.6.4"))
        assertFalse(GitTagService.hasRef("0123456789abcdef\trefs/tags/v1.6.40", "refs/tags/v1.6.4"))
        assertFalse(
            GitTagService.hasRef("0123456789abcdef\trefs/tags/v1.6.4^{}", "refs/tags/v1.6.4")
        )
    }
}
