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