package com.aicode.feature.git.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class SemVerTest {
    @Test
    public void parsesOnlyCompleteVersionTags() {
        assertEquals(new SemVer(1, 6, 2), SemVer.parseTag("v1.6.2"));
        assertNull(SemVer.parseTag("v1.6"));
        assertNull(SemVer.parseTag("v1.6.2-beta"));
        assertNull(SemVer.parseTag("release-1.6.2"));
    }

    @Test
    public void incrementsEachVersionLevel() {
        SemVer version = new SemVer(1, 6, 3);
        assertEquals("v2.0.0", version.nextMajor().toTag());
        assertEquals("v1.7.0", version.nextMinor().toTag());
        assertEquals("v1.6.4", version.nextPatch().toTag());
    }

    @Test
    public void normalizesLeadingZeroes() {
        assertEquals("v1.2.3", SemVer.parseTag("v01.002.0003").toTag());
    }

    @Test
    public void rejectsComponentsThatExceedLongRange() {
        assertNull(SemVer.parseTag("v9223372036854775808.0.0"));
    }

    @Test
    public void failsInsteadOfWrappingOnIncrementOverflow() {
        SemVer version = new SemVer(Long.MAX_VALUE, 0, 0);
        assertThrows(ArithmeticException.class, version::nextMajor);
    }
}
