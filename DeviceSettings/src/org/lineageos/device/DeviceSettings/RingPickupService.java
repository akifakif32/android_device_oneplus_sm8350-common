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
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.media.AudioManager;
import android.os.IBinder;

import androidx.preference.PreferenceManager;

/**
 * Runs while the phone rings: when the phone is picked up (the wake-up pick-up gesture
 * sensor fires, or the accelerometer sees it tilt by TILT_DEGREES), lowers the ring volume
 * to a third. The volume is restored when the call stops ringing.
 */
public class RingPickupService extends Service {

    public static final String KEY_ENABLED = "pickup_reduce_ring";
    private static final String KEY_SAVED_VOLUME = "pickup_saved_ring_volume";

    // Tilt from the orientation the phone rang in. The pick-up gesture sensor's own
    // threshold is fixed in sensor firmware and needs a full lift; this catches smaller
    // movements. Tilt, not shake, so the ringer's vibration on a table can't trigger it.
    private static final float TILT_DEGREES = 20f;
    private static final float GRAVITY_FILTER = 0.8f;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Sensor mAccel;
    private float[] mGravity;
    private float[] mBaseline;
    private boolean mReduced;

    private final TriggerEventListener mListener = new TriggerEventListener() {
        @Override
        public void onTrigger(TriggerEvent event) {
            reduceRing();
        }
    };

    private final SensorEventListener mAccelListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mGravity == null) {
                mGravity = event.values.clone();
                mBaseline = mGravity.clone();
                return;
            }
            for (int i = 0; i < 3; i++) {
                mGravity[i] = GRAVITY_FILTER * mGravity[i] + (1 - GRAVITY_FILTER) * event.values[i];
            }
            if (angleDegrees(mBaseline, mGravity) >= TILT_DEGREES) reduceRing();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void reduceRing() {
        if (mReduced) return;
        mReduced = true;
        AudioManager audio = getSystemService(AudioManager.class);
        int volume = audio.getStreamVolume(AudioManager.STREAM_RING);
        // Never 0: that would switch the ringer mode to vibrate.
        int reduced = Math.max(1, volume / 3);
        if (volume <= reduced) return;
        prefs(this).edit().putInt(KEY_SAVED_VOLUME, volume).apply();
        audio.setStreamVolume(AudioManager.STREAM_RING, reduced, 0);
    }

    private static float angleDegrees(float[] a, float[] b) {
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        double norms = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
                * Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
        if (norms == 0) return 0;
        return (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot / norms))));
    }

    public static boolean isSupported(Context context) {
        return pickupSensor(context) != null || accelerometer(context) != null;
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

    private static Sensor accelerometer(Context context) {
        return context.getSystemService(SensorManager.class)
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public void onCreate() {
        mSensorManager = getSystemService(SensorManager.class);
        mSensor = pickupSensor(this);
        if (mSensor != null) mSensorManager.requestTriggerSensor(mListener, mSensor);
        mAccel = accelerometer(this);
        if (mAccel != null) {
            mSensorManager.registerListener(mAccelListener, mAccel,
                    SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mSensor != null) mSensorManager.cancelTriggerSensor(mListener, mSensor);
        if (mAccel != null) mSensorManager.unregisterListener(mAccelListener);
        restoreVolume(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
