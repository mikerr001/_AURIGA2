package com.drakosanctis.auriga;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Persists exact COCO classes used by the native YOLO locator.
 * Selection labels intentionally match assets/coco_labels.txt and Detection.label.
 */
public final class TargetStore {
    public static final String PREF_KEY = "auriga_targets";
    public static final String LEGACY_PREF_KEY = "auriga_legacy_targets";
    public static final String CATEGORY_ANY = "_any_";

    public static final String[] CATEGORIES = {
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
        "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
        "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
        "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
        "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
        "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
        "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
        "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    };

    public static final String[] PEOPLE = {"person"};
    public static final String[] TRANSPORT = {
        "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
        "traffic light", "stop sign", "parking meter"
    };
    public static final String[] FOOD = {
        "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza",
        "donut", "cake", "bottle", "wine glass", "cup", "bowl"
    };
    public static final String[] HOME = {
        "chair", "couch", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote",
        "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
        "clock", "book", "vase"
    };
    public static final String[] PERSONAL = {
        "backpack", "umbrella", "handbag", "tie", "suitcase", "cell phone", "book", "bottle"
    };

    public static final String[] GROUP_NAMES = {
        "People & animals", "Vehicles & street", "Personal items & tech",
        "Sports & outdoors", "Food & drink", "Home & everyday"
    };
    public static final String[][] GROUPS = {
        {"person", "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "teddy bear"},
        {"bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench"},
        {"backpack", "umbrella", "handbag", "tie", "suitcase", "cell phone", "laptop", "mouse", "remote", "keyboard", "book", "clock", "scissors", "hair drier", "toothbrush"},
        {"frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket"},
        {"bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake"},
        {"chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "microwave", "oven", "toaster", "sink", "refrigerator", "vase"}
    };

    private TargetStore() {}

    public static Set<String> read(Context ctx) {
        Set<String> selection = readKey(ctx, PREF_KEY, true);
        // Older builds stored ML Kit labels in this key. They are not YOLO/COCO
        // classes; reset stale selections instead of silently filtering everything out.
        if (!selection.contains(CATEGORY_ANY)) {
            for (String selected : selection) {
                boolean known = false;
                for (String label : CATEGORIES) {
                    if (label.equalsIgnoreCase(selected)) { known = true; break; }
                }
                if (!known) {
                    selection.clear();
                    selection.add(CATEGORY_ANY);
                    write(ctx, selection);
                    break;
                }
            }
        }
        return selection;
    }

    /** The legacy ML Kit HUD has a different label taxonomy; keep its settings separate. */
    public static Set<String> readLegacy(Context ctx) {
        return readKey(ctx, LEGACY_PREF_KEY, true);
    }

    private static Set<String> readKey(Context ctx, String key, boolean defaultAny) {
        SharedPreferences prefs = ctx.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String csv = prefs.getString(key, null);
        Set<String> out = new LinkedHashSet<>();
        if (csv == null || csv.trim().isEmpty()) {
            if (defaultAny) out.add(CATEGORY_ANY);
            return out;
        }
        for (String token : csv.split(",")) {
            String value = token.trim();
            if (!value.isEmpty()) out.add(value);
        }
        if (out.isEmpty() && defaultAny) out.add(CATEGORY_ANY);
        return out;
    }

    public static void write(Context ctx, Set<String> selection) {
        writeKey(ctx, PREF_KEY, selection);
    }

    public static void writeLegacy(Context ctx, Set<String> selection) {
        writeKey(ctx, LEGACY_PREF_KEY, selection);
    }

    private static void writeKey(Context ctx, String key, Set<String> selection) {
        SharedPreferences prefs = ctx.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> normalized = selection == null || selection.isEmpty()
                ? new LinkedHashSet<>(Arrays.asList(CATEGORY_ANY)) : selection;
        StringBuilder csv = new StringBuilder();
        for (String value : normalized) {
            if (value == null || value.trim().isEmpty()) continue;
            if (csv.length() > 0) csv.append(',');
            csv.append(value.trim());
        }
        prefs.edit().putString(key, csv.length() == 0 ? CATEGORY_ANY : csv.toString()).apply();
    }

    public static boolean matches(Set<String> selection, String label) {
        if (selection == null || selection.isEmpty() || selection.contains(CATEGORY_ANY)) return true;
        if (label == null) return false;
        for (String chosen : selection) {
            if (chosen != null && chosen.equalsIgnoreCase(label)) return true;
        }
        return false;
    }

    /** Legacy ML Kit labels are deliberately isolated from COCO class filters. */
    public static boolean matchesLegacy(Set<String> selection, String label) {
        if (selection == null || selection.isEmpty() || selection.contains(CATEGORY_ANY)) return true;
        if (label == null) return false;
        for (String chosen : selection) if (chosen != null && chosen.equalsIgnoreCase(label)) return true;
        return false;
    }

    public static String summary(Set<String> selection) {
        if (selection == null || selection.isEmpty() || selection.contains(CATEGORY_ANY)) return "Any object";
        if (selection.size() == 1) {
            String value = selection.iterator().next();
            return value.length() == 0 ? "Any object" : value.substring(0, 1).toUpperCase() + value.substring(1);
        }
        return selection.size() + " targets";
    }
}
