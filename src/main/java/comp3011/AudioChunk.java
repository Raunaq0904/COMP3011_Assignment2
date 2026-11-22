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

/**
 * A piece of the media's audio ready to send to the subtitle server.
 *
 * @param startUs    where the piece begins in the media, in microseconds
 * @param durationUs how much audio it holds, in microseconds
 * @param wav        the audio as a complete mono 16 kHz WAV file
 */
record AudioChunk(long startUs, long durationUs, byte[] wav) {
}