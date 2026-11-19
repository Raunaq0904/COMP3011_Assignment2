/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import java.util.Objects;

/**
 * One subtitle: the text to show and the video time span it is shown for. Times are in microseconds so they compare
 * directly with {@link InfoFrame#timestampUs()}. The start is inclusive and the end is exclusive.
 */
public record SubtitleCue(long startUs, long endUs, String text) {

    public SubtitleCue {
        Objects.requireNonNull(text, "text");
        if (startUs < 0) {
            throw new IllegalArgumentException("Cue start must not be negative: " + startUs);
        }
        if (endUs <= startUs) {
            throw new IllegalArgumentException("Cue end must be after its start: " + startUs + " .. " + endUs);
        }
    }

    public boolean isActiveAt(long timestampUs) {
        return timestampUs >= startUs && timestampUs < endUs;
    }
}