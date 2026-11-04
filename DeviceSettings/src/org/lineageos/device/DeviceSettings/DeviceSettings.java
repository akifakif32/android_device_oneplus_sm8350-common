/*
 * Copyright (C) 2018-2022 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.device.DeviceSettings;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.Vibrator;
import android.text.TextUtils;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import android.util.ArrayMap;
import java.util.List;
import java.util.Map;
import org.lineageos.internal.util.FileUtils;
import org.lineageos.device.DeviceSettings.iris.IrisMemcService;

public class DeviceSettings extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String FILE_GAME = "/proc/touchpanel/game_switch_enable";
    private static final String FILE_EDGE = "/proc/touchpanel/oplus_tp_direction";
    private static final String FILE_FAST_CHARGE = "/sys/module/oplus_chg/parameters/force_fast_charge";
    private static final String FILE_LEVEL = "/sys/devices/platform/soc/88c000.i2c/i2c-6/6-005a/leds/vibrator/level";

    private static final String KEY_GAME_SWITCH = "game_mode";
    private static final String KEY_EDGE_TOUCH = "edge_touch";
    private static final String KEY_USB2_SWITCH = "usb2_fast_charge";
    private static final String KEY_VIBSTRENGTH = "vib_strength";
    private static final String KEY_VIDEO_COLOR = "video_color";
    private static final String KEY_VIDEO_SR = "video_sr";
    private static final String KEY_APTX_LOW_LATENCY = "aptx_adaptive_low_latency";

    // Native BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_ADAPTIVE, and the aptX Adaptive mode bits of
    // codec_specific_4 the Bluetooth stack takes (Qualcomm APTX_HQ / APTX_LL)
    private static final int CODEC_TYPE_APTX_ADAPTIVE = 7;
    private static final long APTX_MODE_HQ = 0x1000;
    private static final long APTX_MODE_LL = 0x2000;

    private static final long[] TEST_VIB_PATTERN = { 0, 5 };
    private static final String DEFAULT_VIB_LEVEL = "3";

    private static final Map<String, String> sBooleanNodePreferenceMap = new ArrayMap<>();
    private static final Map<String, String> sStringNodePreferenceMap = new ArrayMap<>();

    private SwitchPreferenceCompat mGameModeSwitch;
    private SwitchPreferenceCompat mEdgeTouchSwitch;
    private SwitchPreferenceCompat mUSB2FastChargeModeSwitch;
    private CustomSeekBarPreference mVibratorStrengthPreference;
    private Vibrator mVibrator;

    private SwitchPreferenceCompat mAptxLowLatencySwitch;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothA2dp mBluetoothA2dp;
    private final BluetoothProfile.ServiceListener mA2dpListener =
            new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mBluetoothA2dp = (BluetoothA2dp) proxy;
            updateAptxLowLatency();
        }

        @Override
        public void onServiceDisconnected(int profile) {
            mBluetoothA2dp = null;
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.main, rootKey);

        mVibrator = getContext().getSystemService(Vibrator.class);

        Preference irisCategory = findPreference("iris");
        if (!IrisMemcService.isSupported(getContext())) {
            getPreferenceScreen().removePreference(irisCategory);
        } else {
            findPreference(IrisMemcService.KEY_MEMC).setOnPreferenceChangeListener((pref, value) -> {
                // The new value is stored after this returns.
                Context context = getContext().getApplicationContext();
                new Handler(Looper.getMainLooper()).post(() -> IrisMemcService.sync(context));
                return true;
            });
        }
        
        if (!RingPickupService.isSupported(getContext())) {
            getPreferenceScreen().removePreference(findPreference("calls"));
        }

        Preference.OnPreferenceChangeListener videoListener = (pref, value) -> {
            // The new value is stored after this returns.
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            boolean isColor = KEY_VIDEO_COLOR.equals(pref.getKey());
            applyVideoEnhance(isColor ? (Boolean) value : prefs.getBoolean(KEY_VIDEO_COLOR, false),
                    isColor ? prefs.getBoolean(KEY_VIDEO_SR, false) : (Boolean) value);
            return true;
        };
        findPreference(KEY_VIDEO_COLOR).setOnPreferenceChangeListener(videoListener);
        findPreference(KEY_VIDEO_SR).setOnPreferenceChangeListener(videoListener);

        mGameModeSwitch = bindSwitchPref(KEY_GAME_SWITCH, FILE_GAME);
        mEdgeTouchSwitch = bindSwitchPref(KEY_EDGE_TOUCH, FILE_EDGE);
        mUSB2FastChargeModeSwitch = bindSwitchPref(KEY_USB2_SWITCH, FILE_FAST_CHARGE);

        mAptxLowLatencySwitch = findPreference(KEY_APTX_LOW_LATENCY);
        mAptxLowLatencySwitch.setEnabled(false);
        mAptxLowLatencySwitch.setOnPreferenceChangeListener((pref, value) -> {
            BluetoothCodecConfig current = getAptxAdaptiveConfig();
            if (current == null) {
                return false;
            }
            setAptxMode(current, (Boolean) value);
            return true;
        });
        BluetoothManager bluetoothManager = getContext().getSystemService(BluetoothManager.class);
        mBluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.getProfileProxy(getContext(), mA2dpListener, BluetoothProfile.A2DP);
        }

        mVibratorStrengthPreference = (CustomSeekBarPreference) findPreference(KEY_VIBSTRENGTH);
        if (Utils.fileWritable(FILE_LEVEL)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            mVibratorStrengthPreference.setValue(prefs.getInt(KEY_VIBSTRENGTH, Integer.parseInt(Utils.getFileValue(FILE_LEVEL, DEFAULT_VIB_LEVEL))));
            mVibratorStrengthPreference.setOnPreferenceChangeListener(this);
        } else {
            mVibratorStrengthPreference.setEnabled(false);
        }

        
    }

    private SwitchPreferenceCompat bindSwitchPref(String key, String sysfsPath) {
        SwitchPreferenceCompat pref = (SwitchPreferenceCompat) findPreference(key);
        if (pref != null) {
            if (Utils.fileWritable(sysfsPath)) {
                pref.setEnabled(true);
                pref.setChecked(PreferenceManager.getDefaultSharedPreferences(getContext())
                        .getBoolean(key, Utils.getFileValueAsBoolean(sysfsPath, false)));
                pref.setOnPreferenceChangeListener(this);
            } else {
                pref.setEnabled(false);
            }
        }
        return pref;
    }

    @Override
    public void onResume() {
        super.onResume();
        enforceTouchPanelPolicy();
        // The quick settings tile may have changed it while we were away.
        SwitchPreferenceCompat memc = findPreference(IrisMemcService.KEY_MEMC);
        if (memc != null) {
            memc.setChecked(IrisMemcService.isEnabled(getContext()));
        }
        if (getActivity() != null) {
            getActivity().setTitle(R.string.device_title);
        }
        updateAptxLowLatency();
    }

    @Override
    public void onDestroy() {
        if (mBluetoothAdapter != null && mBluetoothA2dp != null) {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, mBluetoothA2dp);
        }
        super.onDestroy();
    }

    private BluetoothCodecConfig getAptxAdaptiveConfig() {
        if (mBluetoothA2dp == null) {
            return null;
        }
        BluetoothDevice device = getActiveA2dpDevice();
        BluetoothCodecStatus status = device != null ? mBluetoothA2dp.getCodecStatus(device) : null;
        BluetoothCodecConfig config = status != null ? status.getCodecConfig() : null;
        return config != null && config.getCodecType() == CODEC_TYPE_APTX_ADAPTIVE ? config : null;
    }

    private void updateAptxLowLatency() {
        if (mAptxLowLatencySwitch == null) {
            return;
        }
        BluetoothCodecConfig current = getAptxAdaptiveConfig();
        mAptxLowLatencySwitch.setEnabled(current != null);
        // The stack keeps the mode only while Bluetooth runs, so send the saved one again.
        // ponytail: re-applied only when this page opens; a Bluetooth restart goes back to HQ until then
        if (current != null && mAptxLowLatencySwitch.isChecked()) {
            setAptxMode(current, true);
        }
    }

    private void setAptxMode(BluetoothCodecConfig current, boolean lowLatency) {
        BluetoothCodecConfig config = new BluetoothCodecConfig.Builder()
                .setExtendedCodecType(current.getExtendedCodecType())
                .setCodecPriority(BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST)
                .setSampleRate(current.getSampleRate())
                .setBitsPerSample(current.getBitsPerSample())
                .setChannelMode(current.getChannelMode())
                .setCodecSpecific1(current.getCodecSpecific1())
                .setCodecSpecific2(current.getCodecSpecific2())
                .setCodecSpecific3(current.getCodecSpecific3())
                .setCodecSpecific4(lowLatency ? APTX_MODE_LL : APTX_MODE_HQ)
                .build();
        BluetoothDevice device = getActiveA2dpDevice();
        if (device != null) {
            mBluetoothA2dp.setCodecConfigPreference(device, config);
        }
    }

    // BluetoothA2dp.getActiveDevice() is hidden; the adapter's system API is what the stubs carry
    private BluetoothDevice getActiveA2dpDevice() {
        List<BluetoothDevice> active = mBluetoothAdapter != null
                ? mBluetoothAdapter.getActiveDevices(BluetoothProfile.A2DP) : List.of();
        return active.isEmpty() ? null : active.get(0);
    }

    @Override
    public void onViewCreated(android.view.View view, android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        androidx.recyclerview.widget.RecyclerView listView = getListView();
        if (listView != null) {
            listView.setClipToPadding(false);
            int paddingBottom = (int) (24 * getResources().getDisplayMetrics().density);
            listView.setPadding(
                listView.getPaddingLeft(),
                listView.getPaddingTop(),
                listView.getPaddingRight(),
                paddingBottom
            );
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();

        if (preference == mGameModeSwitch) return applySwitch(editor, KEY_GAME_SWITCH, FILE_GAME, (Boolean) newValue);
        if (preference == mEdgeTouchSwitch) return applySwitch(editor, KEY_EDGE_TOUCH, FILE_EDGE, (Boolean) newValue);
        if (preference == mUSB2FastChargeModeSwitch) return applySwitch(editor, KEY_USB2_SWITCH, FILE_FAST_CHARGE, (Boolean) newValue);
        
        if (preference == mVibratorStrengthPreference) {
            int value = Integer.parseInt(newValue.toString());
            editor.putInt(KEY_VIBSTRENGTH, value).apply();
            Utils.writeValue(FILE_LEVEL, String.valueOf(value));
            if (mVibrator != null && mVibrator.hasVibrator()) {
                if (value > 0) {
                    int duration = 8 + (value * 3);
                    int amplitude = Math.min(160, 40 + (value * 35));
                    try {
                        mVibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, amplitude));
                    } catch (Exception e) {
                        mVibrator.vibrate(duration);
                    }
                }
            }
            return true;
        }

        

        String node = sBooleanNodePreferenceMap.get(key);
        if (!TextUtils.isEmpty(node) && FileUtils.isFileWritable(node)) {
            FileUtils.writeLine(node, (Boolean) newValue ? "1" : "0");
            return true;
        }
        
        node = sStringNodePreferenceMap.get(key);
        if (!TextUtils.isEmpty(node) && FileUtils.isFileWritable(node)) {
            FileUtils.writeLine(node, (String) newValue);
            return true;
        }

        return false;
    }

    private boolean applySwitch(SharedPreferences.Editor editor, String prefKey, String sysfsPath, boolean enabled) {
        editor.putBoolean(prefKey, enabled).apply();
        Utils.writeValue(sysfsPath, enabled ? "1" : "0");
        return true;
    }

    

    private void enforceTouchPanelPolicy() {
        if (mGameModeSwitch == null || mEdgeTouchSwitch == null) return;

        // PowerTools (and its sys.perf_mode_active writer) is not part of this
        // build, so game mode / edge touch are always user-editable -- there is
        // no power profile left that can lock them into a forced state.
        mGameModeSwitch.setEnabled(true);
        mEdgeTouchSwitch.setEnabled(true);
    }

    @Override
    public void setPreferencesFromResource(int preferencesResId, String rootKey) {
        super.setPreferencesFromResource(preferencesResId, rootKey);
        
        for (String pref : sBooleanNodePreferenceMap.keySet()) {
            SwitchPreferenceCompat b = (SwitchPreferenceCompat) findPreference(pref);
            if (b == null) continue;
            String node = sBooleanNodePreferenceMap.get(pref);
            if (FileUtils.isFileReadable(node)) {
                b.setChecked("1".equals(FileUtils.readOneLine(node)));
                b.setOnPreferenceChangeListener(this);
            } else {
                removePref(b);
            }
        }
        
        for (String pref : sStringNodePreferenceMap.keySet()) {
            ListPreference l = (ListPreference) findPreference(pref);
            if (l == null) continue;
            String node = sStringNodePreferenceMap.get(pref);
            if (FileUtils.isFileReadable(node)) {
                l.setValue(FileUtils.readOneLine(node));
                l.setOnPreferenceChangeListener(this);
            } else {
                removePref(l);
            }
        }
    }

    private void removePref(Preference pref) {
        PreferenceGroup parent = pref.getParent();
        if (parent != null) {
            parent.removePreference(pref);
            if (parent.getPreferenceCount() == 0) removePref(parent);
        }
    }

    

    

    // liboplusvppfilter.so reads these each time a video decoder starts.
    private static void applyVideoEnhance(boolean color, boolean sr) {
        SystemProperties.set("debug.oplus.filter.enable", color || sr ? "1" : "0");
        SystemProperties.set("debug.osie.enable", color ? "1" : "0");
        SystemProperties.set("debug.sr.enable", sr ? "1" : "0");
    }

    public static void restoreVideoEnhance(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        applyVideoEnhance(prefs.getBoolean(KEY_VIDEO_COLOR, false), prefs.getBoolean(KEY_VIDEO_SR, false));
    }

    public static void restoreFastChargeSetting(Context context) {
        if (Utils.fileWritable(FILE_FAST_CHARGE)) {
            boolean value = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(KEY_USB2_SWITCH, Utils.getFileValueAsBoolean(FILE_FAST_CHARGE, false));
            Utils.writeValue(FILE_FAST_CHARGE, value ? "1" : "0");
        }
    }

    public static void restoreVibStrengthSetting(Context context) {
        if (Utils.fileWritable(FILE_LEVEL)) {
            int value = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(KEY_VIBSTRENGTH, Integer.parseInt(Utils.getFileValue(FILE_LEVEL, DEFAULT_VIB_LEVEL)));
            Utils.writeValue(FILE_LEVEL, String.valueOf(value));
        }
    }
}