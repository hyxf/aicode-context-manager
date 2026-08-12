package com.aicode.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A strict vMAJOR.MINOR.PATCH version used for release tags. */
public final class SemVer implements Comparable<SemVer> {
    private static final Pattern TAG_PATTERN = Pattern.compile("^v(\\d+)\\.(\\d+)\\.(\\d+)$");

    private final long major;
    private final long minor;
    private final long patch;

    public SemVer(long major, long minor, long patch) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version components must be non-negative");
        }
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static @Nullable SemVer parseTag(@NotNull String tag) {
        Matcher matcher = TAG_PATTERN.matcher(tag);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new SemVer(
                    Long.parseLong(matcher.group(1)),
                    Long.parseLong(matcher.group(2)),
                    Long.parseLong(matcher.group(3))
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public @NotNull SemVer nextMajor() {
        return new SemVer(Math.incrementExact(major), 0, 0);
    }

    public @NotNull SemVer nextMinor() {
        return new SemVer(major, Math.incrementExact(minor), 0);
    }

    public @NotNull SemVer nextPatch() {
        return new SemVer(major, minor, Math.incrementExact(patch));
    }

    public @NotNull String toTag() {
        return "v" + major + "." + minor + "." + patch;
    }

    @Override
    public int compareTo(@NotNull SemVer other) {
        int majorComparison = Long.compare(major, other.major);
        if (majorComparison != 0) {
            return majorComparison;
        }
        int minorComparison = Long.compare(minor, other.minor);
        return minorComparison != 0 ? minorComparison : Long.compare(patch, other.patch);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SemVer other)) {
            return false;
        }
        return major == other.major && minor == other.minor && patch == other.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return toTag();
    }
}
