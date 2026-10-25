package com.drakosanctis.auriga;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

/** Native controls and local status for Auriga's opt-in visual place-memory prototype. */
public final class SpatialMapActivity extends Activity {
    private static final int BG = Color.rgb(244, 247, 249);
    private static final int SURFACE = Color.WHITE;
    private static final int INK = Color.rgb(20, 39, 49);
    private static final int MUTED = Color.rgb(88, 108, 119);
    private static final int BRAND = Color.rgb(0, 112, 126);
    private static final int BORDER = Color.rgb(219, 229, 233);

    private VisualAnchorMap map;
    private LinearLayout body;
    private TextView enabledStatus;
    private Button toggleButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        map = VisualAnchorMap.getInstance(this);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | (BuildVersion.hasLightNavBar() ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(body);
        setContentView(scroll);
        buildStaticContent();
        refreshMap();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshMap();
    }

    private void buildStaticContent() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 31, BRAND, true);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Go back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(44)));
        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(8);
        header.addView(titleStack, titleParams);
        titleStack.addView(text("Visual place memory", 20, INK, true));
        titleStack.addView(text("LOCAL · EXPERIMENTAL", 9, MUTED, true));
        body.addView(header);

        TextView description = text("Opt in to save compact visual signatures from the live camera and link recurring views into a local place graph. Nothing is uploaded.", 14, MUTED, false);
        description.setLineSpacing(dp(3), 1f);
        body.addView(description, margins(-1, -2, 0, 22, 0, 0));

        LinearLayout enableCard = card();
        body.addView(enableCard, margins(-1, -2, 0, 0, 0, 0));
        enabledStatus = text("", 14, INK, true);
        enableCard.addView(enabledStatus);
        TextView optInNote = text("Mapping starts only after you enable it here and return to the live camera. It stops when the live camera activity is not running.", 12, MUTED, false);
        optInNote.setLineSpacing(dp(2), 1f);
        enableCard.addView(optInNote, margins(-1, -2, 0, 8, 0, 0));
        toggleButton = button("", BRAND, Color.WHITE, Color.TRANSPARENT);
        enableCard.addView(toggleButton, margins(-1, dp(48), 0, 12, 0, 0));
        toggleButton.setOnClickListener(v -> {
            map.setEnabled(!map.isEnabled());
            refreshMap();
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(actions, margins(-1, -2, 0, 14, 0, 0));
        Button scan = button("Open live camera", SURFACE, BRAND, BORDER);
        actions.addView(scan, new LinearLayout.LayoutParams(0, dp(48), 1f));
        scan.setOnClickListener(v -> startActivity(new Intent(this, LocatorActivity.class)));
        View gap = new View(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        Button clear = button("Clear map", SURFACE, Color.rgb(155, 48, 48), BORDER);
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(48), 1f));
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Clear local visual map?")
                .setMessage("This permanently deletes the saved visual anchors and their connections from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear map", (dialog, which) -> {
                    map.clear();
                    refreshMap();
                }).show());

        TextView graphTitle = text("Saved places", 20, INK, true);
        body.addView(graphTitle, margins(-1, -2, 0, 28, 0, 4));
        TextView graphSub = text("Visual anchors are labels, not GPS points or metric coordinates.", 12, MUTED, false);
        body.addView(graphSub);
    }

    private void refreshMap() {
        if (body == null || map == null) return;
        // Keep the static controls intact; rebuild only the dynamic section.
        if (enabledStatus != null) {
            enabledStatus.setText(map.isEnabled() ? "Visual memory is ON" : "Visual memory is OFF");
            toggleButton.setText(map.isEnabled() ? "Pause visual memory" : "Enable visual memory");
            toggleButton.setBackground(shape(map.isEnabled() ? Color.rgb(220, 244, 235) : BRAND, 14,
                    map.isEnabled() ? Color.rgb(170, 221, 204) : Color.TRANSPARENT));
            toggleButton.setTextColor(map.isEnabled() ? Color.rgb(14, 87, 68) : Color.WHITE);
        }

        int dynamicStart = body.getChildCount();
        // Dynamic content is tagged so a refresh doesn't duplicate rows.
        for (int i = body.getChildCount() - 1; i >= 0; i--) {
            View child = body.getChildAt(i);
            Object tag = child.getTag();
            if ("visual-map-dynamic".equals(tag)) body.removeViewAt(i);
        }

        VisualAnchorMap.Snapshot snapshot = map.snapshot();
        LinearLayout summary = card();
        summary.setTag("visual-map-dynamic");
        body.addView(summary);
        summary.addView(text(snapshot.anchors.size() + " visual anchors", 18, INK, true));
        summary.addView(text(snapshot.edges.size() + " connections · " + snapshot.observations
                + " sampled observations", 12, MUTED, false), margins(-1, -2, 0, 4, 0, 0));
        String current = snapshot.currentAnchorId >= 0
                ? "Current place signature: A" + snapshot.currentAnchorId
                : "Current place signature: not established yet";
        summary.addView(text(current, 12, BRAND, true), margins(-1, -2, 0, 8, 0, 0));
        String seen = snapshot.lastObservationAt > 0
                ? "Last sample: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(snapshot.lastObservationAt))
                : "No camera samples saved yet";
        summary.addView(text(seen, 11, MUTED, false));

        if (snapshot.anchors.isEmpty()) {
            LinearLayout empty = card();
            empty.setTag("visual-map-dynamic");
            body.addView(empty, margins(-1, -2, 0, 12, 0, 0));
            empty.addView(text("Your local map is empty", 15, INK, true));
            empty.addView(text(map.isEnabled()
                    ? "Open the live camera and slowly look around to begin collecting visual place signatures."
                    : "Enable visual memory, then open the live camera to start collecting local visual place signatures.",
                    12, MUTED, false), margins(-1, -2, 0, 5, 0, 0));
        } else {
            TextView anchorHeading = text("ANCHORS", 10, BRAND, true);
            anchorHeading.setLetterSpacing(.12f);
            anchorHeading.setTag("visual-map-dynamic");
            body.addView(anchorHeading, margins(-1, -2, 0, 18, 0, 8));
            for (VisualAnchorMap.AnchorView anchor : snapshot.anchors) {
                LinearLayout row = card();
                row.setTag("visual-map-dynamic");
                body.addView(row, margins(-1, -2, 0, 0, 0, 8));
                row.addView(text("A" + anchor.id, 15, INK, true));
                row.addView(text("Seen " + anchor.visits + " times", 12, MUTED, false),
                        margins(-1, -2, 0, 3, 0, 0));
                row.addView(text("Last seen " + DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(new Date(anchor.lastSeen)), 11, MUTED, false),
                        margins(-1, -2, 0, 2, 0, 0));
            }
            if (!snapshot.edges.isEmpty()) {
                TextView edgeHeading = text("CONNECTIONS", 10, BRAND, true);
                edgeHeading.setLetterSpacing(.12f);
                edgeHeading.setTag("visual-map-dynamic");
                body.addView(edgeHeading, margins(-1, -2, 0, 18, 0, 8));
                for (VisualAnchorMap.EdgeView edge : snapshot.edges) {
                    LinearLayout row = card();
                    row.setTag("visual-map-dynamic");
                    body.addView(row, margins(-1, -2, 0, 0, 0, 8));
                    row.addView(text("A" + edge.from + "  →  A" + edge.to, 14, INK, true));
                    row.addView(text("Observed transition " + edge.traversals + " times", 12, MUTED, false),
                            margins(-1, -2, 0, 3, 0, 0));
                }
            }
        }

        LinearLayout warning = column();
        warning.setTag("visual-map-dynamic");
        warning.setPadding(dp(14), dp(13), dp(14), dp(13));
        warning.setBackground(shape(Color.rgb(255, 246, 224), 15, Color.rgb(239, 220, 170)));
        body.addView(warning, margins(-1, -2, 0, 14, 0, 0));
        warning.addView(text("LIMITATION", 10, Color.rgb(111, 76, 14), true));
        warning.addView(text("This is a coarse visual place graph—not SLAM, a 3D world map, validated localization, or a mobility safety feature. It does not store distances or bearings.", 12, Color.rgb(91, 70, 35), false),
                margins(-1, -2, 0, 4, 0, 0));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(SURFACE, 18, BORDER));
        return card;
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private Button button(String label, int fill, int foreground, int stroke) {
        Button v = new Button(this);
        v.setText(label);
        v.setTextSize(13);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setTextColor(foreground);
        v.setAllCaps(false);
        v.setMinHeight(dp(44));
        v.setBackground(shape(fill, 14, stroke));
        return v;
    }

    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams margins(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Keeps compile-time API checks local and avoids newer theme dependencies. */
    private static final class BuildVersion {
        static boolean hasLightNavBar() {
            return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O;
        }
    }
}
