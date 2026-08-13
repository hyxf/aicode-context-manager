package com.aicode.util;

import com.aicode.model.ChangelogData;
import com.aicode.model.ChangelogData.Commit;
import com.aicode.model.ChangelogData.Release;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds and safely merges the plugin-managed section of CHANGELOG.md. */
public final class ChangelogBuilder {
    public static final String START_MARKER = "<!-- aicode-changelog:start -->";
    public static final String END_MARKER = "<!-- aicode-changelog:end -->";
    private static final String HEADER = "# Changelog\n\n"
            + "All notable changes to this project will be documented in this file.\n\n";
    private static final Pattern CONVENTIONAL_COMMIT = Pattern.compile(
            "^(feat|fix|perf|refactor|revert|docs|test|build|ci|chore)(?:\\(([^)]+)\\))?!?:\\s*(.+)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> CATEGORY_ORDER = List.of(
            "Added", "Fixed", "Changed", "Removed", "Other Changes"
    );

    private ChangelogBuilder() {
    }

    public static @NotNull String create(@NotNull ChangelogData data) {
        return HEADER + managedSection(data) + "\n";
    }

    public static boolean hasManagedSection(@NotNull String existing) {
        int start = existing.indexOf(START_MARKER);
        int end = existing.indexOf(END_MARKER);
        return start >= 0 && end > start;
    }

    public static @NotNull String update(@NotNull String existing, @NotNull ChangelogData data) {
        int start = existing.indexOf(START_MARKER);
        int end = existing.indexOf(END_MARKER);
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("The changelog does not contain a valid managed section");
        }
        int suffixStart = end + END_MARKER.length();
        return existing.substring(0, start) + managedSection(data) + existing.substring(suffixStart);
    }

    private static @NotNull String managedSection(@NotNull ChangelogData data) {
        StringBuilder markdown = new StringBuilder(START_MARKER).append('\n');
        for (Release release : data.releases()) {
            markdown.append("\n## [").append(release.version()).append(']');
            if (!release.unreleased() && !release.date().isBlank()) {
                markdown.append(" - ").append(release.date());
            }
            markdown.append('\n');
            appendCommits(markdown, release.commits());
        }
        return markdown.append('\n').append(END_MARKER).toString();
    }

    private static void appendCommits(@NotNull StringBuilder markdown, @NotNull List<Commit> commits) {
        Map<String, List<String>> categories = new LinkedHashMap<>();
        CATEGORY_ORDER.forEach(category -> categories.put(category, new ArrayList<>()));
        for (Commit commit : commits) {
            ParsedCommit parsed = parse(commit.subject());
            categories.get(parsed.category()).add(parsed.description());
        }
        categories.forEach((category, descriptions) -> {
            if (!descriptions.isEmpty()) {
                markdown.append("\n### ").append(category).append("\n\n");
                descriptions.forEach(description -> markdown.append("- ").append(description).append('\n'));
            }
        });
    }

    static @NotNull ParsedCommit parse(@NotNull String subject) {
        Matcher matcher = CONVENTIONAL_COMMIT.matcher(subject.trim());
        if (!matcher.matches()) {
            return new ParsedCommit("Other Changes", subject.trim());
        }
        String type = matcher.group(1).toLowerCase();
        String scope = matcher.group(2);
        String description = matcher.group(3).trim();
        if (scope != null && !scope.isBlank()) {
            description = "**" + scope + ":** " + description;
        }
        String category = switch (type) {
            case "feat" -> "Added";
            case "fix" -> "Fixed";
            case "revert" -> "Removed";
            case "perf", "refactor" -> "Changed";
            default -> "Other Changes";
        };
        return new ParsedCommit(category, description);
    }

    record ParsedCommit(@NotNull String category, @NotNull String description) {
    }
}
