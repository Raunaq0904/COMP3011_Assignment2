/*
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Guards the extension point: anyone adding a FrameProcessor to the registry
 * must not clash with an existing flag or with the player's own reserved flags.
 */
class FrameProcessorRegistryTest {

    // Short flags the player itself uses (help, audio, maximise, monitor 1 and 2).
    private static final String RESERVED_SHORT_FLAGS = "haxX12";

    // The flag letter sits at index 3 of each help line: "  -n, --number-frames ..."
    private static char shortFlagOf(String helpLine) {
        return helpLine.charAt(3);
    }

    @Test
    void helpListsEveryRegisteredProcessor() {
        assertEquals(11, FrameProcessorRegistry.helpLines().size());
    }

    @Test
    void everyListedOptionIsFindableByItsShortFlag() {
        for (String line : FrameProcessorRegistry.helpLines()) {
            assertTrue(FrameProcessorRegistry.findByShortFlag(shortFlagOf(line)).isPresent(), line);
        }
    }

    @Test
    void shortFlagsAreUniqueAndNeverReserved() {
        Set<Character> seen = new HashSet<>();
        for (String line : FrameProcessorRegistry.helpLines()) {
            char flag = shortFlagOf(line);
            assertTrue(seen.add(flag), "Duplicate short flag " + flag);
            assertTrue(RESERVED_SHORT_FLAGS.indexOf(flag) < 0, "Reserved flag " + flag);
        }
    }

    @Test
    void factoriesProduceNonNullProcessors() {
        List<String> lines = FrameProcessorRegistry.helpLines();
        for (String line : lines) {
            FrameProcessorOption option =
                    FrameProcessorRegistry.findByShortFlag(shortFlagOf(line)).orElseThrow();

            assertNotNull(option.create());
        }
    }
}