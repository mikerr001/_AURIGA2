package com.drakosanctis.auriga;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * OdometryManager: The "GhostAnchor(TM)" of the engine.
 * Tracks feature points between frames to stabilize distance and
 * bearing against hand tremors and camera movement.
 *
 * Three responsibilities:
 *   1. Distance smoothing per-column with an ADAPTIVE low-pass filter.
 *      alpha rises briefly when the reading has jumped outside N sigma
 *      of its running variance (catch up to real object movement) and
 *      falls back when the reading is steady (reject per-frame noise).
 *   2. Bearing smoothing with an independent smoother. Previously the
 *      bearing readout came out raw and flickered more than distance.
 *   3. Visual-shift centroid (GhostAnchor proper) that tells the main
 *      loop when the camera itself is moving fast. Callers multiply
 *      that into the smoother update so a single panning frame does
 *      not poison the low-pass state.
 */
public class OdometryManager {

    // --- Low-pass state per column for distance ---
    private static final int COLS = 3;
    private static final float BASE_ALPHA = 0.35f;      // steady-state weight (was 0.2 — too sluggish)
    private static final float FAST_ALPHA = 0.70f;      // when reading jumped (was 0.5)
    private static final float SIGMA_TRIGGER = 2.5f;   // stddev multiples to trigger FAST
    private static final float VAR_ALPHA = 0.1f;       // how fast variance updates
    private static final float MAX_SHIFT_TRUSTED = 25.0f; // px/frame before we freeze (was 8 — froze on normal walking)

    private final float[] smoothedDistances = {0.0f, 0.0f, 0.0f};
    private final float[] distanceVariances = {1.0f, 1.0f, 1.0f};
    private final boolean[] distanceInit = {false, false, false};

    // --- Low-pass state per column for bearing (degrees) ---
    private final float[] smoothedBearings = {0.0f, 0.0f, 0.0f};
    private final float[] bearingVariances = {1.0f, 1.0f, 1.0f};
    private final boolean[] bearingInit = {false, false, false};

    // --- Visual-shift state (GhostAnchor brightness centroid) ---
    private float lastAnchorX = -1.0f;
    private float lastAnchorY = -1.0f;
    private float lastShiftMagnitude = 0.0f;

    public OdometryManager() { }

    /**
     * smoothDistance: Applies an adaptive low-pass filter to the raw
     * distance reading for a specific column.
     *
     * confidence  in [0, 1]. Values below ~0.3 cause the update to be
     *             SKIPPED entirely (returns the previous smoothed value)
     *             so a bad detection does not poison the filter.
     * shiftMag    magnitude of the GhostAnchor visual shift for the
     *             current frame. Large shift -> camera moving fast ->
     *             temporarily freeze the filter (returns previous value)
     *             because any measured distance delta is dominated by
     *             camera motion, not real object motion.
     */
    public float smoothDistance(int column, float newDistance, float confidence, float shiftMag) {
        if (column < 0 || column >= COLS) return Float.NaN;
        // Never let invalid detector output or confidence poison persistent
        // smoothing state. An uninitialised column has no safe prior value.
        if (!Float.isFinite(newDistance) || !Float.isFinite(confidence)
                || !Float.isFinite(shiftMag)) {
            return distanceInit[column] ? smoothedDistances[column] : Float.NaN;
        }
        confidence = Math.max(0.0f, Math.min(1.0f, confidence));

        // Reject very low-confidence frames — threshold lowered from 0.3 to 0.15
        // so moderate-contrast scenes still contribute to the smoother.
        if (confidence < 0.15f && distanceInit[column]) {
            return smoothedDistances[column];
        }
        // Freeze while camera is panning hard.
        if (shiftMag > MAX_SHIFT_TRUSTED && distanceInit[column]) {
            return smoothedDistances[column];
        }

        if (!distanceInit[column]) {
            smoothedDistances[column] = newDistance;
            distanceVariances[column] = 1.0f;
            distanceInit[column] = true;
            return newDistance;
        }

        float delta = newDistance - smoothedDistances[column];
        float deltaSq = delta * delta;
        // Running variance estimate -- cheaper than true Welford and
        // more than accurate enough for an adaptive-alpha trigger.
        distanceVariances[column] =
                VAR_ALPHA * deltaSq + (1f - VAR_ALPHA) * distanceVariances[column];
        float sigma = (float) Math.sqrt(distanceVariances[column]);

        float alpha = (Math.abs(delta) > SIGMA_TRIGGER * sigma) ? FAST_ALPHA : BASE_ALPHA;
        // Confidence modulates alpha but floor raised to 0.5 (was 0.3) so
        // accepted frames always contribute at least half of BASE_ALPHA weight.
        alpha *= Math.max(0.5f, confidence);

        smoothedDistances[column] =
                alpha * newDistance + (1f - alpha) * smoothedDistances[column];
        return smoothedDistances[column];
    }

    /**
     * Back-compat overload: callers that have not yet threaded confidence
     * through use 1.0 confidence and 0 shift (i.e. classic BASE_ALPHA).
     */
    public float smoothDistance(int column, float newDistance) {
        return smoothDistance(column, newDistance, 1.0f, 0.0f);
    }

