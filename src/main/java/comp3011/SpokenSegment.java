/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

/** One stretch of speech reported by the subtitle server: its text and its time span in seconds. */
public record SpokenSegment(double startSeconds, double endSeconds, String text) {
}