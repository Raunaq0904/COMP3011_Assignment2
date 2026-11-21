/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011.server;

/** Decides which transcribed segments are probably not real speech (background noise, music, silence). */
final class SegmentFilter {

    static final double NO_SPEECH_LIMIT = 0.6;
    static final double LOG_PROB_LIMIT = -1.0;

    private SegmentFilter() {
    }

    /** A segment is skipped only when it is both likely to be non-speech and low confidence. */
    static boolean isNotSpeech(double noSpeechProb, double avgLogProb) {
        return noSpeechProb > NO_SPEECH_LIMIT && avgLogProb < LOG_PROB_LIMIT;
    }
}
