/*
 * Starter code supplied for Adelaide University COMP3011 Assignment 2.
 * Students are free to modify this file for assessment purposes.
 * 
 * Authors:
 *   1. Simon Ratcliffe, in collaboration with GPT-5.6 Terra
 *   2. Raunaqdeep Singh Khosla, a1954269
 *
 * Copyright 2026 Simon Ratcliffe
 */
package comp3011;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created in the VideoPlayerApp's main function to receive and parse the video player's command-line arguments.
 *
 * <p>
 * Extracts the video file and supported launch options, reports invalid input
 * or help text, and exposes the resulting launch configuration via methods to the
 * {@link VideoPlayerApp}.
 * </p>
 *
 * <p>
 * Frame-processor options (for example {@code -j} / {@code --jitter-frames}) are not
 * known to this class. They are resolved through {@link FrameProcessorRegistry}, and each
 * occurrence adds a new {@link FrameProcessor} to an ordered list, so options keep
 * their command-line order and may be repeated. Short options may be stacked, so
 * {@code -jfddn} means the same as {@code -j -f -d -d -n}.
 * </p>
 */
public class CommandLineController {
    private final String[] args;

    private boolean helpRequested;
    private boolean audioRequested;
    private boolean maximiseRequested;
    private Integer displayId;
    private File videoFile;
    private String errorMessage;
    private final List<FrameProcessor> frameProcessors = new ArrayList<>();

    public CommandLineController(String[] args) {
        this.args = args.clone();
        parse();
        if (errorMessage != null) {
            System.out.println(errorMessage);
        }
        if (helpRequested) {
            printHelp();
        }
    }

    public File getVideoFile() {
        return videoFile;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getExitCode() {
        return errorMessage == null ? 0 : 1;
    }

    public Integer getDisplayId() {
        return displayId;
    }

    public boolean isAudioRequested() {
        return audioRequested;
    }

    public boolean isMaximiseRequested() {
        return maximiseRequested;
    }

    /**
     * The frame processors requested on the command line, in command-line order
     * (a processor requested twice appears twice, as two separate objects).
     * The caller takes ownership of these objects; this controller does not use them again.
     */
    public List<FrameProcessor> getFrameProcessors() {
        return List.copyOf(frameProcessors);
    }

    public boolean shouldLaunchApplication() {
        return errorMessage == null && videoFile != null;
    }

    private void parse() {
        List<String> videoFiles = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                applyLongOption(arg);
            } else if (arg.startsWith("-")) {
                applyShortOptions(arg);
            } else {
                videoFiles.add(arg);
            }
        }

        if (videoFiles.size() > 1) {
            reportError("Usage: VideoPlayer [options] [video-file]");
        } else if (videoFiles.size() == 1) {
            videoFile = new File(videoFiles.get(0));
            if (!videoFile.isFile()) {
                reportError("File not found: " + videoFile.getPath());
                videoFile = null;
            }
        } else {
            if (!helpRequested) {
                reportError("No video file specified.");
            }
        }
    }

    /** Handles one long option, for example {@code --jitter-frames}. */
    private void applyLongOption(String arg) {
        String name = arg.substring(2);
        if (applyPlayerLongOption(name)) {
            return;
        }
        FrameProcessorRegistry.findByLongFlag(name).ifPresentOrElse(
                option -> frameProcessors.add(option.create()),
                () -> reportError("Unknown option: " + arg));
    }

    /**
     * Handles one dash-prefixed argument holding one or more short options, for example
     * {@code -a} or the stacked {@code -jfddn}. Each letter is applied in order.
     */
    private void applyShortOptions(String arg) {
        if (arg.length() == 1) {
            reportError("Unknown option: " + arg);
            return;
        }
        for (char flag : arg.substring(1).toCharArray()) {
            applyShortOption(flag);
        }
    }

    private void applyShortOption(char flag) {
        if (applyPlayerShortOption(flag)) {
            return;
        }
        FrameProcessorRegistry.findByShortFlag(flag).ifPresentOrElse(
                option -> frameProcessors.add(option.create()),
                () -> reportError("Unknown option: -" + flag));
    }

    /** Applies the player's own short options. Returns false if the letter is not one of them. */
    private boolean applyPlayerShortOption(char flag) {
        switch (flag) {
            case 'h' -> helpRequested = true;
            case 'a' -> audioRequested = true;
            case 'x' -> maximiseRequested = true;
            case '1' -> setDisplayId(1);
            case '2' -> setDisplayId(2);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Applies the player's own long options. Returns false if the name is not one of them. */
    private boolean applyPlayerLongOption(String name) {
        switch (name) {
            case "help" -> helpRequested = true;
            case "audio" -> audioRequested = true;
            case "maximise" -> maximiseRequested = true;
            case "monitor-1" -> setDisplayId(1);
            case "monitor-2" -> setDisplayId(2);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void setDisplayId(int displayId) {
        if (this.displayId != null && this.displayId != displayId) {
            reportError("Only one display option can be used");
            return;
        }
        this.displayId = displayId;
    }

    /** Records an error, keeping the first one so the root cause is not hidden by a later, less useful message. */
    private void reportError(String message) {
        if (errorMessage == null) {
            errorMessage = message;
        }
    }

    private void printHelp() {
        System.out.println("Usage: VideoPlayer [options] [video-file]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help         Show this help message");
        System.out.println("  -a, --audio        Play audio");
        System.out.println("  -x, --maximise     Open the player maximised");
        System.out.println("  -1, --monitor-1    Open the player on display 1");
        System.out.println("  -2, --monitor-2    Open the player on display 2");
        System.out.println();
        System.out.println("Frame processors:");
        FrameProcessorRegistry.helpLines().forEach(System.out::println);
        System.out.println();
        System.out.println("Frame processors are applied in command-line order and may be repeated.");
        System.out.println("Example: -nssnfwyvdjmbp numbers, scratches twice, numbers again, flickers,");
        System.out.println("converts, warms, vignettes, dusts, jitters, mottles, bleeds, then peppers.");
    }
}
