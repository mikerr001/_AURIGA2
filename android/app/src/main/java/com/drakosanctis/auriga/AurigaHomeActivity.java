package com.drakosanctis.auriga;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Native Auriga home screen. This is deliberately independent of the web demo:
 * all navigation here launches Android activities and never embeds a WebView.
 */
public final class AurigaHomeActivity extends Activity {
    private final int ink = Color.rgb(22, 39, 51);
    private final int muted = Color.rgb(91, 109, 121);
    private final int brand = Color.rgb(8, 127, 140);
    private final int border = Color.rgb(222, 230, 235);
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(246, 248, 251));
        window.setNavigationBarColor(Color.rgb(246, 248, 251));
        int systemUiFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            systemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(systemUiFlags);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 248, 251));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(18), dp(22), dp(30));
        scroll.addView(content);
        setContentView(scroll);
        buildHome();
    }

    private void buildHome() {
        LinearLayout header = row();
        TextView mark = text("A", 22, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(shape(Color.rgb(17, 43, 57), 15, Color.TRANSPARENT));
        header.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout brandText = column();
        TextView title = text("Auriga", 21, ink, true);
        TextView subtitle = text("BY DRAKOSANCTIS", 10, muted, true);
        subtitle.setLetterSpacing(.12f);
        brandText.addView(title);
        brandText.addView(subtitle);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0, -2, 1f);
        brandParams.leftMargin = dp(12);
        header.addView(brandText, brandParams);

        TextView status = text("PREVIEW", 10, brand, true);
        status.setLetterSpacing(.08f);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(8), dp(12), dp(8));
        status.setBackground(shape(Color.rgb(224, 244, 243), 50, Color.TRANSPARENT));
        header.addView(status);
        content.addView(header);

        TextView eyebrow = text("YOUR ON-DEVICE COPILOT", 11, brand, true);
        eyebrow.setLetterSpacing(.12f);
        LinearLayout.LayoutParams eyebrowParams = params(-2, -2, 0, 0, 0, 0);
        eyebrowParams.topMargin = dp(32);
        content.addView(eyebrow, eyebrowParams);

        TextView headline = text("A little more context.\nA little more independence.", 32, ink, true);
        headline.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams headlineParams = params(-1, -2, 0, 10, 0, 0);
        content.addView(headline, headlineParams);

        TextView intro = text("Choose a tool for the task in front of you. The Android app stays native; the browser demo lives separately.", 15, muted, false);
        intro.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams introParams = params(-1, -2, 0, 8, 0, 0);
        content.addView(intro, introParams);

        LinearLayout hero = column();
        hero.setPadding(dp(22), dp(22), dp(22), dp(22));
        hero.setBackground(shape(Color.rgb(17, 43, 57), 24, Color.TRANSPARENT));
        LinearLayout.LayoutParams heroParams = params(-1, -2, 0, 24, 0, 0);
        content.addView(hero, heroParams);

        TextView heroLabel = text("LIVE CAMERA", 10, Color.rgb(130, 226, 224), true);
        heroLabel.setLetterSpacing(.14f);
        hero.addView(heroLabel);
        TextView heroTitle = text("Understand what’s around you.", 23, Color.WHITE, true);
        LinearLayout.LayoutParams heroTitleParams = params(-1, -2, 0, 10, 0, 0);
        hero.addView(heroTitle, heroTitleParams);
        TextView heroCopy = text("Open the native camera view for on-device object detection and spoken cues. Detection availability is shown honestly if a model cannot load.", 14, Color.rgb(207, 223, 229), false);
        heroCopy.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams heroCopyParams = params(-1, -2, 0, 8, 0, 0);
        hero.addView(heroCopy, heroCopyParams);
        Button start = button("Start live scan  →", brand, Color.WHITE);
        LinearLayout.LayoutParams startParams = params(-1, dp(52), 0, 18, 0, 0);
        hero.addView(start, startParams);
        start.setOnClickListener(v -> open(LocatorActivity.class));

        TextView toolsTitle = text("Your tools", 22, ink, true);
        LinearLayout.LayoutParams toolsTitleParams = params(-1, -2, 0, 10, 0, 0);
        content.addView(toolsTitle, toolsTitleParams);
        TextView toolsCopy = text("Focused tools, one clear purpose each.", 14, muted, false);
        content.addView(toolsCopy);

        LinearLayout rowOne = row();
        LinearLayout.LayoutParams rowParams = params(-1, -2, 0, 16, 0, 0);
        content.addView(rowOne, rowParams);
        rowOne.addView(module("Read text", "Signs, labels and printed pages", "Aa", () -> open(ReaderActivity.class)), weighted());
        rowOne.addView(spacer(dp(12)), new LinearLayout.LayoutParams(dp(12), 1));
        rowOne.addView(module("Identify colour", "Name the colour in view", "◉", () -> open(ColorSenseActivity.class)), weighted());

        LinearLayout rowTwo = row();
        content.addView(rowTwo, params(-1, -2, 0, 12, 0, 0));
        rowTwo.addView(module("Object targets", "Choose what to track", "⌖", () -> open(TargetsActivity.class)), weighted());
        rowTwo.addView(spacer(dp(12)), new LinearLayout.LayoutParams(dp(12), 1));
        rowTwo.addView(module("Calibration", "Improve measurements", "↗", () -> open(CalibrationWalkActivity.class)), weighted());

        LinearLayout rowThree = row();
        content.addView(rowThree, params(-1, -2, 0, 12, 0, 0));
        rowThree.addView(module("Connect a camera", "USB, Wi-Fi and supported devices", "⌁", () -> open(CameraConnectActivity.class)), weighted());
        rowThree.addView(spacer(dp(12)), new LinearLayout.LayoutParams(dp(12), 1));
        rowThree.addView(module("Model status", "Check local AI availability", "▦", () -> open(ModelStatusActivity.class)), weighted());

        LinearLayout voice = row();
        voice.setGravity(Gravity.CENTER_VERTICAL);
        voice.setPadding(dp(16), dp(14), dp(16), dp(14));
        voice.setBackground(shape(Color.WHITE, 18, border));
        LinearLayout.LayoutParams voiceParams = params(-1, -2, 0, 18, 0, 0);
        content.addView(voice, voiceParams);
        TextView voiceIcon = text("◖", 24, brand, true);
        voice.addView(voiceIcon);
        LinearLayout voiceWords = column();
        LinearLayout.LayoutParams voiceWordsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        voiceWordsParams.leftMargin = dp(12);
        voice.addView(voiceWords, voiceWordsParams);
        voiceWords.addView(text("Voice copilot", 15, ink, true));
        voiceWords.addView(text("Open the live screen and use its microphone control.", 12, muted, false));
        TextView voiceArrow = text("→", 21, brand, true);
        voice.addView(voiceArrow);
        voice.setFocusable(true);
        voice.setClickable(true);
        voice.setContentDescription("Open the live camera screen to use voice copilot");
        voice.setOnClickListener(v -> open(LocatorActivity.class));

        TextView safety = text("EXPERIMENTAL BUILD  ·  NOT A CERTIFIED MOBILITY AID\nDo not rely on Auriga for collision avoidance or safety-critical distance estimates. A successful install does not mean the sensing system has been validated.", 12, Color.rgb(105, 75, 19), false);
        safety.setPadding(dp(15), dp(14), dp(15), dp(14));
        safety.setBackground(shape(Color.rgb(255, 244, 216), 15, Color.rgb(239, 216, 157)));
        safety.setLineSpacing(dp(3), 1f);
        content.addView(safety, params(-1, -2, 0, 22, 0, 0));

        TextView footer = text("Privacy-first direction · Clear status · User in control", 11, muted, false);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer, params(-1, -2, 0, 20, 0, 0));
    }

    private LinearLayout module(String name, String description, String glyph, Runnable action) {
        LinearLayout card = column();
        card.setPadding(dp(15), dp(16), dp(15), dp(16));
        card.setBackground(shape(Color.WHITE, 18, border));
        card.setMinimumHeight(dp(145));
        TextView icon = text(glyph, 22, brand, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(Color.rgb(224, 244, 243), 13, Color.TRANSPARENT));
        card.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = text(name, 14, ink, true);
        LinearLayout.LayoutParams titleParams = params(-1, -2, 0, 12, 0, 0);
        card.addView(title, titleParams);
        TextView copy = text(description, 12, muted, false);
        copy.setLineSpacing(dp(2), 1f);
        card.addView(copy);
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(name + ". " + description);
        card.setOnClickListener(v -> action.run());
        return card;
    }

    private void open(Class<?> destination) {
        try {
            startActivity(new Intent(this, destination));
        } catch (Throwable t) {
            android.widget.Toast.makeText(this, "This tool could not be opened.", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button button(String label, int background, int foreground) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(foreground);
        view.setAllCaps(false);
        view.setMinHeight(dp(48));
        view.setBackground(shape(background, 15, Color.TRANSPARENT));
        view.setContentDescription(label);
        return view;
    }

    private GradientDrawable shape(int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private View spacer(int widthDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), 1));
        return view;
    }

    private LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
