package com.drakosanctis.auriga;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OdometryManagerTest {

    private static final float EPSILON = 0.0001f;

    @Test
    public void invalidDistanceDoesNotPoisonExistingSmootherState() {
        OdometryManager manager = new OdometryManager();

        assertTrue(Float.isNaN(manager.smoothDistance(0, Float.NaN, 1.0f, 0.0f)));

        assertEquals(3.0f, manager.smoothDistance(0, 3.0f, 1.0f, 0.0f), EPSILON);
        assertEquals(3.0f,
                manager.smoothDistance(0, Float.POSITIVE_INFINITY, 1.0f, 0.0f), EPSILON);
        assertEquals(3.0f,
                manager.smoothDistance(0, 4.0f, Float.NaN, 0.0f), EPSILON);
        assertEquals(3.0f,
                manager.smoothDistance(0, 4.0f, 1.0f, Float.POSITIVE_INFINITY), EPSILON);

        assertFalse(Float.isNaN(manager.smoothDistance(0, 3.5f, 1.0f, 0.0f)));
    }

    @Test
    public void distanceConfidenceIsClampedAndCannotOvershootMeasurement() {
        OdometryManager manager = new OdometryManager();
        manager.smoothDistance(1, 2.0f, 1.0f, 0.0f);

        float smoothed = manager.smoothDistance(1, 8.0f, 5.0f, 0.0f);

        assertTrue("smoothing should not overshoot the new measurement", smoothed <= 8.0f);
        assertTrue("smoothing should move toward the new measurement", smoothed > 2.0f);
    }

    @Test
    public void invalidBearingDoesNotPoisonExistingSmootherState() {
        OdometryManager manager = new OdometryManager();

        assertTrue(Float.isNaN(manager.smoothBearing(2, Float.NaN, 1.0f, 0.0f)));
        assertEquals(-12.0f, manager.smoothBearing(2, -12.0f, 1.0f, 0.0f), EPSILON);
        assertEquals(-12.0f,
                manager.smoothBearing(2, 45.0f, Float.NaN, 0.0f), EPSILON);
        assertEquals(-12.0f,
                manager.smoothBearing(2, 45.0f, 1.0f, Float.POSITIVE_INFINITY), EPSILON);
    }

    @Test
    public void resetClearsPreviousValues() {
        OdometryManager manager = new OdometryManager();
        manager.smoothDistance(0, 2.0f);
        manager.smoothBearing(0, 4.0f, 1.0f, 0.0f);

        manager.reset();

        assertTrue(Float.isNaN(manager.smoothDistance(0, Float.NaN, 1.0f, 0.0f)));
        assertTrue(Float.isNaN(manager.smoothBearing(0, Float.NaN, 1.0f, 0.0f)));
    }
}
