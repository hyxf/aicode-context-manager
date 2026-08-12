package com.aicode.service;

import com.aicode.model.SemVer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;

/** Finds the latest release tag and calculates the three supported increments. */
public final class GitTagVersionService {
    private static final SemVer INITIAL_BASELINE = new SemVer(0, 0, 0);

    private GitTagVersionService() {
    }

    public static @NotNull VersionCandidates calculateCandidates(@NotNull Collection<String> tags) {
        SemVer latest = tags.stream()
                .map(SemVer::parseTag)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(INITIAL_BASELINE);
        return new VersionCandidates(latest, latest.nextMajor(), latest.nextMinor(), latest.nextPatch());
    }

    public record VersionCandidates(
            @NotNull SemVer current,
            @NotNull SemVer major,
            @NotNull SemVer minor,
            @NotNull SemVer patch
    ) {
    }
}
