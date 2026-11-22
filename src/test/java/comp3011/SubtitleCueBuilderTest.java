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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SubtitleCueBuilderTest {

    private static final long SECOND = 1_000_000L;

    private static SpokenSegment segment(double start, double end, String text) {
        return new SpokenSegment(start, end, text);
    }

    @Test
    void aShortSegmentBecomesOneCueShiftedByTheChunkOffset() {
        List<SubtitleCue> cues = SubtitleCueBuilder.build(List.of(segment(1.0, 3.0, "Hello there.")), 10 * SECOND);

        assertEquals(1, cues.size());
        assertEquals(11 * SECOND, cues.get(0).startUs());
        assertEquals(13 * SECOND, cues.get(0).endUs());
        assertEquals("Hello there.", cues.get(0).text());
    }

    @Test
    void aLongSegmentIsSplitBetweenPhrasesAndSharesItsTime() {
        String text = "I went to the shop yesterday, and then I came straight home. After that I made dinner and watched a film.";

        List<SubtitleCue> cues = SubtitleCueBuilder.build(List.of(segment(0, 10, text)), 0);

        assertEquals(2, cues.size());
        assertEquals("I went to the shop yesterday, and then I came straight home.", cues.get(0).text());
        assertEquals("After that I made dinner and watched a film.", cues.get(1).text());
        assertEquals(0, cues.get(0).startUs());
        assertEquals(cues.get(0).endUs(), cues.get(1).startUs());
        assertEquals(10 * SECOND, cues.get(1).endUs());
        assertTrue(cues.get(0).endUs() > 5 * SECOND && cues.get(0).endUs() < 6 * SECOND);
    }

    @Test
    void noCueStaysOnScreenLongerThanSevenSeconds() {
        List<SubtitleCue> cues = SubtitleCueBuilder.build(List.of(segment(0, 20, "Hello.")), 0);

        assertEquals(7 * SECOND, cues.get(0).endUs());
    }

    @Test
    void noCueIsShownForLessThanOneSecond() {
        List<SubtitleCue> cues = SubtitleCueBuilder.build(List.of(segment(5.0, 5.3, "Yes.")), 0);

        assertEquals(5 * SECOND, cues.get(0).startUs());
        assertEquals(6 * SECOND, cues.get(0).endUs());
    }

    @Test
    void aCueIsCutShortRatherThanOverlapTheNextOne() {
        List<SubtitleCue> cues = SubtitleCueBuilder.build(
                List.of(segment(0, 0.4, "Hi."), segment(0.5, 3, "There.")), 0);

        assertEquals(2, cues.size());
        assertEquals(500_000L, cues.get(0).endUs());
        assertEquals(500_000L, cues.get(1).startUs());
    }

    @Test
    void blankOrEmptySegmentsAreIgnored() {
        List<SubtitleCue> cues = SubtitleCueBuilder.build(
                List.of(segment(0, 2, "   "), segment(3, 3, "No time."), segment(4, 6, "Real.")), 0);

        assertEquals(1, cues.size());
        assertEquals("Real.", cues.get(0).text());
    }

    @Test
    void everyCueFitsTheTextRulesEvenForVeryLongSpeech() {
        String sentence = "The quick brown fox jumps over the lazy dog, and then it runs away. ";
        List<SubtitleCue> cues = SubtitleCueBuilder.build(List.of(segment(0, 60, sentence.repeat(10))), 0);

        assertTrue(cues.size() > 1);
        for (SubtitleCue cue : cues) {
            List<String> lines = SubtitleText.lines(cue.text());
            assertTrue(lines.size() <= SubtitleText.MAX_LINES, cue.text());
            for (String line : lines) {
                assertTrue(line.length() <= SubtitleText.MAX_LINE_CHARS, line);
            }
            long shown = cue.endUs() - cue.startUs();
            assertTrue(shown >= SubtitleCueBuilder.MIN_DISPLAY_US && shown <= SubtitleCueBuilder.MAX_DISPLAY_US);
        }
    }
}