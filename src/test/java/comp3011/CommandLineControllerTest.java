/*
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Regression tests for command-line parsing. They need no display or video
 * decoding: CommandLineController only parses arguments and checks the file exists.
 */
class CommandLineControllerTest {

    // JUnit creates a fresh temporary folder per test and deletes it afterwards,
    // so tests never depend on a real video on your machine.
    @TempDir
    Path tempDir;

    private String videoPath() throws IOException {
        return Files.createFile(tempDir.resolve("clip.mp4")).toString();
    }

    // Builds the argument array from a space-separated option string and appends a real file.
    private CommandLineController parse(String options) throws IOException {
        List<String> args = new ArrayList<>(Arrays.asList(options.split(" ")));
        args.add(videoPath());
        return new CommandLineController(args.toArray(String[]::new));
    }

    // Compares by class, because the effects don't implement equals().
    private static List<Class<?>> classesOf(CommandLineController controller) {
        return controller.getFrameProcessors().stream().<Class<?>>map(Object::getClass).toList();
    }

    @Test
    void noEffectOptionsGivesEmptyProcessorList() throws IOException {
        CommandLineController controller =
                new CommandLineController(new String[] { videoPath() });

        assertTrue(controller.shouldLaunchApplication());
        assertTrue(controller.getFrameProcessors().isEmpty());
    }

    // The same five effects must come out of every spelling the spec allows:
    // long, short, stacked, repeated and mixed.
    @ParameterizedTest
    @ValueSource(strings = {
            "-j -f -d -d -n",
            "-jfddn",
            "--jitter-frames --flicker-frames --dust-frames --dust-frames --number-frames",
            "-jf -dd --number-frames",
            "-j --flicker-frames -ddn" })
    void allOptionStylesGiveTheSameOrderedProcessors(String options) throws IOException {
        assertEquals(
                List.of(FrameJitterer.class, FrameFlickerer.class, FrameDuster.class,
                        FrameDuster.class, FrameNumberer.class),
                classesOf(parse(options)));
    }

    // The spec's own example: all 11 effects, with repeats, in exact command-line order.
    @Test
    void specExampleKeepsExactCommandLineOrder() throws IOException {
        assertEquals(
                List.of(FrameNumberer.class, FrameScratcher.class, FrameScratcher.class,
                        FrameNumberer.class, FrameFlickerer.class, FrameBlackAndWhiter.class,
                        FrameYellower.class, FrameVignetter.class, FrameDuster.class,
                        FrameJitterer.class, FrameMottler.class, FrameBleeder.class,
                        FramePepperer.class),
                classesOf(parse("-nssnfwyvdjmbp")));
    }

    // Repeating an option must create independent objects, because each processor
    // instance has its own random seed derived from its instance index.
    @Test
    void repeatedOptionCreatesDistinctInstances() throws IOException {
        List<FrameProcessor> processors = parse("-ss").getFrameProcessors();

        assertEquals(2, processors.size());
        assertNotSame(processors.get(0), processors.get(1));
    }
    
    // An unknown stacked letter must be rejected, not silently ignored.
    @Test
    void unknownShortOptionIsAnError() throws IOException {
        CommandLineController controller = parse("-z");

        assertFalse(controller.shouldLaunchApplication());
        assertEquals("Unknown option: -z", controller.getErrorMessage());
        assertEquals(1, controller.getExitCode());
    }

    @Test
    void unknownLongOptionIsAnError() throws IOException {
        CommandLineController controller = parse("--bogus");

        assertFalse(controller.shouldLaunchApplication());
        assertEquals("Unknown option: --bogus", controller.getErrorMessage());
    }

    // The first error is the root cause; a later problem (here: no file) must not hide it.
    @Test
    void firstErrorIsKept() {
        CommandLineController controller = new CommandLineController(new String[] { "-z" });

        assertEquals("Unknown option: -z", controller.getErrorMessage());
    }

    @Test
    void missingVideoFileIsAnError() {
        CommandLineController controller = new CommandLineController(new String[] { "-n" });

        assertFalse(controller.shouldLaunchApplication());
        assertEquals("No video file specified.", controller.getErrorMessage());
    }

    @Test
    void nonexistentVideoFileIsAnError() {
        CommandLineController controller =
                new CommandLineController(new String[] { "no-such-file.mp4" });

        assertFalse(controller.shouldLaunchApplication());
        assertTrue(controller.getErrorMessage().startsWith("File not found"));
    }

    // Help alone is not an error: exit code 0, but the player must not launch.
    @Test
    void helpAloneIsNotAnErrorAndDoesNotLaunch() {
        CommandLineController controller = new CommandLineController(new String[] { "-h" });

        assertFalse(controller.shouldLaunchApplication());
        assertEquals(0, controller.getExitCode());
    }

    // Player options and effects can share one stacked argument and still all take effect.
    @Test
    void playerOptionsAndEffectsCanBeStackedTogether() throws IOException {
        CommandLineController controller = parse("-ax1wy");

        assertTrue(controller.isAudioRequested());
        assertTrue(controller.isMaximiseRequested());
        assertEquals(1, controller.getDisplayId());
        assertEquals(List.of(FrameBlackAndWhiter.class, FrameYellower.class), classesOf(controller));
    }

    @Test
    void conflictingDisplayOptionsAreAnError() throws IOException {
        CommandLineController controller = parse("-12");

        assertFalse(controller.shouldLaunchApplication());
        assertEquals("Only one display option can be used", controller.getErrorMessage());
    }

    // Callers receive a read-only snapshot, so they can't corrupt the controller's list.
    @Test
    void returnedProcessorListCannotBeModified() throws IOException {
        List<FrameProcessor> processors = parse("-n").getFrameProcessors();

        assertThrows(UnsupportedOperationException.class, processors::clear);
    }
}