/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011.server;

/** JSON body of {@code GET /api/v1/global/usage}: cumulative audio seconds sent for transcription. */
public record UsageResponse(double usage) {
}