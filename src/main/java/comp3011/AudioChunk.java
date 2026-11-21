/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

/**
 * A piece of the media's audio ready to send to the subtitle server.
 *
 * @param startUs    where the piece begins in the media, in microseconds
 * @param durationUs how much audio it holds, in microseconds
 * @param wav        the audio as a complete mono 16 kHz WAV file
 */
record AudioChunk(long startUs, long durationUs, byte[] wav) {
}