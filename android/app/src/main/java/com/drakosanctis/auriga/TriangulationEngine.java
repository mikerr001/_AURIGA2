package com.drakosanctis.auriga;

/**
 * TriangulationEngine: The mathematical core of DrakoSanctis.
 * Exposes the TruePath(TM), SkyShield(TM), and relative-bearing API.
 * Metric range/height paths fail closed until a validated camera-only
 * plane-intersection and ground-contact solver is integrated.
 * Relative bearing is optical-axis-relative, never compass/world bearing.
 */
public class TriangulationEngine {

    private final FiducialLUT lut;
    private final HardwareHAL hal;
    private int frameWidth = 640;
    private int frameHeight = 480;

    public TriangulationEngine(FiducialLUT lut, HardwareHAL hal) {
        this.lut = lut;
        this.hal = hal;
    }

    public void setFrameSize(int width, int height) {
        this.frameWidth = width;
        this.frameHeight = height;
    }

    /**
     * SpatialOutput: Holds the final calculated metrics for an object.
     *
     * sanityScore is 1.0 when the row-based and width-based distance
     * estimators agree, and drops toward 0 as they diverge. The main
     * loop multiplies this into the detector's confidence before
     * passing the reading to the smoother so disagreeing frames are
     * effectively discarded without special-casing in the caller.
     */
    public static class SpatialOutput {
        public float distanceM;
        public float heightM;
        public float bearingDeg;
        public String signature; // WALL / POLE / OBJECT
        public float sanityScore;

        SpatialOutput(float d, float h, float b, String s, float sanity) {
            this.distanceM = d;
            this.heightM = h;
            this.bearingDeg = b;
            this.signature = s;
            this.sanityScore = sanity;
        }
    }

    /**
     * Legacy row-only ranging entry point. A detector bounding-box edge does
     * not prove object/floor contact. Do not return a metric range until a
     * validated plane/contact solver supplies the required scene evidence.
     */
    public SpatialOutput calculateGroundDistance(int baseY, int centerX, float observedWidthPx) {
        return null;
    }

    /** Relative bearing from the optical axis only; never a compass heading. */
    public float calculateRelativeBearingDeg(int centerX) {
        if (frameWidth <= 0 || centerX < 0 || centerX >= frameWidth || hal == null) {
            return Float.NaN;
        }
        float focalLengthPx = hal.getFocalLengthPx(frameWidth);
        if (!Float.isFinite(focalLengthPx) || focalLengthPx <= 0f) return Float.NaN;
        float deltaX = centerX - frameWidth / 2.0f;
        return (float) Math.toDegrees(Math.atan(deltaX / focalLengthPx));
    }

    /**
     * Suspended-object height remains unavailable until the scene has a
     * validated ground-contact point and camera-only ray/plane intersection.
     */
    public SpatialOutput calculateSuspendedHeight(int hangY, int centerX) {
        return null;
    }
}
