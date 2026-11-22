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
import java.util.Optional;

/**
 * An immutable set of subtitle cues ordered by start time. Because it never changes after construction it can be
 * shared between the thread that receives subtitles and the worker threads that draw them, with no locking: a new
 * track is built when new subtitles arrive and the shared reference is swapped.
 */
public final class SubtitleTrack {

    public static final SubtitleTrack EMPTY = new SubtitleTrack(List.of());

    private final List<SubtitleCue> cues;

    public SubtitleTrack(List<SubtitleCue> cues) {
        List<SubtitleCue> sorted = new ArrayList<>(cues);
        sorted.sort(Comparator.comparingLong(SubtitleCue::startUs));
        this.cues = List.copyOf(sorted);
    }

    public int size() {
        return cues.size();
    }

    /** Returns a new track holding this track's cues plus the given ones. This track is not changed. */
    public SubtitleTrack plus(List<SubtitleCue> more) {
        List<SubtitleCue> all = new ArrayList<>(cues);
        all.addAll(more);
        return new SubtitleTrack(all);
    }

    /**
     * Finds the cue to show at a video time: the last cue that has started, if it has not yet ended. Uses a binary
     * search because this runs once for every frame.
     */
    public Optional<SubtitleCue> cueAt(long timestampUs) {
        int low = 0;
        int high = cues.size() - 1;
        int lastStarted = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (cues.get(mid).startUs() <= timestampUs) {
                lastStarted = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        if (lastStarted < 0) {
            return Optional.empty();
        }
        SubtitleCue candidate = cues.get(lastStarted);
        return candidate.isActiveAt(timestampUs) ? Optional.of(candidate) : Optional.empty();
    }
}