/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011.server;

import java.util.List;

/** JSON body returned to the player: the spoken segments, with times in seconds from the start of the audio sent. */
public record SubtitlesResponse(List<Segment> segments) {

    public record Segment(double start, double end, String text) {
    }
}
