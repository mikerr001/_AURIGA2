package com.drakosanctis.auriga;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class FiducialLUTTest {

    @Test
    public void constructorDefaultsCannotAuthorizeMetricRange() {
        FiducialLUT lut = new FiducialLUT();

        assertFalse(lut.isMetricScaleTrusted());
        assertEquals(-1.0f, lut.getDistanceFromWidth(100.0f), 0.0001f);
        assertEquals(-1.0f, lut.getDistanceFromRow(200.0f), 0.0001f);
    }

    @Test
    public void ordinaryLoadedProfileRemainsUnapproved() {
        FiducialLUT lut = new FiducialLUT();
        List<FiducialLUT.TrainingPoint> points = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            points.add(new FiducialLUT.TrainingPoint(
                    i * 0.5f, 800.0f / i, 280.0f - i * 10.0f));
        }

        lut.loadProfile(points, "test-candidate");

        assertFalse(lut.isMetricScaleTrusted());
        assertEquals(-1.0f, lut.getDistanceFromWidth(100.0f), 0.0001f);
    }
}
