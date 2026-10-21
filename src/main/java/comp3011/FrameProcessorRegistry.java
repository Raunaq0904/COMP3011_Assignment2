/*
 * New file for COMP3011 Assignment 2 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import java.util.List;
import java.util.Optional;

/**
 * The single place that knows which command-line options map to which
 * {@link FrameProcessor} classes.
 *
 * <p>
 * {@link CommandLineController} asks this registry to resolve flags and never
 * references an individual effect class itself, so the parser stays the same
 * no matter how many effects exist. The help text for the frame processors is
 * also generated from this table, so the help and the behaviour cannot drift apart.
 * </p>
 *
 * <h2>How to add a new FrameProcessor</h2>
 * <ol>
 *   <li>Write the new class extending {@link FrameProcessor} with a public no-argument constructor.</li>
 *   <li>Add one {@link FrameProcessorOption} line to {@link #OPTIONS} below.
 *       Pick a short flag not already used here or by {@link CommandLineController}
 *       ({@code h a x 1 2} are taken by the player's own options).</li>
 * </ol>
 * <p>
 * Nothing else changes: parsing, stacking (e.g. {@code -jfd}), repetition, ordering
 * and the help output all pick the new option up automatically. Options appear in
 * the help in the order listed here.
 * </p>
 */
public final class FrameProcessorRegistry {

    /** Column width for "--long-flag" in the help output, so descriptions line up. */
    private static final int HELP_FLAG_COLUMN_WIDTH = 19;

    /** Every supported frame-processor option. THIS IS THE EXTENSION POINT. */
    private static final List<FrameProcessorOption> OPTIONS = List.of(
            new FrameProcessorOption('n', "number-frames", "Render the frame number onto each frame", FrameNumberer::new),
            new FrameProcessorOption('s', "scratch-frames", "Render vertical film scratches", FrameScratcher::new),
            new FrameProcessorOption('f', "flicker-frames", "Randomly dim frames", FrameFlickerer::new),
            new FrameProcessorOption('w', "black-and-white", "Convert frames to black and white", FrameBlackAndWhiter::new),
            new FrameProcessorOption('y', "yellow-frames", "Apply a warmer colour temperature", FrameYellower::new),
            new FrameProcessorOption('v', "vignette-frames", "Darken the frame edges", FrameVignetter::new),
            new FrameProcessorOption('d', "dust-frames", "Render dust and hair marks", FrameDuster::new),
            new FrameProcessorOption('j', "jitter-frames", "Randomly displace frames by a few pixels", FrameJitterer::new),
            new FrameProcessorOption('m', "mottle-frames", "Add cloudy emulsion mottling", FrameMottler::new),
            new FrameProcessorOption('b', "bleed-frames", "Bleed light into frames", FrameBleeder::new),
            new FrameProcessorOption('p', "pepper-frames", "Pepper frames with dark spots/blotches", FramePepperer::new));

    private FrameProcessorRegistry() {
        // Static lookup only; not meant to be instantiated.
    }

    /** Finds the option for a single-letter flag such as {@code 'j'} (no dash). */
    public static Optional<FrameProcessorOption> findByShortFlag(char shortFlag) {
        return OPTIONS.stream()
                .filter(option -> option.shortFlag() == shortFlag)
                .findFirst();
    }

    /** Finds the option for a long flag such as {@code "jitter-frames"} (no leading dashes). */
    public static Optional<FrameProcessorOption> findByLongFlag(String longFlag) {
        return OPTIONS.stream()
                .filter(option -> option.longFlag().equals(longFlag))
                .findFirst();
    }

    /** One formatted help line per option, e.g. {@code "  -n, --number-frames    Render ..."}. */
    public static List<String> helpLines() {
        return OPTIONS.stream()
                .map(option -> String.format("  -%c, %-" + HELP_FLAG_COLUMN_WIDTH + "s%s",
                        option.shortFlag(), "--" + option.longFlag(), option.description()))
                .toList();
    }
}
