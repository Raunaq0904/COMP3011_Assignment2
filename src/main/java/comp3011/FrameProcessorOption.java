/*
 * New file for COMP3011 Assignment 2 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import java.util.function.Supplier;

/**
 * One command-line option that adds a {@link FrameProcessor} to the video player,
 * for example {@code -j, --jitter-frames}.
 *
 * <p>
 * An option is pure data: its flags, its help text, and a factory that builds a
 * fresh processor each time the option appears on the command line. A factory
 * (rather than a shared instance) is required because options may be repeated
 * (for example {@code -dd}) and every repetition must be its own processor
 * object with its own state.
 * </p>
 *
 * @param shortFlag   the single-letter form, without the dash (e.g. {@code 'j'})
 * @param longFlag    the long form, without the leading dashes (e.g. {@code "jitter-frames"})
 * @param description the text shown for this option in the help output
 * @param factory     creates a new processor instance for each occurrence of the option
 */
public record FrameProcessorOption(
        char shortFlag,
        String longFlag,
        String description,
        Supplier<FrameProcessor> factory) {

    /** Builds a new processor for one occurrence of this option. */
    public FrameProcessor create() {
        return factory.get();
    }
}
