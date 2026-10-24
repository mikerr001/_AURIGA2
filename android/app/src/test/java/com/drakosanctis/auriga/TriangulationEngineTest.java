package com.drakosanctis.auriga;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TriangulationEngineTest {

    @Test
    public void metricRangeAndSuspendedHeightFailClosedWithoutValidatedSolver() {
        TriangulationEngine engine = new TriangulationEngine(null, null);

        assertNull(engine.calculateGroundDistance(200, 320, 40.0f));
        assertNull(engine.calculateSuspendedHeight(120, 320));
    }

    @Test
    public void relativeBearingIsUnavailableWithoutCameraCalibration() {
        TriangulationEngine engine = new TriangulationEngine(null, null);
        engine.setFrameSize(640, 480);

        assertTrue(Float.isNaN(engine.calculateRelativeBearingDeg(320)));
        assertTrue(Float.isNaN(engine.calculateRelativeBearingDeg(-1)));
        assertTrue(Float.isNaN(engine.calculateRelativeBearingDeg(640)));
    }

    @Test
    public void invalidFrameDimensionsMakeBearingUnavailable() {
        TriangulationEngine engine = new TriangulationEngine(null, null);
        engine.setFrameSize(0, 480);

        assertTrue(Float.isNaN(engine.calculateRelativeBearingDeg(0)));
    }
}
