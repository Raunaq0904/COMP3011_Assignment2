/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 *
 * AI acknowledgement: this file was developed with the assistance of Claude (Anthropic's generative AI
 * assistant), used as a research, design and development aid. Claude proposed the design and the code,
 * explaining each part step by step. I typed every change in myself, then ran, tested and reviewed it so
 * that I understand what each part does and why it is in the project. All commits were made by me.
 * See the "AI acknowledgement" section of README.md for details.
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SubtitleTrackTest {

    private static final long SECOND = 1_000_000L;

    private static SubtitleCue cue(double startSeconds, double endSeconds, String text) {
        return new SubtitleCue((long) (startSeconds * SECOND), (long) (endSeconds * SECOND), text);
    }

    @Test
    void emptyTrackNeverHasACue() {
        assertTrue(SubtitleTrack.EMPTY.cueAt(0).isEmpty());
        assertTrue(SubtitleTrack.EMPTY.cueAt(5 * SECOND).isEmpty());
    }

    @Test
    void cueIsShownFromItsStartUntilJustBeforeItsEnd() {
        SubtitleTrack track = new SubtitleTrack(List.of(cue(2, 4, "Hello")));

        assertTrue(track.cueAt(2 * SECOND - 1).isEmpty());
        assertEquals("Hello", track.cueAt(2 * SECOND).orElseThrow().text());
        assertEquals("Hello", track.cueAt(4 * SECOND - 1).orElseThrow().text());
        assertTrue(track.cueAt(4 * SECOND).isEmpty());
    }

    @Test
    void gapBetweenCuesShowsNothing() {
        SubtitleTrack track = new SubtitleTrack(List.of(cue(1, 2, "One"), cue(5, 6, "Two")));

        assertEquals("One", track.cueAt(1 * SECOND + 1).orElseThrow().text());
        assertTrue(track.cueAt(3 * SECOND).isEmpty());
        assertEquals("Two", track.cueAt(5 * SECOND + 1).orElseThrow().text());
    }

    @Test
    void cuesGivenOutOfOrderAreStillFound() {
        SubtitleTrack track = new SubtitleTrack(List.of(cue(8, 9, "Third"), cue(1, 2, "First"), cue(4, 5, "Second")));

        assertEquals("First", track.cueAt(1 * SECOND).orElseThrow().text());
        assertEquals("Second", track.cueAt(4 * SECOND).orElseThrow().text());
        assertEquals("Third", track.cueAt(8 * SECOND).orElseThrow().text());
    }

    @Test
    void invalidCuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SubtitleCue(-1, 5, "x"));
        assertThrows(IllegalArgumentException.class, () -> new SubtitleCue(5, 5, "x"));
        assertThrows(IllegalArgumentException.class, () -> new SubtitleCue(5, 4, "x"));
    }
}