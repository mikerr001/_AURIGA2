package com.drakosanctis.auriga;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import android.view.MotionEvent;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.common.util.concurrent.ListenableFuture;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native CameraX object locator for Auriga Navi.
 *
 * Runs the bundled YOLOv8n TensorFlow Lite model on device, paints detections
 * through LocatorOverlayView, and announces the object class plus relative
 * screen direction. It does not present bounding-box-based distance or height
 * estimates as measurements.
 *
 * If the model is absent or cannot load, the activity shows a native
 * detector-unavailable state. It never switches into the browser demo.
 */
public class LocatorActivity extends ComponentActivity {

    private static final String TAG = "LocatorActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 1702;
    private static final int MIC_PERMISSION_REQUEST    = 1703;
    private static final int VOICE_SETUP_REQUEST       = 1704;
    private boolean pendingVoiceStart = false;
    private boolean micPermissionPending = false;

    /** Same SharedPreferences keys the WebView locator used. */
    private static final String PREF_LOCATOR_VOICE  = "locator_web_voice_enabled";
    private static final String PREF_LOCATOR_HAPTIC = "locator_web_haptic_enabled";
    private static final String PREF_SMART_LIGHT    = "locator_smart_light_enabled";

    /**
     * Average pixel luminance (0–255) below which the analyser considers
     * the scene "dark" and automatically fires a torch flash even when
     * smart-light mode is in AUTO state. Tune lower to flash less often.
     */
    private static final int DARK_THRESHOLD = 55;

    /** How long (ms) to leave the torch on so the sensor stabilises
     *  before we grab the inference frame. */
    private static final long TORCH_SETTLE_MS = 130;

    /** One inference per ~333 ms (≈3 fps) -- plenty for a guidance
     *  HUD and keeps battery + thermal load reasonable. */
    private static final long ANALYSIS_INTERVAL_MS = 333L;

    /** Don't speak the same label more often than this. */
    private static final long SPEECH_COOLDOWN_MS = 2200L;

    // ── Distance / bearing constants ────────────────────────────────────

    private DrawerLayout drawerLayout;
    private PreviewView previewView;
    private LocatorOverlayView overlayView;
    private LinearLayout modelMissingPanel;

    private TextView voiceSub;
    private TextView hapticSub;
    private TextView smartLightSub;

    private boolean          smartLightEnabled = false;
    private Camera           cameraRef         = null;
    private volatile boolean analysisBound = false;
    /**
     * Two-phase torch flag. Set to {@code true} in Phase 1 (dark scene
     * detected, torch fired). Cleared in Phase 2 (next fresh frame
     * captured under torch light, inference runs, torch killed).
     */
    private volatile boolean torchPrimed       = false;

    private boolean voiceEnabled = true;
    private boolean hapticEnabled = true;

    private YoloDetector detector;
    private ExecutorService analysisExecutor;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    private HapticManager haptic;

    // Short confirmation beep played when the camera locks on target.
    private ToneGenerator lockOnBeep;

    private long lastSpokenAt = 0L;
    private String lastSpokenLabel = "";
    private long lastAnalysedAt = 0L;

    /** Most-recent YOLO detection list — updated every frame on the main thread.
     *  Read by onDescribePage() to give a real spoken scene summary. */
    private volatile List<Detection> recentDetections = Collections.emptyList();

    /** Parking-sensor style distance earcons — non-verbal proximity feedback. */

    /** Light sensor — warns user when scene is too dark for accurate detection. */
    private SensorManager sensorManager;
    private Sensor        lightSensor;
    private long          lastLowLightAt = 0L;

