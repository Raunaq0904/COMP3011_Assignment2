/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SubtitleTextTest {

    private static void assertChunkFits(String chunk) {
        List<String> lines = SubtitleText.lines(chunk);
        assertTrue(lines.size() <= SubtitleText.MAX_LINES, "Too many lines: " + chunk);
        for (String line : lines) {
            assertTrue(line.length() <= SubtitleText.MAX_LINE_CHARS, "Line too long: " + line);
        }
    }

    @Test
    void shortTextStaysOnOneLine() {
        assertEquals(List.of("Hello there."), SubtitleText.lines("Hello there."));
    }

    @Test
    void textOfExactlyOneLineStaysOnOneLine() {
        String text = "word ".repeat(8) + "ab"; // 42 characters
        assertEquals(42, text.length());
        assertEquals(List.of(text), SubtitleText.lines(text));
    }

    @Test
    void longTextBreaksAfterPunctuation() {
        List<String> lines = SubtitleText.lines("I went to the shop yesterday, and then I came straight home.");

        assertEquals(List.of("I went to the shop yesterday,", "and then I came straight home."), lines);
    }

    @Test
    void longSpeechSplitsIntoChunksThatEachFit() {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(6).trim();

        List<String> chunks = SubtitleText.chunks(text);

        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertChunkFits(chunk);
        }
        assertEquals(text, String.join(" ", chunks));
    }

    @Test
    void aWordLongerThanALineIsCutSoNoLineIsTooLong() {
        List<String> chunks = SubtitleText.chunks("a".repeat(100));

        assertTrue(chunks.size() >= 2);
        for (String chunk : chunks) {
            assertChunkFits(chunk);
        }
    }

    @Test
    void blankTextGivesNothingToShow() {
        assertTrue(SubtitleText.chunks("   ").isEmpty());
        assertTrue(SubtitleText.lines("").isEmpty());
    }
}