    /**
     * smoothBearing: Same adaptive strategy, applied to bearing degrees.
     * Bearing jumps frame-to-frame were the single largest visual jitter
     * source because bearing previously had no smoother at all.
     */
    public float smoothBearing(int column, float newBearingDeg, float confidence, float shiftMag) {
        if (column < 0 || column >= COLS) return Float.NaN;
        // Reject non-finite bearing/quality inputs before they can enter
        // the smoother; confidence is bounded so alpha cannot exceed 1.
        if (!Float.isFinite(newBearingDeg) || !Float.isFinite(confidence)
                || !Float.isFinite(shiftMag)) {
            return bearingInit[column] ? smoothedBearings[column] : Float.NaN;
        }
        confidence = Math.max(0.0f, Math.min(1.0f, confidence));

        if (confidence < 0.15f && bearingInit[column]) {
            return smoothedBearings[column];
        }
        if (shiftMag > MAX_SHIFT_TRUSTED && bearingInit[column]) {
            return smoothedBearings[column];
        }

        if (!bearingInit[column]) {
            smoothedBearings[column] = newBearingDeg;
            bearingVariances[column] = 1.0f;
            bearingInit[column] = true;
            return newBearingDeg;
        }

        float delta = newBearingDeg - smoothedBearings[column];
        float deltaSq = delta * delta;
        bearingVariances[column] =
                VAR_ALPHA * deltaSq + (1f - VAR_ALPHA) * bearingVariances[column];
        float sigma = (float) Math.sqrt(bearingVariances[column]);

        float alpha = (Math.abs(delta) > SIGMA_TRIGGER * sigma) ? FAST_ALPHA : BASE_ALPHA;
        alpha *= Math.max(0.5f, confidence);

        smoothedBearings[column] =
                alpha * newBearingDeg + (1f - alpha) * smoothedBearings[column];
        return smoothedBearings[column];
    }

    /**
     * calculateVisualShift: GhostAnchor(TM) logic.
     * Uses a "Center of Brightness" (Centroid) instead of single brightest
     * pixel so spurious lamps don't lock the anchor. Returns the delta
     * (dx, dy) in pixels vs. the previous frame; the magnitude is also
     * cached in lastShiftMagnitude for callers to read without recomputing.
     */
    public float[] calculateVisualShift(Bitmap frame) {
        // Camera pipelines may briefly deliver null/degenerate frames during
        // startup, rotation, or teardown. Fail closed rather than dereference
        // null or read outside the bitmap.
        if (frame == null || frame.isRecycled()
                || frame.getWidth() <= 0 || frame.getHeight() <= 0) {
            lastAnchorX = -1.0f;
            lastAnchorY = -1.0f;
            lastShiftMagnitude = Float.POSITIVE_INFINITY;
            return new float[]{Float.NaN, Float.NaN};
        }
        int width = frame.getWidth();
        int height = frame.getHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        int searchRadius = 30;
        int startX = Math.max(0, centerX - searchRadius);
        int endX = Math.min(width, centerX + searchRadius);
        int startY = Math.max(0, centerY - searchRadius);
        int endY = Math.min(height, centerY + searchRadius);

        float sumX = 0, sumY = 0, sumWeight = 0;

        for (int x = startX; x < endX; x++) {
            for (int y = startY; y < endY; y++) {
                int pixel = frame.getPixel(x, y);
                int brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                if (brightness > 100) {
                    float weight = (float) Math.pow(brightness / 255.0, 2);
                    sumX += x * weight;
                    sumY += y * weight;
                    sumWeight += weight;
                }
            }
        }

        // No usable visual texture means tracking is unavailable, not
        // that the camera stayed still. Reset the anchor so the next valid
        // frame starts a fresh baseline instead of fabricating zero motion.
        if (!Float.isFinite(sumWeight) || sumWeight <= 0.0f) {
            lastAnchorX = -1.0f;
            lastAnchorY = -1.0f;
            lastShiftMagnitude = Float.POSITIVE_INFINITY;
            return new float[]{Float.NaN, Float.NaN};
        }
        float bestX = sumX / sumWeight;
        float bestY = sumY / sumWeight;

        float deltaX = 0, deltaY = 0;
        if (lastAnchorX != -1.0f) {
            deltaX = bestX - lastAnchorX;
            deltaY = bestY - lastAnchorY;
        }

        lastAnchorX = bestX;
        lastAnchorY = bestY;
        lastShiftMagnitude = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        return new float[]{ deltaX, deltaY };
    }

    public float getLastShiftMagnitude() {
        return lastShiftMagnitude;
    }

    public void reset() {
        for (int i = 0; i < COLS; i++) {
            smoothedDistances[i] = 0.0f;
            distanceVariances[i] = 1.0f;
            distanceInit[i] = false;
            smoothedBearings[i] = 0.0f;
            bearingVariances[i] = 1.0f;
            bearingInit[i] = false;
        }
        lastAnchorX = -1.0f;
        lastAnchorY = -1.0f;
        lastShiftMagnitude = 0.0f;
    }
}