    private final SensorEventListener lightListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            float lux = e.values[0];
            long  now = SystemClock.uptimeMillis();
            if (lux < 8f && now - lastLowLightAt > 60_000L && voiceEngine != null) {
                lastLowLightAt = now;
                voiceEngine.speakQuiet(
                        "Low light. Enable Smart Light for better accuracy.");
            }
        }
        @Override
        public void onAccuracyChanged(Sensor s, int accuracy) {}
    };

    private Set<String> activeTargets = Collections.emptySet();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Voice navigation ───────────────────────────────────────────
    private AurigaVoiceEngine        voiceEngine;
    private SerpentineGestureDetector serpentine;
    private TextView                 voiceTranscript;
    private Button                   voiceMicFab;

    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (AurigaVoiceEngine.ACTION_WAKE_WORD.equals(intent.getAction())) {
                requestVoiceListening();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(
                MainActivity.PREFS_NAME, MODE_PRIVATE);
        voiceEnabled      = prefs.getBoolean(PREF_LOCATOR_VOICE,  true);
        hapticEnabled     = prefs.getBoolean(PREF_LOCATOR_HAPTIC, true);
        smartLightEnabled = prefs.getBoolean(PREF_SMART_LIGHT,    false);

        setContentView(R.layout.activity_locator);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        drawerLayout = findViewById(R.id.drawer_layout);
        previewView = findViewById(R.id.locator_preview);
        overlayView = findViewById(R.id.locator_overlay);
        modelMissingPanel = findViewById(R.id.locator_model_missing);

        wireDrawer();
        wireMenuToggle();
        wireTargetPicker();
        initVoiceNavigation();

        // Try to load the YOLO model. tryCreate() returns null when
        // no .tflite asset is bundled; we surface that via the
        // amber "model not bundled" panel rather than crashing.
        try {
            detector = YoloDetector.tryCreate(this);
        } catch (Throwable t) {
            Log.e(TAG, "YOLO detector init blew up", t);
            detector = null;
            Toast.makeText(this,
                    "YOLO load error: " + t.getMessage(),
                    Toast.LENGTH_LONG).show();
        }

        // Camera preview is independent of the detector. A missing or broken
        // model must disable inference, not prevent the camera from opening.
        modelMissingPanel.setVisibility(View.GONE);
        overlayView.setModelReady(false);
        overlayView.setStatus(detector == null
                ? "CAMERA STARTING · DETECTOR UNAVAILABLE — RANGE OUTPUT DISABLED"
                : "INITIALISING CAMERA…");

        haptic = new HapticManager(this);
        analysisExecutor = Executors.newSingleThreadExecutor();
        initTts();
        activeTargets = TargetStore.read(this);

        try {
            lockOnBeep = new ToneGenerator(AudioManager.STREAM_MUSIC, 65);
        } catch (Throwable ignored) { }

        ensureCameraPermissionAndStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Pick up changes the user made in TargetsActivity.
        try {
            activeTargets = TargetStore.read(this);
            refreshTargetPickerButton();
        } catch (Throwable t) {
            Log.w(TAG, "TargetStore reload failed", t);
        }
        refreshFeedbackGate(findViewById(R.id.nav_feedback),
                findViewById(R.id.nav_feedback_hint));
        refreshAiStatus();
        if (voiceEngine != null) voiceEngine.onResume();

        // Tell FrameRelay we are now the active frame source. AurigaCoreService
        // listens on the relay; it no longer binds CameraX directly, which
        // eliminates the SecurityException crash on Android 12+ (camera HAL
        // "different process than original client").
        FrameRelay.get().markSourceActive();

        // Light-level sensor — gentle "low light" advisory spoken at most once/min.
        if (sensorManager == null) {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager != null)
                lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(lightListener, lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (voiceEngine != null) voiceEngine.onPause();
        FrameRelay.get().markSourceInactive(); // stop pushing frames to service engines
        if (sensorManager != null) {
            try { sensorManager.unregisterListener(lightListener); } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        if (analysisExecutor != null) analysisExecutor.shutdown();
        if (detector != null) {
            try { detector.close(); } catch (Throwable ignored) {}
        }
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Throwable ignored) {}
        }
        if (haptic != null) {
            try { haptic.stop(); } catch (Throwable ignored) {}
        }
        if (lockOnBeep != null) {
            try { lockOnBeep.release(); } catch (Throwable ignored) {}
        }
        if (sensorManager != null) {
            try { sensorManager.unregisterListener(lightListener); } catch (Throwable ignored) {}
        }
        if (voiceEngine != null) {
            try { voiceEngine.shutdown(); } catch (Throwable ignored) {}
        }
        try { unregisterReceiver(wakeReceiver); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_SETUP_REQUEST) {
            // Re-init engine so it picks up the new assistant name.
            if (voiceEngine != null) {
                try { voiceEngine.shutdown(); } catch (Throwable ignored) {}
            }
            voiceEngine = buildVoiceEngine();
        }
    }

    // ─── Voice navigation init ─────────────────────────────────────

    /**
     * Initialises all voice-navigation components:
     *   1. Finds the mic FAB + transcript views from the layout.
     *   2. Wires the mic FAB click and the serpentine gesture to the engine.
     *   3. Registers the wake-word broadcast receiver.
     *   4. Starts the always-on {@link AurigaVoiceService}.
     *   5. If first-run (no name set), launches {@link VoiceSetupActivity}.
     *   6. Requests RECORD_AUDIO permission if not yet granted.
     *
     * Safe to call before the camera has been set up.
     */
    private void initVoiceNavigation() {
        voiceTranscript = findViewById(R.id.voice_transcript);
        voiceMicFab     = findViewById(R.id.voice_mic_fab);

        // Explicit mic activation is more reliable and private than the legacy
        // always-on capture service, which competed with SpeechRecognizer.
        AurigaVoiceService.stopListening(this);
        voiceEngine = buildVoiceEngine();

        // Serpentine gesture — detector is created here; events are fed
        // via dispatchTouchEvent() at the activity level so it always sees
        // all MOVE/UP events regardless of which child view consumes them.
        serpentine = new SerpentineGestureDetector(this,
                this::requestVoiceListening);

        // Long-press on the locator frame as a second activation path.
        FrameLayout locatorFrame = findViewById(R.id.locator_frame);
        if (locatorFrame != null) {
            locatorFrame.setOnLongClickListener(v -> {
                requestVoiceListening();
                return true;
            });
        }

        // Mic FAB tap.
        if (voiceMicFab != null) {
            voiceMicFab.setOnClickListener(v -> {
                if (voiceEngine != null && voiceEngine.isListening()) {
                    voiceEngine.stopListening();
                    Toast.makeText(this, "Voice input stopped.", Toast.LENGTH_SHORT).show();
                } else {
                    requestVoiceListening();
                }
            });
            // Long-press (2.5 s) activates Emergency SOS — critical
            // accessibility gap: no competitor provides this.
            voiceMicFab.setOnLongClickListener(v -> {
                if (voiceEngine != null) voiceEngine.activateSos();
                return true;
            });
        }

        // Wake-word broadcast from AurigaVoiceService.
        IntentFilter filter = new IntentFilter(AurigaVoiceEngine.ACTION_WAKE_WORD);
        androidx.core.content.ContextCompat.registerReceiver(
                this, wakeReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);

        // First-run: launch name-setup if not yet done.
        if (!AurigaVoiceEngine.isSetupDone(this)) {
            //noinspection deprecation
            startActivityForResult(
                    new Intent(this, VoiceSetupActivity.class),
                    VOICE_SETUP_REQUEST);
        }

        // Microphone access is requested only after the user explicitly activates
        // voice. This avoids an unrelated permission prompt on first launch.
    }

    /** Request microphone permission only after a user action, then start one listen cycle. */
    private void requestVoiceListening() {
        if (voiceEngine == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            voiceEngine.startListening();
            return;
        }
        pendingVoiceStart = true;
        if (micPermissionPending) return;
        micPermissionPending = true;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_REQUEST);
    }

    /** Build (or re-build) the AurigaVoiceEngine with a live Listener. */
    private AurigaVoiceEngine buildVoiceEngine() {
        return new AurigaVoiceEngine(this, new AurigaVoiceEngine.Listener() {
            @Override
            public void onListeningStarted() {
                if (voiceMicFab != null) {
                    voiceMicFab.setText("STOP");
                    voiceMicFab.setContentDescription("Stop voice input");
                }
                if (voiceTranscript != null) {
                    voiceTranscript.setText("Listening now. Say a command.");
                    voiceTranscript.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onListeningStopped() {
                if (voiceMicFab != null) {
                    voiceMicFab.setText("MIC");
                    voiceMicFab.setContentDescription("Start voice command. Long-press for emergency SOS.");
                }
                mainHandler.postDelayed(() -> {
                    if (voiceTranscript != null)
                        voiceTranscript.setVisibility(View.GONE);
                }, 1800);
            }
            @Override
            public void onTranscript(String text) {
                if (voiceTranscript != null) {
                    voiceTranscript.setText(text);
                    voiceTranscript.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onOpenDrawer()  {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            }
            @Override
            public void onCloseDrawer() { closeDrawer(); }
            @Override
            public void onGoBack()      { onBackPressed(); }
            @Override
            public void onDescribePage() {
                if (voiceEngine == null) return;
                List<Detection> dets = recentDetections;
                if (dets == null || dets.isEmpty()) {
                    voiceEngine.speak("Nothing detected right now. "
                            + "Move the camera around.");
                    return;
                }
                // Build a natural spoken summary: e.g. "I can see a person straight
                // ahead, a chair to your left, and a bottle to your right. 3 objects total."
                StringBuilder sb = new StringBuilder("I can see ");
                int n = Math.min(dets.size(), 5);
                for (int i = 0; i < n; i++) {
                    Detection d = dets.get(i);
                    String dir = directionWord(d.centerX());
                    if (i > 0 && i == n - 1) sb.append(", and ");
                    else if (i > 0) sb.append(", ");
                    sb.append("a ").append(d.label).append(" ").append(dir);
                }
                sb.append(". ");
                if (dets.size() > n)
                    sb.append(dets.size()).append(" objects total.");
                else
                    sb.append(dets.size() == 1 ? "1 object total." : dets.size() + " objects total.");
                voiceEngine.speak(sb.toString());
            }
        });
    }

    // ─── Camera ────────────────────────────────────────────────────

    private void ensureCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_PERMISSION_REQUEST) {
            micPermissionPending = false;
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            boolean shouldStart = pendingVoiceStart;
            pendingVoiceStart = false;
            if (granted) {
                if (shouldStart && voiceEngine != null) {
                    voiceEngine.startListening();
                } else {
                    Toast.makeText(this, "Microphone ready. Tap MIC when you want to speak.",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Voice commands need microphone permission.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode != CAMERA_PERMISSION_REQUEST) return;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            startCamera();
        } else {
            overlayView.setStatus("CAMERA PERMISSION REQUIRED");
            Toast.makeText(this,
                    "Object Locator needs the camera to detect objects.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startCamera() {
        try {
            VisualAnchorMap.getInstance(getApplicationContext()).beginSession();
        } catch (Throwable mapError) {
            Log.w(TAG, "Could not begin visual map session", mapError);
        }
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                bindCameraUseCases(provider);
                overlayView.setModelReady(detector != null && analysisBound);
                if (!analysisBound) {
                    overlayView.setStatus("CAMERA PREVIEW ACTIVE · ANALYSIS UNAVAILABLE");
                    Toast.makeText(this,
                            "Camera preview is on, but live analysis could not start. Try closing other camera apps.",
                            Toast.LENGTH_LONG).show();
                } else if (detector == null) {
                    overlayView.setStatus("DETECTOR UNAVAILABLE — RANGE OUTPUT DISABLED");
                } else {
                    overlayView.setStatus("CAMERA ACTIVE · DETECTOR READY");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Camera bind failed", t);
                overlayView.setStatus("CAMERA UNAVAILABLE: " + t.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider provider) {
        CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
        provider.unbindAll();
        analysisBound = false;
        torchPrimed = false;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Try RGBA analysis and preview together. CameraX can replace/unbind
        // previous use cases on a later bindToLifecycle call, so never bind
        // analysis by itself after binding the preview.
        try {
            ImageAnalysis rgbaAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build();
            rgbaAnalysis.setAnalyzer(analysisExecutor, new YoloAnalyzer());
            provider.unbindAll();
            cameraRef = provider.bindToLifecycle(this, selector, preview, rgbaAnalysis);
            analysisBound = true;
            Log.i(TAG, "Camera preview and RGBA analysis bound.");
            return;
        } catch (Throwable rgbaError) {
            Log.w(TAG, "RGBA analysis unavailable; retrying default YUV analysis", rgbaError);
        }

        // Retry with YUV, still binding Preview and ImageAnalysis as one set.
        try {
            ImageAnalysis yuvAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            yuvAnalysis.setAnalyzer(analysisExecutor, new YoloAnalyzer());
            provider.unbindAll();
            cameraRef = provider.bindToLifecycle(this, selector, preview, yuvAnalysis);
            analysisBound = true;
            Log.i(TAG, "Camera preview and fallback YUV analysis bound.");
            return;
        } catch (Throwable analysisError) {
            Log.e(TAG, "Image analysis could not bind; falling back to preview only", analysisError);
        }

        // Last-resort preview-only mode. The UI must clearly disclose that
        // detection is unavailable rather than imply that live analysis works.
        try {
            provider.unbindAll();
            cameraRef = provider.bindToLifecycle(this, selector, preview);
            analysisBound = false;
            Log.w(TAG, "Camera preview bound without image analysis.");
        } catch (Throwable previewError) {
            analysisBound = false;
            cameraRef = null;
            Log.e(TAG, "Camera preview could not bind", previewError);
            throw previewError;
        }
    }

    /**
     * Throttled YOLOv8 analyser. Runs on the
     * {@link #analysisExecutor} (single-threaded by design --
     * {@link YoloDetector} is not re-entrant).
     */
    private final class YoloAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy image) {
            try {
                long now = SystemClock.uptimeMillis();
                if (now - lastAnalysedAt < ANALYSIS_INTERVAL_MS) {
                    return;
                }
                lastAnalysedAt = now;

                // ── Smart Lighting (two-phase) ────────────────────────
                // Phase 1 (torchPrimed == false):
                //   Probe the current frame brightness. If dark, fire the
                //   torch and return — do NOT run inference on this stale
                //   dark frame. The torch warms up between cycles.
                // Phase 2 (torchPrimed == true):
                //   This frame was captured while the torch was already on.
                //   Run inference on the well-lit frame, then kill the torch.
                // Net result: the inference always sees a lit frame, never
                // the dark frame that triggered the flash.
                if (smartLightEnabled && cameraRef != null) {
                    if (!torchPrimed) {
                        Bitmap probe = imageProxyToUprightBitmap(image);
                        boolean isDark = probe == null
                                || computeAvgBrightness(probe) < DARK_THRESHOLD;
                        if (probe != null) probe.recycle();
                        if (isDark) {
                            try {
                                cameraRef.getCameraControl().enableTorch(true);
                            } catch (Throwable ignored) {}
                            torchPrimed = true;
                            return; // wait for next fresh frame under torch light
                        }
                        // Scene is bright — fall through to normal inference.
                    }
                    // torchPrimed == true: torch has been on since last cycle;
                    // inference below will use this lit frame, then torch off.
                }

                // ── Inference ────────────────────────────────────────
                Bitmap bmp = imageProxyToUprightBitmap(image);
                // If torch was primed, this lit frame is now in memory — kill torch.
                if (torchPrimed) {
                    tryDisableTorch();
                    torchPrimed = false;
                }
                if (bmp == null) return;

                // Opt-in local visual place memory; the map stores coarse image
                // signatures only and never derives distances or bearings.
                try {
                    VisualAnchorMap.getInstance(getApplicationContext()).observe(bmp);
                } catch (Throwable mapError) {
                    Log.w(TAG, "Visual place-memory update failed", mapError);
                }

                // Keep the live preview and relay available even if the detector
                // asset fails to load. Never substitute fabricated detections.
                if (detector == null) {
                    FrameRelay.get().publishBitmap(bmp, 0);
                    bmp.recycle();
                    mainHandler.post(() -> {
                        overlayView.setModelReady(false);
                        overlayView.setStatus("DETECTOR UNAVAILABLE — RANGE OUTPUT DISABLED");
                    });
                    return;
                }

                List<Detection> dets = detector.detect(bmp);

                List<Detection> filtered = filterByTargets(dets);
                Detection target = pickPrimaryTarget(filtered);

final List<Detection> uiDets = dets;
                final List<Detection> uiFiltered = filtered;
                final Detection uiTarget = target;
                mainHandler.post(() -> {
                    recentDetections = uiDets; // keep full scene for the describe command
                    overlayView.setDetections(uiFiltered, uiTarget);
                    if (uiTarget != null) {
                        overlayView.setStatus(buildStatusLine(uiTarget));
                        announceTarget(uiTarget);
                    } else if (uiDets.isEmpty()) {
                        overlayView.setStatus("NO TARGETS IN VIEW");
                    } else {
                        overlayView.setStatus(uiDets.size()
                                + " OBJECT" + (uiDets.size() == 1 ? "" : "S")
                                + " — NONE MATCH FILTER");
                    }
                });

                // Feed processed frame to AurigaCoreService perception engines via
                // FrameRelay (stair sense, traffic sense, crossing guard, face/pill/cash
                // identification, scene description). The bitmap is already upright (rotation
                // has been applied by imageProxyToUprightBitmap) so we pass rotation=0.
                // FrameRelay.publishBitmap() converts ARGB→NV21 internally and dispatches to
                // all registered listeners. Done BEFORE recycle so the pixels are still valid.
                FrameRelay.get().publishBitmap(bmp, 0);

                bmp.recycle();
            } catch (Throwable t) {
                Log.e(TAG, "Analyzer failure", t);
            } finally {
                image.close();
            }
        }
    }

    // ─── Smart Lighting helpers ────────────────────────────────────

    /**
     * Samples every 8th pixel of {@code bmp} (R, G, B channels) and
     * returns the average luminance in the range [0, 255]. Cheap enough
     * to run on the analysis thread before the YOLO inference pass.
     */
    private static int computeAvgBrightness(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        long sum = 0;
        int  count = 0;
        for (int y = 0; y < h; y += 8) {
            for (int x = 0; x < w; x += 8) {
                int px = bmp.getPixel(x, y);
                int r = (px >> 16) & 0xFF;
                int g = (px >>  8) & 0xFF;
                int b =  px        & 0xFF;
                // Perceptual luminance (ITU-R BT.601 coefficients)
                sum += (r * 299 + g * 587 + b * 114) / 1000;
                count++;
            }
        }
        return count == 0 ? 255 : (int)(sum / count);
    }

    /** Turn the torch off via CameraX; swallows all exceptions so the
     *  analysis loop is never interrupted by a torch failure. */
    private void tryDisableTorch() {
        try {
            if (cameraRef != null)
                cameraRef.getCameraControl().enableTorch(false);
        } catch (Throwable ignored) {}
    }

    // ─── Camera ────────────────────────────────────────────────────

    /**
     * Convert a CameraX {@link ImageProxy} to an upright ARGB
     * {@link Bitmap}. Uses the platform helper added in CameraX
     * 1.3+, then applies the rotation reported by the proxy so
     * the model sees a sensor-orientation-corrected frame.
     */
    private static Bitmap imageProxyToUprightBitmap(ImageProxy image) {
        Bitmap raw;
        try {
            raw = image.toBitmap();
        } catch (Throwable t) {
            Log.e(TAG, "ImageProxy.toBitmap() failed", t);
            return null;
        }
        int rot = image.getImageInfo().getRotationDegrees();
        if (rot == 0) return raw;
        Matrix m = new Matrix();
        m.postRotate(rot);
        Bitmap rotated = Bitmap.createBitmap(raw, 0, 0,
                raw.getWidth(), raw.getHeight(), m, true);
        if (rotated != raw) raw.recycle();
        return rotated;
    }

    private List<Detection> filterByTargets(List<Detection> all) {
        if (all.isEmpty()) return all;
        if (activeTargets == null
                || activeTargets.isEmpty()
                || activeTargets.contains(TargetStore.CATEGORY_ANY)) {
            return all;
        }
        ArrayList<Detection> kept = new ArrayList<>();
        for (Detection d : all) {
            if (TargetStore.matches(activeTargets, d.label)) {
                kept.add(d);
            }
        }
        return kept;
    }

    /**
     * Pick the most "centred and prominent" detection. Score is
     * {@code area * (1 - distFromCentre)} so big boxes near the
     * crosshair beat small distant ones, but a small object
     * dead-centre still wins over a large object at the edge.
     */
    private Detection pickPrimaryTarget(List<Detection> dets) {
        Detection best = null;
        float bestScore = -1f;
        for (Detection d : dets) {
            float dx = d.centerX() - 0.5f;
            float dy = d.centerY() - 0.5f;
            float distFromCentre = (float) Math.sqrt(dx * dx + dy * dy);
            float score = d.area() * (1f - Math.min(0.99f, distFromCentre));
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    // ─── Native HUD status and speech ─────────────────────────────

    /**
     * Reports only object class, relative screen direction and model confidence.
     * Metric distance and height remain suppressed until a validated scale and
     * ground-plane solution are available.
     */
    private String buildStatusLine(Detection d) {
        return d.label.toUpperCase(Locale.US)
                + " · " + directionWord(d.centerX()).toUpperCase(Locale.US)
                + " · " + Math.round(d.confidence * 100) + "%";
    }

    private static String directionWord(float normX) {
        if (normX < 0.40f) return "to your left";
        if (normX > 0.60f) return "to your right";
        return "ahead";
    }

    private void announceTarget(Detection d) {
        String direction = directionWord(d.centerX());
        boolean centred = "ahead".equals(direction);
        if (hapticEnabled && haptic != null && centred) {
            try { haptic.alert(); } catch (Throwable ignored) {}
        }
        if (centred && lockOnBeep != null) {
            try { lockOnBeep.startTone(ToneGenerator.TONE_PROP_BEEP2, 120); }
            catch (Throwable ignored) {}
        }
        if (!voiceEnabled || !ttsReady || tts == null) return;
        long now = SystemClock.uptimeMillis();
        boolean sameAsLast = d.label.equalsIgnoreCase(lastSpokenLabel);
        if (sameAsLast && now - lastSpokenAt < SPEECH_COOLDOWN_MS) return;
        if (!sameAsLast && now - lastSpokenAt < 700L) return;
        String label = d.label.substring(0, 1).toUpperCase(Locale.US)
                + d.label.substring(1).toLowerCase(Locale.US);
        tts.speak(label + ", " + direction + ".", TextToSpeech.QUEUE_FLUSH, null,
                "auriga_locator_" + now);
        lastSpokenAt = now;
        lastSpokenLabel = d.label;
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            ttsReady = (status == TextToSpeech.SUCCESS);
            if (ttsReady && tts != null) {
                tts.setLanguage(Locale.getDefault());
                tts.setSpeechRate(1.05f);
            } else {
                Log.w(TAG, "TTS init failed: " + status);
            }
        });
    }

    // ─── Drawer wiring (mirrors LocatorWebActivity) ───────────────

    private void wireTargetPicker() {
        Button targetButton = findViewById(R.id.locator_target_button);
        if (targetButton != null) {
            targetButton.setOnClickListener(v -> safeStart(TargetsActivity.class, "Object targets"));
            refreshTargetPickerButton();
        }
    }

    private void refreshTargetPickerButton() {
        Button targetButton = findViewById(R.id.locator_target_button);
        if (targetButton == null) return;
        Set<String> selection;
        try { selection = TargetStore.read(this); }
        catch (Throwable t) { selection = Collections.emptySet(); }
        String summary = TargetStore.summary(selection);
        String label = TargetStore.CATEGORY_ANY.equals(summary) ? "Any object" : summary;
        targetButton.setText("FIND: " + label.toUpperCase(Locale.US));
        targetButton.setContentDescription("Change what Auriga looks for. Current target filter: " + label + ". The locator prioritises a centred visible match; distance is not measured.");
    }

    private void wireMenuToggle() {
        Button menu = findViewById(R.id.menu_toggle);
        if (menu != null) {
            menu.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }
    }

    private void wireDrawer() {
        // ── NAVIGATE ──────────────────────────────────────────────
        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) navHome.setOnClickListener(v -> closeDrawer());

        View navReader = findViewById(R.id.nav_reader);
        if (navReader != null) navReader.setOnClickListener(v -> {
            closeDrawer();
            safeStart(ReaderActivity.class, "DrakoVoice Reader");
        });

        View navTargets = findViewById(R.id.nav_targets);
        View navColorSense = findViewById(R.id.nav_color_sense);
        if (navColorSense != null) navColorSense.setOnClickListener(v -> {
            closeDrawer();
            safeStart(ColorSenseActivity.class, "Color Sense");
        });

        if (navTargets != null) navTargets.setOnClickListener(v -> {
            closeDrawer();
            safeStart(TargetsActivity.class, "Targets");
        });

        View navCameraConnect = findViewById(R.id.nav_camera_connect);
        if (navCameraConnect != null) navCameraConnect.setOnClickListener(v -> {
            closeDrawer();
            safeStart(CameraConnectActivity.class, "Camera Connect");
        });

        View navStreamLaptop = findViewById(R.id.nav_stream_laptop);
        if (navStreamLaptop != null) navStreamLaptop.setOnClickListener(v -> {
            closeDrawer();
            safeStart(CameraStreamActivity.class, "Stream to Laptop");
        });

        View navAbout = findViewById(R.id.nav_about);
        if (navAbout != null) navAbout.setOnClickListener(v -> {
            closeDrawer();
            safeStart(AboutActivity.class, "About");
        });

        // ── SETUP ─────────────────────────────────────────────────
        View navCalibrate = findViewById(R.id.nav_calibrate);
        if (navCalibrate != null) navCalibrate.setOnClickListener(v -> {
            closeDrawer();
            safeStart(CalibrationWalkActivity.class, "Calibration Walk");
        });

        View navFeedback = findViewById(R.id.nav_feedback);
        if (navFeedback != null) navFeedback.setOnClickListener(v -> {
            closeDrawer();
            safeStart(FeedbackActivity.class, "Feedback");
        });
        refreshFeedbackGate(navFeedback, findViewById(R.id.nav_feedback_hint));

        // ── AI SLEEP TIMER ───────────────────────────────────────
        View navAiTimeout = findViewById(R.id.nav_ai_timeout);
        TextView aiTimeoutSub = findViewById(R.id.nav_ai_timeout_sub);
        if (navAiTimeout != null) {
            refreshAiTimeoutLabel(aiTimeoutSub);
            navAiTimeout.setOnClickListener(v -> {
                int[] options = {60, 120, 300, 600};
                String[] labels = {"1 minute", "2 minutes (default)", "5 minutes", "10 minutes"};
                int current = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                        .getInt(MindEngine.PREF_OFFLOAD_TIMEOUT,
                                MindEngine.DEFAULT_OFFLOAD_TIMEOUT_SEC);
                int currentIdx = 1; // default: 2 min
                for (int i = 0; i < options.length; i++) {
                    if (options[i] == current) { currentIdx = i; break; }
                }
                int nextIdx = (currentIdx + 1) % options.length;
                getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putInt(MindEngine.PREF_OFFLOAD_TIMEOUT, options[nextIdx])
                        .apply();
                refreshAiTimeoutLabel(aiTimeoutSub);
                Toast.makeText(this,
                        "AI sleep timer: " + labels[nextIdx],
                        Toast.LENGTH_SHORT).show();
            });
        }

        // ── VOICE / HAPTIC TOGGLES ───────────────────────────────
        View navVoice = findViewById(R.id.nav_voice_locator);
        voiceSub = findViewById(R.id.nav_voice_locator_sub);
        if (navVoice != null) {
            navVoice.setOnClickListener(v -> {
                voiceEnabled = !voiceEnabled;
                getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                        .edit().putBoolean(PREF_LOCATOR_VOICE, voiceEnabled).apply();
                if (!voiceEnabled && tts != null) {
                    try { tts.stop(); } catch (Throwable ignored) {}
                }
                refreshMuteLabels();
                Toast.makeText(this,
                        voiceEnabled ? "Voice ON" : "Voice MUTED",
                        Toast.LENGTH_SHORT).show();
            });
        }

        View navHaptic = findViewById(R.id.nav_haptic_locator);
        hapticSub = findViewById(R.id.nav_haptic_locator_sub);
        if (navHaptic != null) {
            navHaptic.setOnClickListener(v -> {
                hapticEnabled = !hapticEnabled;
                getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                        .edit().putBoolean(PREF_LOCATOR_HAPTIC, hapticEnabled).apply();
                refreshMuteLabels();
                Toast.makeText(this,
                        hapticEnabled ? "Haptic ON" : "Haptic MUTED",
                        Toast.LENGTH_SHORT).show();
            });
        }

        View navSmartLight = findViewById(R.id.nav_smart_light);
        smartLightSub = findViewById(R.id.nav_smart_light_sub);
        if (navSmartLight != null) {
            navSmartLight.setOnClickListener(v -> {
                smartLightEnabled = !smartLightEnabled;
                getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                        .edit().putBoolean(PREF_SMART_LIGHT, smartLightEnabled).apply();
                refreshMuteLabels();
                Toast.makeText(this,
                        smartLightEnabled
                                ? "Smart Light ON — flashes before each scan"
                                : "Smart Light OFF",
                        Toast.LENGTH_SHORT).show();
                // Make sure torch is off whenever user disables the feature
                if (!smartLightEnabled) tryDisableTorch();
            });
        }
        refreshMuteLabels();

        // ── AI ASSISTANT ─────────────────────────────────────────
        View navAi = findViewById(R.id.nav_ai_assistant);
        if (navAi != null) navAi.setOnClickListener(v -> {
            closeDrawer();
            safeStart(ModelStatusActivity.class, "AI Assistant");
        });

        // ── SUPPORT / CONTRIBUTE ─────────────────────────────────
        View navHelp = findViewById(R.id.nav_help);
        if (navHelp != null) navHelp.setOnClickListener(v -> {
            closeDrawer();
            safeStart(HelpActivity.class, "Help");
        });

        View navSupport = findViewById(R.id.nav_support);
        if (navSupport != null) navSupport.setOnClickListener(v -> {
            closeDrawer();
            safeStart(SupportActivity.class, "Support");
        });

        View navContributeCalibration = findViewById(R.id.nav_contribute_calibration);
        if (navContributeCalibration != null) {
            navContributeCalibration.setOnClickListener(v -> {
                closeDrawer();
                safeStart(ContributeActivity.class, "Contribute");
            });
        }

        View navContributeSdk = findViewById(R.id.nav_contribute_sdk);
        if (navContributeSdk != null) {
            navContributeSdk.setOnClickListener(v -> {
                closeDrawer();
                safeStart(ContributeActivity.class, "Contribute");
            });
        }
    }

    private void refreshMuteLabels() {
        if (voiceSub != null) {
            voiceSub.setText(voiceEnabled ? "ON · tap to mute" : "MUTED · tap to enable");
        }
        if (hapticSub != null) {
            hapticSub.setText(hapticEnabled ? "ON · tap to mute" : "MUTED · tap to enable");
        }
        if (smartLightSub != null) {
            smartLightSub.setText(smartLightEnabled
                    ? "ON · flashes before each scan"
                    : "OFF · tap to enable");
        }
    }

    /** Update the AI Sleep Timer sub-label to show the currently configured timeout. */
    private void refreshAiTimeoutLabel(TextView sub) {
        if (sub == null) return;
        int secs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                .getInt(MindEngine.PREF_OFFLOAD_TIMEOUT, MindEngine.DEFAULT_OFFLOAD_TIMEOUT_SEC);
        String label;
        if (secs < 120)       label = "After " + secs + " sec idle · tap to change";
        else if (secs < 3600) label = "After " + (secs / 60) + " min idle · tap to change";
        else                  label = "After " + (secs / 3600) + " hr idle · tap to change";
        sub.setText(label);
    }

    /**
     * Refresh the AI Assistant drawer sub-label to reflect current model state.
     * Called from onResume so users always see fresh status when they open the drawer.
     */
    private void refreshAiStatus() {
        TextView sub = findViewById(R.id.nav_ai_assistant_sub);
        if (sub == null) return;
        ModelDownloadManager mgr = AurigaApplication.modelDownloadManager;
        if (mgr == null) {
            sub.setText(getString(R.string.drawer_ai_active_none));
            return;
        }
        ModelDownloadManager.ModelState ls = mgr.getState(ModelDownloadManager.ModelId.QWEN_LARGE);
        ModelDownloadManager.ModelState ss = mgr.getState(ModelDownloadManager.ModelId.QWEN_SMALL);
        if (ls == ModelDownloadManager.ModelState.DOWNLOADING) {
            sub.setText("Qwen 1.5B downloading  " + mgr.getProgressPercent(ModelDownloadManager.ModelId.QWEN_LARGE) + "% · tap to manage");
        } else if (ss == ModelDownloadManager.ModelState.DOWNLOADING) {
            sub.setText("Qwen 0.5B downloading  " + mgr.getProgressPercent(ModelDownloadManager.ModelId.QWEN_SMALL) + "% · tap to manage");
        } else if (ls == ModelDownloadManager.ModelState.READY) {
            sub.setText(getString(R.string.drawer_ai_active_large));
        } else if (ss == ModelDownloadManager.ModelState.READY) {
            sub.setText(getString(R.string.drawer_ai_active_small));
        } else {
            sub.setText(getString(R.string.drawer_ai_active_none));
        }
    }

    private void refreshFeedbackGate(View feedbackRow, View hint) {
        try {
            SharedPreferences prefs = getSharedPreferences(
                    MainActivity.PREFS_NAME, MODE_PRIVATE);
            boolean walkDone = prefs.getBoolean(
                    CalibrationWalkActivity.PREF_WALK_DONE, false);
            if (hint != null) hint.setVisibility(walkDone ? View.GONE : View.VISIBLE);
            if (feedbackRow != null) feedbackRow.setAlpha(walkDone ? 1f : 0.55f);
        } catch (Throwable ignored) { /* row stays tappable either way */ }
    }

    private void closeDrawer() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private void safeStart(Class<?> target, String label) {
        try {
            startActivity(new Intent(this, target));
        } catch (Throwable t) {
            Toast.makeText(this,
                    label + " unavailable: " + t.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Feed every touch event to the serpentine gesture detector BEFORE
     * any view processes it. This guarantees the detector always receives
     * ACTION_DOWN, ACTION_MOVE, and ACTION_UP regardless of whether a
     * child view (PreviewView, overlay, FAB, etc.) consumes the event.
     *
     * Previously the detector was attached via {@code setOnTouchListener}
     * on the locator FrameLayout, but a non-clickable FrameLayout drops
     * MOVE/UP events when ACTION_DOWN returns {@code false}, so the S-curve
     * was never recognised. Routing through dispatchTouchEvent fixes this.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (serpentine != null) serpentine.onTouch(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK
                && drawerLayout != null
                && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

}
