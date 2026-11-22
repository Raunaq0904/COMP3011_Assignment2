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

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers which stretches of the media have already been sent for transcription, as a sorted list of ranges that
 * never overlap or touch. The subtitle feeder uses it to choose where to read next, so it never pays to transcribe the
 * same audio twice, whatever the viewer does (seek, toggle subtitles off and on). Used by the feeder thread only.
 */
final class CoverageMap {

    private final List<long[]> ranges = new ArrayList<>(); // each is {startUs, endUs}, endUs exclusive

    void markCovered(long fromUs, long toUs) {
        if (toUs <= fromUs) {
            return;
        }
        List<long[]> merged = new ArrayList<>();
        long newFrom = fromUs;
        long newTo = toUs;
        boolean placed = false;
        for (long[] range : ranges) {
            if (range[1] < newFrom) {
                merged.add(range); // entirely before the new range
            } else if (range[0] > newTo) {
                if (!placed) {
                    merged.add(new long[] { newFrom, newTo });
                    placed = true;
                }
                merged.add(range); // entirely after it
            } else {
                newFrom = Math.min(newFrom, range[0]); // overlaps or touches: absorb it
                newTo = Math.max(newTo, range[1]);
            }
        }
        if (!placed) {
            merged.add(new long[] { newFrom, newTo });
        }
        ranges.clear();
        ranges.addAll(merged);
    }

    /** The first position at or after {@code fromUs} (and before {@code endUs}) that is not covered, or -1 if none. */
    long firstGap(long fromUs, long endUs) {
        long position = Math.max(0, fromUs);
        for (long[] range : ranges) {
            if (range[1] <= position) {
                continue;
            }
            if (range[0] > position) {
                break;
            }
            position = range[1]; // inside a covered range: skip to its end
        }
        return position < endUs ? position : -1;
    }

    /**
     * Where to read next: the first gap from the playback position onwards, or if everything after it is done, the
     * first gap before it. Returns -1 when the whole media has been covered.
     */
    long nextToDo(long playbackUs, long endUs) {
        long gap = firstGap(playbackUs, endUs);
        if (gap >= 0) {
            return gap;
        }
        return firstGap(0, Math.min(playbackUs, endUs));
    }

    /** Where the gap that starts at {@code gapStartUs} ends: the start of the next covered range, or {@code endUs}. */
    long gapEnd(long gapStartUs, long endUs) {
        for (long[] range : ranges) {
            if (range[0] > gapStartUs) {
                return Math.min(range[0], endUs);
            }
        }
        return endUs;
    }
}