/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CoverageMapTest {

    @Test
    void overlappingAndTouchingRangesAreMerged() {
        CoverageMap map = new CoverageMap();
        map.markCovered(0, 10);
        map.markCovered(10, 20);
        map.markCovered(30, 40);
        map.markCovered(15, 35);

        assertEquals(40, map.firstGap(0, 100));
        assertEquals(-1, map.firstGap(0, 40));
    }

    @Test
    void findsTheFirstUncoveredPositionFromWhereYouStart() {
        CoverageMap map = new CoverageMap();
        map.markCovered(0, 10);
        map.markCovered(20, 30);

        assertEquals(10, map.firstGap(5, 100));
        assertEquals(12, map.firstGap(12, 100));
        assertEquals(30, map.firstGap(25, 100));
        assertEquals(-1, map.firstGap(0, 10));
    }

    @Test
    void wrapsRoundToTheStartOnceTheEndIsDone() {
        CoverageMap map = new CoverageMap();
        map.markCovered(10, 20);

        assertEquals(0, map.nextToDo(10, 20));
        map.markCovered(0, 10);
        assertEquals(-1, map.nextToDo(10, 20));
    }

    @Test
    void aGapEndsWhereTheNextCoveredRangeStarts() {
        CoverageMap map = new CoverageMap();
        map.markCovered(10, 20);

        assertEquals(10, map.gapEnd(0, 100));
        assertEquals(100, map.gapEnd(20, 100));
    }

    @Test
    void emptyOrBackwardsRangesChangeNothing() {
        CoverageMap map = new CoverageMap();
        map.markCovered(5, 5);
        map.markCovered(9, 3);

        assertEquals(0, map.firstGap(0, 100));
    }
}