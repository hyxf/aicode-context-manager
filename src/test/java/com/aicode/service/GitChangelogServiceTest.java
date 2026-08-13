package com.aicode.service;

import com.aicode.model.ChangelogData.Commit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GitChangelogServiceTest {
    @Test
    public void parsesSemanticTagAndCreationDate() {
        GitChangelogService.Tag tag = GitChangelogService.parseTag("v1.6.7\t2026-08-13");

        assertEquals("v1.6.7", tag.name());
        assertEquals("2026-08-13", tag.date());
        assertEquals("v1.6.7", tag.version().toTag());
    }

    @Test
    public void ignoresTagsOutsideSupportedSemanticVersionFormat() {
        assertNull(GitChangelogService.parseTag("release-1.6.7\t2026-08-13"));
        assertNull(GitChangelogService.parseTag("v1.6.7-beta\t2026-08-13"));
        assertNull(GitChangelogService.parseTag("v1.6\t2026-08-13"));
    }

    @Test
    public void parsesCommitHashAndPreservesTabsInSubject() {
        Commit commit = GitChangelogService.parseCommit("abc123\tfeat: support\ttabs");

        assertEquals("abc123", commit.hash());
        assertEquals("feat: support\ttabs", commit.subject());
    }

    @Test
    public void ignoresMalformedCommitOutput() {
        assertNull(GitChangelogService.parseCommit("missing separator"));
        assertNull(GitChangelogService.parseCommit("abc123\t"));
    }
}
