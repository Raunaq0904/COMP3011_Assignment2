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

/** The part of an audio extractor that the subtitle feeder needs, so the feeder can be tested without a media file. */
interface ChunkSource extends AutoCloseable {

    /** Length of the media in microseconds, or 0 or less if unknown. */
    long durationUs();

    /** Moves the next chunk to start at about this position. */
    void seekTo(long positionUs) throws java.io.IOException;

    /** Reads about this much audio, or returns null when the end of the media has been reached. */
    AudioChunk nextChunk(long maxDurationUs) throws java.io.IOException;

    @Override
    void close();
}