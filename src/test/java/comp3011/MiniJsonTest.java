/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MiniJsonTest {

    @Test
    void readsTheServersReplyShape() {
        Object parsed = MiniJson.parse("""
                {"segments":[{"start":0.0,"end":2.5,"text":"Hello there."},
                             {"start":3,"end":4.25e0,"text":"Second."}]}
                """);

        List<?> segments = (List<?>) ((Map<?, ?>) parsed).get("segments");
        assertEquals(2, segments.size());
        assertEquals("Hello there.", ((Map<?, ?>) segments.get(0)).get("text"));
        assertEquals(2.5, ((Map<?, ?>) segments.get(0)).get("end"));
        assertEquals(3.0, ((Map<?, ?>) segments.get(1)).get("start"));
        assertEquals(4.25, ((Map<?, ?>) segments.get(1)).get("end"));
    }

    @Test
    void readsEscapesInStrings() {
        Object parsed = MiniJson.parse("\"She said \\\"hi\\\"\\nthen left \\u00e9\"");

        assertEquals("She said \"hi\"\nthen left \u00e9", parsed);
    }

    @Test
    void readsLiteralsAndEmptyContainers() {
        Map<?, ?> parsed = (Map<?, ?>) MiniJson.parse("{\"a\":true,\"b\":false,\"c\":null,\"d\":[],\"e\":{}}");

        assertEquals(Boolean.TRUE, parsed.get("a"));
        assertEquals(Boolean.FALSE, parsed.get("b"));
        assertEquals(null, parsed.get("c"));
        assertEquals(List.of(), parsed.get("d"));
        assertEquals(Map.of(), parsed.get("e"));
    }

    @Test
    void brokenJsonIsRejectedWithAnException() {
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"segments\":["));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\" 1}"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("[1,2] extra"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("\"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse(""));
    }

    @Test
    void absurdNestingIsRejectedInsteadOfOverflowingTheStack() {
        String deep = "[".repeat(10_000) + "]".repeat(10_000);

        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse(deep));
    }
}