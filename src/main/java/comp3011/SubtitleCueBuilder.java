/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns spoken segments from the server into subtitle cues that follow the display rules: text that fits two lines of
 * at most 42 characters, breaks between phrases where possible, and each cue on screen for between one and seven
 * seconds without overlapping the next one.
 */
final class SubtitleCueBuilder {

    static final long MIN_DISPLAY_US = 1_000_000L;
    static final long MAX_DISPLAY_US = 7_000_000L;

    private record Draft(long startUs, long endUs, String text) {
    }

    private SubtitleCueBuilder() {
    }

    /**
     * @param segments the speech reported by the server, with times in seconds from the start of the audio it was sent
     * @param offsetUs where that audio began in the video, in microseconds
     */
    static List<SubtitleCue> build(List<SpokenSegment> segments, long offsetUs) {
        List<Draft> drafts = new ArrayList<>();
        for (SpokenSegment segment : segments) {
            long start = offsetUs + Math.round(segment.startSeconds() * 1_000_000.0);
            long end = offsetUs + Math.round(segment.endSeconds() * 1_000_000.0);
            if (end <= start || segment.text() == null) {
                continue;
            }
            splitIntoDrafts(segment.text(), start, end, drafts);
        }
        drafts.sort(Comparator.comparingLong(Draft::startUs));

        List<SubtitleCue> cues = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            Draft draft = drafts.get(i);
            long start = Math.max(0, draft.startUs());
            long end = Math.min(Math.max(draft.endUs(), start + MIN_DISPLAY_US), start + MAX_DISPLAY_US);
            if (i + 1 < drafts.size()) {
                end = Math.min(end, drafts.get(i + 1).startUs());
            }
            if (end > start) {
                cues.add(new SubtitleCue(start, end, draft.text()));
            }
        }
        return cues;
    }

    // Long speech is cut into chunks that each fit two lines, and the segment's time is shared out by text length.
    private static void splitIntoDrafts(String text, long start, long end, List<Draft> drafts) {
        List<String> chunks = SubtitleText.chunks(text);
        int totalChars = 0;
        for (String chunk : chunks) {
            totalChars += chunk.length();
        }
        long cursor = start;
        int used = 0;
        for (int i = 0; i < chunks.size(); i++) {
            used += chunks.get(i).length();
            long chunkEnd = (i == chunks.size() - 1) ? end : start + (end - start) * used / totalChars;
            drafts.add(new Draft(cursor, chunkEnd, chunks.get(i)));
            cursor = chunkEnd;
        }
    }
}