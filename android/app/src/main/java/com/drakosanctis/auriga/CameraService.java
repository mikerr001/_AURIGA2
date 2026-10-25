package com.drakosanctis.auriga;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.core.content.ContextCompat;

import java.util.Collections;

/**
 * Camera2 preview owner for the legacy native navigation HUD.
 *
 * The SurfaceTexture buffer size is set before the camera session is created.
 * The worker thread survives pause/resume and is only terminated by release().
 * Failures are surfaced to the UI instead of being silently swallowed.
 */
public final class CameraService {
    private static final String TAG = "AurigaCamera";
    private static final int TARGET_WIDTH = 640;
    private static final int TARGET_HEIGHT = 480;

    public interface Listener {
        void onCameraStatus(String message, Throwable error);
    }

    private final Context context;
    private final Listener listener;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private SurfaceTexture surfaceTexture;
    private Surface previewSurface;
    private boolean isFlashOn;
    private boolean opening;
    private int generation;

    public CameraService(Context context) {
        this(context, null);
    }

    public CameraService(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        ensureBackgroundThread();
    }

    private synchronized void ensureBackgroundThread() {
        if (backgroundThread != null && backgroundThread.isAlive()) return;
        backgroundThread = new HandlerThread("AurigaCameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public synchronized void start(SurfaceTexture texture) {
        if (texture == null) {
            report("Camera surface is unavailable", null);
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            report("Camera permission is not granted", null);
            return;
        }
        if (cameraDevice != null && surfaceTexture == texture) return;

        closeCameraResources();
        ensureBackgroundThread();
        surfaceTexture = texture;
        opening = true;
        final int requestGeneration = ++generation;
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            opening = false;
            report("Android camera service is unavailable", null);
            return;
        }

        try {
            String cameraId = getBackCameraId(manager);
            if (cameraId == null) {
                opening = false;
                report("No rear-facing camera was found", null);
                return;
            }

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size bufferSize = choosePreviewSize(map);
            if (bufferSize == null) {
                opening = false;
                report("The rear camera exposes no SurfaceTexture preview sizes", null);
                return;
            }

            // Critical Camera2 requirement: a SurfaceTexture must have a non-zero
            // default buffer size before it is used as a camera output surface.
            texture.setDefaultBufferSize(bufferSize.getWidth(), bufferSize.getHeight());
            previewSurface = new Surface(texture);
            report("Opening rear camera (" + bufferSize.getWidth() + "×"
                    + bufferSize.getHeight() + ")", null);

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    synchronized (CameraService.this) {
                        if (requestGeneration != generation || surfaceTexture != texture) {
                            camera.close();
                            return;
                        }
                        opening = false;
                        cameraDevice = camera;
                        report("Camera connected; starting preview", null);
                        createPreviewSession(requestGeneration);
                    }
                }

                @Override public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    synchronized (CameraService.this) {
                        if (cameraDevice == camera) cameraDevice = null;
                        opening = false;
                    }
                    report("Camera disconnected by Android", null);
                }

                @Override public void onError(CameraDevice camera, int error) {
                    camera.close();
                    synchronized (CameraService.this) {
                        if (cameraDevice == camera) cameraDevice = null;
                        opening = false;
                    }
                    report("Android camera error " + error, null);
                }
            }, backgroundHandler);
        } catch (CameraAccessException | SecurityException | IllegalArgumentException e) {
            opening = false;
            Log.e(TAG, "Could not open camera", e);
            report("Could not open camera: " + e.getMessage(), e);
        }
    }

    private Size choosePreviewSize(StreamConfigurationMap map) {
        if (map == null) return null;
        Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
        if (sizes == null || sizes.length == 0) return null;
        Size best = sizes[0];
        long bestScore = Long.MAX_VALUE;
        for (Size size : sizes) {
            long score = Math.abs((long) size.getWidth() - TARGET_WIDTH)
                    + Math.abs((long) size.getHeight() - TARGET_HEIGHT);
            if (score < bestScore) {
                best = size;
                bestScore = score;
            }
        }
        return best;
    }

    private synchronized void createPreviewSession(int requestGeneration) {
        if (cameraDevice == null || previewSurface == null) return;
        try {
            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession session) {
                            synchronized (CameraService.this) {
                                if (requestGeneration != generation || cameraDevice == null) {
                                    session.close();
                                    return;
                                }
                                captureSession = session;
                                updatePreview();
                            }
                        }

                        @Override public void onConfigureFailed(CameraCaptureSession session) {
                            session.close();
                            report("Android could not configure the camera preview surface", null);
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException | IllegalArgumentException e) {
            Log.e(TAG, "Could not create preview session", e);
            report("Could not create preview session: " + e.getMessage(), e);
        }
    }

    private synchronized void updatePreview() {
        if (cameraDevice == null || captureSession == null || previewSurface == null) return;
        try {
            CaptureRequest.Builder builder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            if (isFlashOn) {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            } else {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }
            captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
            report("Camera preview active", null);
        } catch (CameraAccessException | IllegalStateException e) {
            Log.e(TAG, "Could not start repeating preview", e);
            report("Preview failed: " + e.getMessage(), e);
        }
    }

    public synchronized void setFlash(boolean on) {
        isFlashOn = on;
        updatePreview();
    }

    private String getBackCameraId(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            Integer facing = manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
        }
        return null;
    }

    /** Close camera objects but retain the worker thread for a later resume. */
    public synchronized void stop() {
        generation++;
        opening = false;
        closeCameraResources();
    }

    /** Final cleanup; call when the owning Activity is being destroyed. */
    public synchronized void release() {
        stop();
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    private void closeCameraResources() {
        if (captureSession != null) {
            try { captureSession.stopRepeating(); } catch (Throwable ignored) {}
            try { captureSession.close(); } catch (Throwable ignored) {}
            captureSession = null;
        }
        if (cameraDevice != null) {
            try { cameraDevice.close(); } catch (Throwable ignored) {}
            cameraDevice = null;
        }
        if (previewSurface != null) {
            try { previewSurface.release(); } catch (Throwable ignored) {}
            previewSurface = null;
        }
        surfaceTexture = null;
    }

    private void report(String message, Throwable error) {
        Log.i(TAG, message, error);
        if (listener != null) listener.onCameraStatus(message, error);
    }
}
