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

    @Test
    fun extractsOnlyDirectTagRefs() {
        assertEquals(
            "v1.6.4",
            GitTagService.parseTagRef("0123456789abcdef\trefs/tags/v1.6.4"),
        )
        assertNull(GitTagService.parseTagRef("0123456789abcdef\trefs/tags/v1.6.4^{}"))
        assertNull(GitTagService.parseTagRef("0123456789abcdef\trefs/heads/main"))
    }

    @Test
    fun parsesAheadAndBehindCounts() {
        assertEquals(3 to 2, GitTagService.parseDivergence("3\t2"))
        assertNull(GitTagService.parseDivergence("invalid"))
    }

    @Test
    fun explainsConflictingLocalAndRemoteTags() {
        val message =
            GitTagService.tagFetchConflictMessage(
                "! [rejected]        v1.9.8 -> v1.9.8  (would clobber existing tag)"
            )

        assertEquals(
            "Local tag v1.9.8 points to a different commit than the remote tag. " +
                "Rename or delete the local tag, then retry.",
            message,
        )
        assertNull(GitTagService.tagFetchConflictMessage("fatal: unable to access remote"))
    }

    @Test
    fun rejectsASelectedVersionThatIsNoLongerLatest() {
        assertTrue(GitTagService.isVersionOutdated("v1.2.1", listOf("v1.3.0")))
        assertTrue(GitTagService.isVersionOutdated("v1.3.0", listOf("v1.3.0")))
        assertFalse(GitTagService.isVersionOutdated("v1.3.1", listOf("v1.3.0")))
    }
}
