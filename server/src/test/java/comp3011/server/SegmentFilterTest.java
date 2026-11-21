/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SegmentFilterTest {

    @Test
    void clearSpeechIsKept() {
        assertFalse(SegmentFilter.isNotSpeech(0.05, -0.3));
    }

    @Test
    void likelyNonSpeechWithLowConfidenceIsSkipped() {
        assertTrue(SegmentFilter.isNotSpeech(0.9, -1.5));
    }

    @Test
    void aSegmentIsKeptWhenOnlyOneConditionHolds() {
        assertFalse(SegmentFilter.isNotSpeech(0.9, -0.5));
        assertFalse(SegmentFilter.isNotSpeech(0.2, -1.5));
    }

    @Test
    void thresholdValuesThemselvesAreKept() {
        assertFalse(SegmentFilter.isNotSpeech(0.6, -1.0));
    }
}
