/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011.server;

import java.io.IOException;

/** Turns audio into a speech-to-text JSON document. Kept as an interface so tests can supply canned responses. */
public interface SpeechToTextClient {

    String transcribe(byte[] audioBytes, String filename) throws IOException;
}
