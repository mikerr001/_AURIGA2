package com.drakosanctis.auriga;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.util.Log;

/**
 * Explicit target for UsbManager's mutable permission PendingIntent.
 * CameraConnectActivity currently asks users to reconnect after granting
 * permission; this receiver records the result without starting UI from
 * the background.
 */
public final class UsbPermissionReceiver extends BroadcastReceiver {
    public static final String ACTION_USB_PERMISSION =
            "com.drakosanctis.auriga.USB_PERMISSION";
    private static final String TAG = "UsbPermissionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_USB_PERMISSION.equals(intent.getAction())) {
            return;
        }
        boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
        Log.i(TAG, granted ? "USB device permission granted" : "USB device permission denied");
    }
}
