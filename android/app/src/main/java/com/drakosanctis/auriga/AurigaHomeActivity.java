package com.drakosanctis.auriga;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Native-first AurigaNavi dashboard.
 *
 * This is an application home screen, not a web landing page. It contains no
 * WebView and every action opens a native Android activity.
 */
public final class AurigaHomeActivity extends Activity {
    private static final int BG = Color.rgb(244, 247, 249);
    private static final int SURFACE = Color.WHITE;
    private static final int INK = Color.rgb(20, 39, 49);
    private static final int MUTED = Color.rgb(88, 108, 119);
    private static final int BRAND = Color.rgb(0, 112, 126);
    private static final int BRAND_PALE = Color.rgb(222, 244, 244);
    private static final int BORDER = Color.rgb(219, 229, 233);
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);
        content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(26));
        scroll.addView(content);
        setContentView(scroll);
        buildDashboard();
    }

    private void buildDashboard() {
        // Compact app bar: identity and a real device-status destination.
        LinearLayout appBar = row();
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("A", 21, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(shape(INK, 14, Color.TRANSPARENT));
        appBar.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout titleStack = column();
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(12);
        appBar.addView(titleStack, titleParams);
        titleStack.addView(text("AurigaNavi", 19, INK, true));
        TextView sub = text("SPATIAL COPILOT", 9, MUTED, true);
        sub.setLetterSpacing(.14f);
        titleStack.addView(sub);

        Button statusButton = button("Device status", SURFACE, BRAND, BORDER);
        statusButton.setTextSize(11);
        appBar.addView(statusButton, new LinearLayout.LayoutParams(-2, dp(42)));
        statusButton.setOnClickListener(v -> open(ModelStatusActivity.class));
        content.addView(appBar);

        // Functional heading replaces the promotional website-style hero.
        TextView greeting = text("What do you want to do?", 28, INK, true);
        content.addView(greeting, margins(-1, -2, 0, 28, 0, 4));
        TextView intro = text("Start with the task. Auriga opens a focused native tool, with controls kept predictable.", 14, MUTED, false);
        content.addView(intro);
        TextView firstUse = text("First time? Allow camera access when asked. The microphone is only requested when you tap MIC; you can stop listening at any time.", 12, BRAND, false);
        firstUse.setLineSpacing(dp(2), 1f);
        content.addView(firstUse, margins(-1, -2, 0, 12, 0, 0));

        // One primary action, visually dominant and easy to hit.
        LinearLayout scanCard = column();
        scanCard.setPadding(dp(20), dp(20), dp(20), dp(20));
        scanCard.setBackground(shape(INK, 24, Color.TRANSPARENT));
        content.addView(scanCard, margins(-1, -2, 0, 22, 0, 0));

        LinearLayout scanTop = row();
        scanTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView live = text("LIVE CAMERA", 10, Color.rgb(156, 243, 213), true);
        live.setLetterSpacing(.15f);
        scanTop.addView(live, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView nativeTag = text("NATIVE", 9, Color.rgb(204, 224, 229), true);
        nativeTag.setPadding(dp(9), dp(5), dp(9), dp(5));
        nativeTag.setBackground(shape(Color.rgb(37, 64, 75), 50, Color.TRANSPARENT));
        scanTop.addView(nativeTag);
        scanCard.addView(scanTop);

        TextView scanTitle = text("Find a specific object", 24, Color.WHITE, true);
        scanCard.addView(scanTitle, margins(-1, -2, 0, 14, 0, 4));
        TextView scanCopy = text("Choose a target such as a person, phone, chair or vehicle. Then open the live locator. Auriga will say when its detector is unavailable.", 13, Color.rgb(202, 220, 225), false);
        scanCopy.setLineSpacing(dp(3), 1f);
        scanCard.addView(scanCopy);

        Button start = button("Choose an object  →", Color.rgb(156, 243, 213), INK, Color.TRANSPARENT);
        scanCard.addView(start, margins(-1, dp(52), 0, 18, 0, 0));
        start.setContentDescription("Choose which object Auriga should locate.");
        start.setOnClickListener(v -> open(TargetsActivity.class));

        Button explore = button("Open live scene instead", Color.rgb(37, 64, 75), Color.WHITE, Color.rgb(72, 104, 114));
        scanCard.addView(explore, margins(-1, dp(48), 0, 10, 0, 0));
        explore.setContentDescription("Open the live camera without changing the current target selection.");
        explore.setOnClickListener(v -> open(LocatorActivity.class));

        TextView toolsHeading = text("Other tasks", 20, INK, true);
        content.addView(toolsHeading, margins(-1, -2, 0, 26, 0, 5));
        TextView toolsSub = text("Reading, colour, saved places and device setup.", 13, MUTED, false);
        content.addView(toolsSub);

        LinearLayout firstRow = row();
        content.addView(firstRow, margins(-1, -2, 0, 15, 0, 0));
        firstRow.addView(toolCard("Read text", "Pages, signs and labels", "Aa",
                () -> open(ReaderActivity.class)), weighted());
        firstRow.addView(gap(12), new LinearLayout.LayoutParams(dp(12), 1));
        firstRow.addView(toolCard("Identify colour", "Name a colour in view", "◉",
                () -> open(ColorSenseActivity.class)), weighted());

        LinearLayout secondRow = row();
        content.addView(secondRow, margins(-1, -2, 0, 12, 0, 0));
        secondRow.addView(toolCard("Object targets", "Choose what to track", "⌖",
                () -> open(TargetsActivity.class)), weighted());
        secondRow.addView(gap(12), new LinearLayout.LayoutParams(dp(12), 1));
        secondRow.addView(toolCard("Camera devices", "Connect supported hardware", "⌁",
                () -> open(CameraConnectActivity.class)), weighted());

        LinearLayout thirdRow = row();
        content.addView(thirdRow, margins(-1, -2, 0, 12, 0, 0));
        thirdRow.addView(toolCard("Model status", "Check on-device AI", "▦",
                () -> open(ModelStatusActivity.class)), weighted());
        thirdRow.addView(gap(12), new LinearLayout.LayoutParams(dp(12), 1));
        thirdRow.addView(toolCard("Help & controls", "Learn how to use Auriga", "i",
                () -> open(HelpActivity.class)), weighted());

        LinearLayout fourthRow = row();
        content.addView(fourthRow, margins(-1, -2, 0, 12, 0, 0));
        fourthRow.addView(toolCard("Visual place map", "Opt-in local visual memory", "⌂",
                () -> open(SpatialMapActivity.class)), weighted());
        fourthRow.addView(gap(12), new LinearLayout.LayoutParams(dp(12), 1));
        fourthRow.addView(toolCard("Calibration", "Review calibration workflow", "↗",
                () -> open(CalibrationWalkActivity.class)), weighted());

        // Honest, persistent safety state—not a marketing badge.
        LinearLayout safety = column();
        safety.setPadding(dp(15), dp(14), dp(15), dp(14));
        safety.setBackground(shape(Color.rgb(255, 246, 224), 16, Color.rgb(239, 220, 170)));
        content.addView(safety, margins(-1, -2, 0, 22, 0, 0));
        safety.addView(text("EXPERIMENTAL BUILD", 10, Color.rgb(111, 76, 14), true));
        TextView warning = text("Not a certified mobility aid. Do not rely on Auriga for collision avoidance or safety-critical distance estimates.", 12, Color.rgb(91, 70, 35), false);
        warning.setLineSpacing(dp(2), 1f);
        safety.addView(warning, margins(-1, -2, 0, 5, 0, 0));

        LinearLayout footer = row();
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView footerText = text("Privacy-first direction · User in control", 11, MUTED, false);
        footer.addView(footerText, new LinearLayout.LayoutParams(0, -2, 1f));
        Button help = button("Support", BG, BRAND, BORDER);
        help.setTextSize(11);
        footer.addView(help, new LinearLayout.LayoutParams(-2, dp(40)));
        help.setOnClickListener(v -> open(SupportActivity.class));
        content.addView(footer, margins(-1, -2, 0, 20, 0, 0));
    }

    private LinearLayout toolCard(String title, String description, String glyph, Runnable action) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(15), dp(14), dp(16));
        card.setMinimumHeight(dp(142));
        card.setBackground(shape(SURFACE, 18, BORDER));

        TextView icon = text(glyph, 21, BRAND, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(BRAND_PALE, 13, Color.TRANSPARENT));
        card.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        card.addView(text(title, 14, INK, true), margins(-1, -2, 0, 12, 0, 4));
        TextView copy = text(description, 11, MUTED, false);
        copy.setLineSpacing(dp(2), 1f);
        card.addView(copy);
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(title + ". " + description);
        card.setOnClickListener(v -> action.run());
        return card;
    }

    private void open(Class<?> destination) {
        try {
            startActivity(new Intent(this, destination));
        } catch (Throwable t) {
            android.widget.Toast.makeText(this, "That tool could not be opened.", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
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
        v.setTextSize(14);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setTextColor(foreground);
        v.setAllCaps(false);
        v.setMinHeight(dp(44));
        v.setPadding(dp(12), 0, dp(12), 0);
        v.setBackground(shape(fill, 14, stroke));
        v.setContentDescription(label);
        return v;
    }

    private GradientDrawable shape(int fill, int radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private View gap(int widthDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), 1));
        return v;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
