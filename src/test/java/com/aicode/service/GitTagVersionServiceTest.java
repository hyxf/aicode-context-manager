package com.aicode.service;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class GitTagVersionServiceTest {
    @Test
    public void choosesLatestTagByNumericVersion() {
        GitTagVersionService.VersionCandidates candidates = GitTagVersionService.calculateCandidates(
                List.of("test", "v1.9.9", "v1.10.0", "v1.10.0-beta")
        );

        assertEquals("v1.10.0", candidates.current().toTag());
        assertEquals("v2.0.0", candidates.major().toTag());
        assertEquals("v1.11.0", candidates.minor().toTag());
        assertEquals("v1.10.1", candidates.patch().toTag());
    }

    @Test
    public void usesZeroBaselineWhenNoReleaseTagExists() {
        GitTagVersionService.VersionCandidates candidates = GitTagVersionService.calculateCandidates(
                List.of("test", "v1.0", "release-1.0.0")
        );

        assertEquals("v0.0.0", candidates.current().toTag());
        assertEquals("v1.0.0", candidates.major().toTag());
        assertEquals("v0.1.0", candidates.minor().toTag());
        assertEquals("v0.0.1", candidates.patch().toTag());
    }
}
