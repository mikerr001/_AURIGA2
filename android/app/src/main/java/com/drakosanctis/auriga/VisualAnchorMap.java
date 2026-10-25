package com.drakosanctis.auriga;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Local visual place-memory prototype.
 *
 * It stores compact 8x8 brightness descriptors and transitions between
 * recurring views. It does not estimate camera pose, metric coordinates,
 * object distance, or a geometrically accurate 3D world. Those outputs remain
 * unavailable until a validated visual-odometry/scale pipeline exists.
 */
public final class VisualAnchorMap {
    private static final String TAG = "VisualAnchorMap";
    private static final String PREFS = "auriga_visual_anchor_map";
    private static final String KEY_MAP = "map_v1";
    private static final String KEY_ENABLED = "enabled";
    private static final int GRID = 8;
    private static final int MAX_ANCHORS = 120;
    private static final int MAX_HAMMING_DISTANCE = 17;
    private static final long MIN_OBSERVATION_INTERVAL_MS = 700L;

    private static volatile VisualAnchorMap instance;

    private final SharedPreferences prefs;
    private final List<Anchor> anchors = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private long nextId = 1L;
    private long lastAnchorId = -1L;
    private long lastObservationAt;
    private long lastPersistAt;
    private long observations;

    private static final class Descriptor {
        final long bits;
        final int mean;
        final int contrast;
        Descriptor(long bits, int mean, int contrast) {
            this.bits = bits;
            this.mean = mean;
            this.contrast = contrast;
        }
    }

    private static final class Anchor {
        long id;
        long signature;
        int mean;
        int contrast;
        int visits;
        long lastSeen;
    }

    private static final class Edge {
        long from;
        long to;
        int traversals;
    }

    public static VisualAnchorMap getInstance(Context context) {
        if (instance == null) {
            synchronized (VisualAnchorMap.class) {
                if (instance == null) instance = new VisualAnchorMap(context.getApplicationContext());
            }
        }
        return instance;
    }

    private VisualAnchorMap(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    /**
     * Observe a camera frame and update the local visual-place graph.
     * Call from the camera analysis thread; work is bounded to 64 pixel samples.
     *
     * @return matched/created local anchor ID, or -1 when throttled/capacity reached.
     */
    public synchronized long observe(Bitmap frame) {
        if (!prefs.getBoolean(KEY_ENABLED, false)) return -1L;
        if (frame == null || frame.isRecycled() || frame.getWidth() < 8 || frame.getHeight() < 8) {
            return -1L;
        }
        long now = System.currentTimeMillis();
        if (now - lastObservationAt < MIN_OBSERVATION_INTERVAL_MS) return -1L;
        lastObservationAt = now;
        observations++;

        Descriptor descriptor = describe(frame);
        Anchor best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Anchor candidate : anchors) {
            int distance = hammingDistance(descriptor.bits, candidate.signature);
            if (distance > MAX_HAMMING_DISTANCE) continue;
            if (Math.abs(descriptor.mean - candidate.mean) > 55) continue;
            if (Math.abs(descriptor.contrast - candidate.contrast) > 60) continue;
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }

        boolean changed = false;
        if (best == null) {
            if (anchors.size() >= MAX_ANCHORS) {
                if (observations % 10 == 0) persist();
                return -1L;
            }
            best = new Anchor();
            best.id = nextId++;
            best.signature = descriptor.bits;
            best.mean = descriptor.mean;
            best.contrast = descriptor.contrast;
            best.visits = 0;
            anchors.add(best);
            changed = true;
        }

        best.visits++;
        best.lastSeen = now;
        if (lastAnchorId >= 0 && lastAnchorId != best.id) {
            Edge edge = findEdge(lastAnchorId, best.id);
            if (edge == null) {
                edge = new Edge();
                edge.from = lastAnchorId;
                edge.to = best.id;
                edge.traversals = 1;
                edges.add(edge);
                changed = true;
            } else {
                edge.traversals++;
            }
        }
        if (lastAnchorId != best.id) changed = true;
        lastAnchorId = best.id;

        if (changed || now - lastPersistAt > 5000L || observations % 8 == 0) persist();
        return best.id;
    }

    public synchronized boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public synchronized void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public synchronized Snapshot snapshot() {
        List<AnchorView> anchorViews = new ArrayList<>();
        for (Anchor a : anchors) {
            anchorViews.add(new AnchorView(a.id, a.visits, a.lastSeen));
        }
        List<EdgeView> edgeViews = new ArrayList<>();
        for (Edge e : edges) {
            edgeViews.add(new EdgeView(e.from, e.to, e.traversals));
        }
        return new Snapshot(anchorViews, edgeViews, observations, lastAnchorId, lastObservationAt);
    }

    /** Start a new camera session without drawing a false edge from the previous session. */
    public synchronized void beginSession() {
        lastAnchorId = -1L;
        lastObservationAt = 0L;
    }

    public synchronized void clear() {
        anchors.clear();
        edges.clear();
        nextId = 1L;
        lastAnchorId = -1L;
        lastObservationAt = 0L;
        lastPersistAt = 0L;
        observations = 0L;
        prefs.edit().remove(KEY_MAP).apply();
    }

