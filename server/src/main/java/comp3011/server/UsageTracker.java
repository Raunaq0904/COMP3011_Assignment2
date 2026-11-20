/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011.server;

import java.util.concurrent.atomic.DoubleAdder;

import org.springframework.stereotype.Component;

/**
 * Cumulative seconds of audio sent for transcription since the server started. Many requests record usage at the same
 * time, so the total is kept in a {@link DoubleAdder}, which is safe to update from any number of threads.
 */
@Component
public class UsageTracker {

    private final DoubleAdder seconds = new DoubleAdder();

    public void addSeconds(double audioSeconds) {
        if (audioSeconds > 0) {
            seconds.add(audioSeconds);
        }
    }

    public double totalSeconds() {
        return seconds.sum();
    }
}