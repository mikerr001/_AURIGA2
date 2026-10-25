package com.drakosanctis.auriga;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Pure bit-distance tests for the local visual-place descriptor matcher. */
public class VisualAnchorMapTest {
    @Test
    public void identicalDescriptorsHaveZeroDistance() {
        assertEquals(0, VisualAnchorMap.hammingDistance(0x1234L, 0x1234L));
    }

    @Test
    public void allDifferentBitsHaveDistanceSixtyFour() {
        assertEquals(64, VisualAnchorMap.hammingDistance(0L, -1L));
    }

    @Test
    public void oneChangedBitHasDistanceOne() {
        assertEquals(1, VisualAnchorMap.hammingDistance(0L, 1L));
    }
}
