/*
 * SPDX-FileCopyrightText: 2026 The AxionOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.media.AudioManager;
import android.os.IBinder;

import androidx.preference.PreferenceManager;

/**
 * Runs while the phone rings: when the phone is picked up (the wake-up pick-up gesture
 * sensor fires), lowers the ring volume to a third. The volume is restored when the call
 * stops ringing.
 */
public class RingPickupService extends Service {

    public static final String KEY_ENABLED = "pickup_reduce_ring";
    private static final String KEY_SAVED_VOLUME = "pickup_saved_ring_volume";

    private SensorManager mSensorManager;
    private Sensor mSensor;

    private final TriggerEventListener mListener = new TriggerEventListener() {
        @Override
        public void onTrigger(TriggerEvent event) {
            AudioManager audio = getSystemService(AudioManager.class);
            int volume = audio.getStreamVolume(AudioManager.STREAM_RING);
            // Never 0: that would switch the ringer mode to vibrate.
            int reduced = Math.max(1, volume / 3);
            if (volume <= reduced) return;
            prefs(RingPickupService.this).edit().putInt(KEY_SAVED_VOLUME, volume).apply();
            audio.setStreamVolume(AudioManager.STREAM_RING, reduced, 0);
        }
    };

    public static boolean isSupported(Context context) {
        return pickupSensor(context) != null;
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    /** Puts back the ring volume saved when the phone was picked up, if any. */
    public static void restoreVolume(Context context) {
        SharedPreferences prefs = prefs(context);
        int volume = prefs.getInt(KEY_SAVED_VOLUME, -1);
        if (volume < 0) return;
        context.getSystemService(AudioManager.class)
                .setStreamVolume(AudioManager.STREAM_RING, volume, 0);
        prefs.edit().remove(KEY_SAVED_VOLUME).apply();
    }

    private static Sensor pickupSensor(Context context) {
        return context.getSystemService(SensorManager.class)
                .getDefaultSensor(Sensor.TYPE_PICK_UP_GESTURE, true);
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public void onCreate() {
        mSensorManager = getSystemService(SensorManager.class);
        mSensor = pickupSensor(this);
        if (mSensor != null) mSensorManager.requestTriggerSensor(mListener, mSensor);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mSensor != null) mSensorManager.cancelTriggerSensor(mListener, mSensor);
        restoreVolume(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
