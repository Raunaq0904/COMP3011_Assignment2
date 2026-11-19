/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

/**
 * A rendered subtitle: a small ARGB image and where it sits on the video frame. It is built once per cue and then only
 * read, so it can be shared between worker threads. The pixel array must not be modified after construction.
 */
public record SubtitleOverlay(int x, int y, int width, int height, int[] argb) {
}