    public static int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    private static Descriptor describe(Bitmap frame) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        int[] values = new int[GRID * GRID];
        long sum = 0L;
        int index = 0;
        for (int gy = 0; gy < GRID; gy++) {
            int y = Math.min(height - 1, (int) (((gy + 0.5f) / GRID) * height));
            for (int gx = 0; gx < GRID; gx++) {
                int x = Math.min(width - 1, (int) (((gx + 0.5f) / GRID) * width));
                int pixel = frame.getPixel(x, y);
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;
                int luminance = (r * 299 + g * 587 + b * 114) / 1000;
                values[index++] = luminance;
                sum += luminance;
            }
        }
        int mean = (int) (sum / values.length);
        long deviation = 0L;
        long bits = 0L;
        for (int i = 0; i < values.length; i++) {
            if (values[i] >= mean) bits |= (1L << i);
            deviation += Math.abs(values[i] - mean);
        }
        int contrast = (int) (deviation / values.length);
        return new Descriptor(bits, mean, contrast);
    }

    private Edge findEdge(long from, long to) {
        for (Edge edge : edges) {
            if (edge.from == from && edge.to == to) return edge;
        }
        return null;
    }

    private void load() {
        String raw = prefs.getString(KEY_MAP, null);
        if (raw == null || raw.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(raw);
            nextId = Math.max(1L, root.optLong("nextId", 1L));
            lastAnchorId = root.optLong("lastAnchorId", -1L);
            observations = root.optLong("observations", 0L);
            lastObservationAt = root.optLong("lastObservationAt", 0L);

            JSONArray savedAnchors = root.optJSONArray("anchors");
            if (savedAnchors != null) {
                for (int i = 0; i < savedAnchors.length() && anchors.size() < MAX_ANCHORS; i++) {
                    JSONObject item = savedAnchors.optJSONObject(i);
                    if (item == null) continue;
                    Anchor a = new Anchor();
                    a.id = item.optLong("id", -1L);
                    a.signature = Long.parseLong(item.optString("signature", "0"));
                    a.mean = item.optInt("mean", 0);
                    a.contrast = item.optInt("contrast", 0);
                    a.visits = item.optInt("visits", 0);
                    a.lastSeen = item.optLong("lastSeen", 0L);
                    if (a.id >= 0) anchors.add(a);
                }
            }

            JSONArray savedEdges = root.optJSONArray("edges");
            if (savedEdges != null) {
                for (int i = 0; i < savedEdges.length(); i++) {
                    JSONObject item = savedEdges.optJSONObject(i);
                    if (item == null) continue;
                    Edge e = new Edge();
                    e.from = item.optLong("from", -1L);
                    e.to = item.optLong("to", -1L);
                    e.traversals = item.optInt("traversals", 0);
                    if (e.from >= 0 && e.to >= 0 && e.from != e.to) edges.add(e);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Saved visual map was unreadable; starting an empty local map", t);
            anchors.clear();
            edges.clear();
            nextId = 1L;
            lastAnchorId = -1L;
            observations = 0L;
        }
    }

    private void persist() {
        try {
            JSONObject root = new JSONObject();
            root.put("nextId", nextId);
            root.put("lastAnchorId", lastAnchorId);
            root.put("observations", observations);
            root.put("lastObservationAt", lastObservationAt);
            JSONArray savedAnchors = new JSONArray();
            for (Anchor a : anchors) {
                JSONObject item = new JSONObject();
                item.put("id", a.id);
                item.put("signature", Long.toString(a.signature));
                item.put("mean", a.mean);
                item.put("contrast", a.contrast);
                item.put("visits", a.visits);
                item.put("lastSeen", a.lastSeen);
                savedAnchors.put(item);
            }
            JSONArray savedEdges = new JSONArray();
            for (Edge e : edges) {
                JSONObject item = new JSONObject();
                item.put("from", e.from);
                item.put("to", e.to);
                item.put("traversals", e.traversals);
                savedEdges.put(item);
            }
            root.put("anchors", savedAnchors);
            root.put("edges", savedEdges);
            prefs.edit().putString(KEY_MAP, root.toString()).apply();
            lastPersistAt = System.currentTimeMillis();
        } catch (Throwable t) {
            Log.w(TAG, "Could not persist local visual map", t);
        }
    }

    public static final class Snapshot {
        public final List<AnchorView> anchors;
        public final List<EdgeView> edges;
        public final long observations;
        public final long currentAnchorId;
        public final long lastObservationAt;
        Snapshot(List<AnchorView> anchors, List<EdgeView> edges, long observations,
                 long currentAnchorId, long lastObservationAt) {
            this.anchors = anchors;
            this.edges = edges;
            this.observations = observations;
            this.currentAnchorId = currentAnchorId;
            this.lastObservationAt = lastObservationAt;
        }
    }

    public static final class AnchorView {
        public final long id;
        public final int visits;
        public final long lastSeen;
        AnchorView(long id, int visits, long lastSeen) {
            this.id = id;
            this.visits = visits;
            this.lastSeen = lastSeen;
        }
    }

    public static final class EdgeView {
        public final long from;
        public final long to;
        public final int traversals;
        EdgeView(long from, long to, int traversals) {
            this.from = from;
            this.to = to;
            this.traversals = traversals;
        }
    }
}
