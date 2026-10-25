package com.drakosanctis.auriga;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Task-first object selector. Search and grouped presets operate on the exact
 * COCO class names used by the native YOLO model, not the unrelated ML Kit labels.
 */
public final class TargetsActivity extends Activity {
    private static final int BG = Color.rgb(244, 247, 249);
    private static final int SURFACE = Color.WHITE;
    private static final int INK = Color.rgb(20, 39, 49);
    private static final int MUTED = Color.rgb(88, 108, 119);
    private static final int BRAND = Color.rgb(0, 112, 126);
    private static final int PALE = Color.rgb(222, 244, 244);
    private static final int BORDER = Color.rgb(219, 229, 233);

    private final Set<String> selection = new LinkedHashSet<>();
    private LinearLayout results;
    private TextView count;
    private EditText search;
    private String activePreset = "All objects";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        selection.addAll(TargetStore.read(this));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout page = column();
        page.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(page);
        setContentView(scroll);

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹  Back", SURFACE, INK, BORDER);
        back.setContentDescription("Back to the previous screen");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(-2, dp(48)));
        TextView brand = text("AURIGA  /  TARGETS", 11, BRAND, true);
        brand.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1f));
        page.addView(top);

        page.addView(text("What are you looking for?", 27, INK, true), margins(-1,-2,0,22,0,5));
        TextView intro = text("Choose a type or search for an exact object. Your selection is saved automatically.", 14, MUTED, false);
        intro.setLineSpacing(dp(3), 1f);
        page.addView(intro);

        LinearLayout summary = column();
        summary.setPadding(dp(16), dp(14), dp(16), dp(14));
        summary.setBackground(shape(INK, 18, INK));
        page.addView(summary, margins(-1,-2,0,18,0,0));
        summary.addView(text("CURRENT TARGET", 10, Color.rgb(156,243,213), true));
        count = text("", 21, Color.WHITE, true);
        summary.addView(count, margins(-1,-2,0,4,0,3));
        summary.addView(text("Target matching filters detections. It does not measure distance or guarantee a lock.", 12, Color.rgb(209,225,229), false));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setHint("Search: person, chair, phone…");
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setMinHeight(dp(54));
        search.setBackground(shape(SURFACE, 15, BORDER));
        search.setContentDescription("Search available object types");
        page.addView(search, margins(-1,dp(54),0,18,0,0));

        TextView quickTitle = text("QUICK CHOICES", 11, BRAND, true);
        quickTitle.setLetterSpacing(.1f);
        page.addView(quickTitle, margins(-1,-2,0,22,0,9));
        HorizontalScrollView presetsScroll = new HorizontalScrollView(this);
        presetsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presets = row();
        presetsScroll.addView(presets);
        page.addView(presetsScroll);
        addPreset(presets, "Any object", TargetStore.CATEGORY_ANY);
        addPreset(presets, "People", "preset_people");
        addPreset(presets, "Transport", "preset_transport");
        addPreset(presets, "Food & drink", "preset_food");
        addPreset(presets, "At home", "preset_home");
        addPreset(presets, "Personal items", "preset_personal");

        TextView listTitle = text("OBJECT TYPES", 11, BRAND, true);
        listTitle.setLetterSpacing(.1f);
        page.addView(listTitle, margins(-1,-2,0,24,0,8));
        results = column();
        page.addView(results);

        LinearLayout bottom = row();
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(bottom, margins(-1,-2,0,24,0,0));
        Button reset = button("Reset", BG, MUTED, BORDER);
        bottom.addView(reset, new LinearLayout.LayoutParams(0, dp(52), 1f));
        reset.setOnClickListener(v -> applyPreset(TargetStore.CATEGORY_ANY));
        View spacer = new View(this);
        bottom.addView(spacer, new LinearLayout.LayoutParams(dp(10), 1));
        Button start = button("Start locating  →", PALE, INK, BRAND);
        bottom.addView(start, new LinearLayout.LayoutParams(0, dp(52), 1.5f));
        start.setOnClickListener(v -> {
            TargetStore.write(this, selection);
            startActivity(new Intent(this, LocatorActivity.class));
            finish();
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { renderResults(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        refreshSummary();
        renderResults();
    }

    private void addPreset(LinearLayout parent, String label, String key) {
        Button b = button(label, SURFACE, INK, BORDER);
        b.setTextSize(13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.rightMargin = dp(8);
        parent.addView(b, lp);
        b.setOnClickListener(v -> applyPreset(key));
    }

    private void applyPreset(String preset) {
        selection.clear();
        activePreset = preset;
        switch (preset) {
            case "preset_people": selection.addAll(Arrays.asList(TargetStore.PEOPLE)); break;
            case "preset_transport": selection.addAll(Arrays.asList(TargetStore.TRANSPORT)); break;
            case "preset_food": selection.addAll(Arrays.asList(TargetStore.FOOD)); break;
            case "preset_home": selection.addAll(Arrays.asList(TargetStore.HOME)); break;
            case "preset_personal": selection.addAll(Arrays.asList(TargetStore.PERSONAL)); break;
            default: selection.add(TargetStore.CATEGORY_ANY); break;
        }
        TargetStore.write(this, selection);
        refreshSummary();
        renderResults();
    }

    private void toggle(String label) {
        if (selection.contains(TargetStore.CATEGORY_ANY)) selection.clear();
        if (selection.contains(label)) selection.remove(label);
        else selection.add(label);
        if (selection.isEmpty()) selection.add(TargetStore.CATEGORY_ANY);
        activePreset = "";
        TargetStore.write(this, selection);
        refreshSummary();
        renderResults();
    }

    private void refreshSummary() {
        if (count == null) return;
        count.setText(TargetStore.summary(selection));
        count.setContentDescription("Current selection: " + TargetStore.summary(selection));
    }

    private void renderResults() {
        if (results == null) return;
        results.removeAllViews();
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.US);
        int shown = 0;
        for (String label : TargetStore.CATEGORIES) {
            if (!query.isEmpty() && !label.contains(query)) continue;
            shown++;
            boolean selected = selection.contains(TargetStore.CATEGORY_ANY) || selection.contains(label);
            LinearLayout item = row();
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), dp(5), dp(10), dp(5));
            item.setBackground(shape(SURFACE, 14, selected ? BRAND : BORDER));
            item.setMinimumHeight(dp(58));
            item.setFocusable(true);
            item.setClickable(true);
            TextView dot = text(selected ? "✓" : "+", 17, selected ? BRAND : MUTED, true);
            dot.setGravity(Gravity.CENTER);
            item.addView(dot, new LinearLayout.LayoutParams(dp(34), dp(44)));
            TextView name = text(titleCase(label), 15, INK, selected);
            name.setContentDescription(titleCase(label));
            item.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView state = text(selected ? "Selected" : "Add", 12, selected ? BRAND : MUTED, true);
            item.addView(state);
            item.setContentDescription(titleCase(label) + (selected ? ", selected" : ", not selected") + ". Double tap to toggle.");
            item.setOnClickListener(v -> toggle(label));
            results.addView(item, margins(-1,dp(58),0,0,0,7));
        }
        if (shown == 0) {
            TextView empty = text("No object types match “" + query + "”. Try a shorter search.", 14, MUTED, false);
            results.addView(empty, margins(-1,-2,0,8,0,0));
        }
    }

    private String titleCase(String value) {
        if (value == null || value.isEmpty()) return value;
        String[] words = value.split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (out.length() > 0) out.append(' ');
            out.append(word.substring(0,1).toUpperCase(Locale.US)).append(word.substring(1));
        }
        return out.toString();
    }

    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private Button button(String s, int fill, int foreground, int stroke) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(foreground); b.setMinHeight(dp(48));
        b.setPadding(dp(12), 0, dp(12), 0); b.setBackground(shape(fill, 14, stroke)); b.setContentDescription(s); return b;
    }
    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(1), stroke); return d;
    }
    private LinearLayout.LayoutParams margins(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
