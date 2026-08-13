package com.aicode.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Git history already split into the release sections used by the changelog renderer. */
public record ChangelogData(@NotNull List<Release> releases) {
    public record Release(
            @NotNull String version,
            @NotNull String date,
            @NotNull List<Commit> commits,
            boolean unreleased
    ) {
    }

    public record Commit(@NotNull String hash, @NotNull String subject) {
    }
}
