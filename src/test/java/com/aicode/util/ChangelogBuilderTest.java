package com.aicode.util;

import com.aicode.model.ChangelogData;
import com.aicode.model.ChangelogData.Commit;
import com.aicode.model.ChangelogData.Release;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ChangelogBuilderTest {
    @Test
    public void groupsConventionalCommitsAndKeepsUnknownSubjects() {
        String markdown = ChangelogBuilder.create(new ChangelogData(List.of(
                new Release("Unreleased", "", List.of(
                        new Commit("1", "feat(ui): add preview"),
                        new Commit("2", "fix: avoid overwriting files"),
                        new Commit("3", "update documentation")
                ), true)
        )));

        assertTrue(markdown.contains("### Added\n\n- **ui:** add preview"));
        assertTrue(markdown.contains("### Fixed\n\n- avoid overwriting files"));
        assertTrue(markdown.contains("### Other Changes\n\n- update documentation"));
        assertFalse(markdown.contains("feat(ui)"));
    }

    @Test
    public void rendersReleaseDatesAndOmitsEmptyCategories() {
        String markdown = ChangelogBuilder.create(new ChangelogData(List.of(
                new Release("Unreleased", "", List.of(), true),
                new Release("1.6.0", "2026-08-13", List.of(
                        new Commit("1", "perf: speed up history loading")
                ), false)
        )));

        assertTrue(markdown.contains("## [Unreleased]"));
        assertTrue(markdown.contains("## [1.6.0] - 2026-08-13"));
        assertTrue(markdown.contains("### Changed\n\n- speed up history loading"));
        assertFalse(markdown.contains("### Added"));
    }

    @Test
    public void updatesOnlyManagedSection() {
        ChangelogData data = new ChangelogData(List.of(
                new Release("Unreleased", "", List.of(new Commit("1", "fix: refreshed")), true)
        ));
        String existing = "# Custom title\n\nManual introduction.\n\n"
                + ChangelogBuilder.START_MARKER + "\nold generated content\n"
                + ChangelogBuilder.END_MARKER + "\n\nManual footer.\n";

        String updated = ChangelogBuilder.update(existing, data);

        assertTrue(updated.startsWith("# Custom title\n\nManual introduction."));
        assertTrue(updated.contains("- refreshed"));
        assertFalse(updated.contains("old generated content"));
        assertTrue(updated.endsWith("\n\nManual footer.\n"));
    }

    @Test
    public void rejectsFilesWithoutCompleteMarkers() {
        assertFalse(ChangelogBuilder.hasManagedSection("# Changelog"));
        assertThrows(IllegalArgumentException.class,
                () -> ChangelogBuilder.hasManagedSection(ChangelogBuilder.START_MARKER));
        assertThrows(IllegalArgumentException.class,
                () -> ChangelogBuilder.hasManagedSection(ChangelogBuilder.END_MARKER));
    }

    @Test
    public void rejectsDuplicateOrReversedMarkers() {
        String duplicate = ChangelogBuilder.START_MARKER + "\n" + ChangelogBuilder.START_MARKER + "\n"
                + ChangelogBuilder.END_MARKER;
        assertThrows(IllegalArgumentException.class,
                () -> ChangelogBuilder.hasManagedSection(duplicate));
        assertThrows(IllegalArgumentException.class,
                () -> ChangelogBuilder.update(duplicate, new ChangelogData(List.of())));
        assertThrows(IllegalArgumentException.class, () -> ChangelogBuilder.hasManagedSection(
                ChangelogBuilder.END_MARKER + "\n" + ChangelogBuilder.START_MARKER
        ));
    }

    @Test
    public void escapesManagedMarkersFromCommitSubjects() {
        ChangelogData data = new ChangelogData(List.of(
                new Release("Unreleased", "", List.of(
                        new Commit("1", "fix: handle " + ChangelogBuilder.END_MARKER)
                ), true)
        ));

        String markdown = ChangelogBuilder.create(data);

        assertTrue(ChangelogBuilder.hasManagedSection(markdown));
        assertEquals(1, occurrences(markdown, ChangelogBuilder.START_MARKER));
        assertEquals(1, occurrences(markdown, ChangelogBuilder.END_MARKER));
        assertTrue(markdown.contains("&lt;!-- aicode-changelog:end --&gt;"));
    }

    @Test
    public void mapsBreakingAndMaintenanceCommits() {
        assertEquals("Added", ChangelogBuilder.parse("feat!: breaking feature").category());
        assertEquals("Other Changes", ChangelogBuilder.parse("chore(ci): update runner").category());
        assertEquals("**ci:** update runner", ChangelogBuilder.parse("chore(ci): update runner").description());
        assertEquals("Removed", ChangelogBuilder.parse("revert: remove feature").category());
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }
}
