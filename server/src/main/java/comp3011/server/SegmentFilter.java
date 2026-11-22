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
