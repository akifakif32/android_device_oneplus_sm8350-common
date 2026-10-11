/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.iris;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.ArraySet;

import androidx.preference.PreferenceManager;

import org.lineageos.device.DeviceSettings.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The apps and screens motion smoothing is requested for. An entry is either
 * "package" (the whole app) or "package/class" (one activity).
 */
final class IrisApps {

    static final String KEY_ENTRIES = "iris_memc_entries";
    private static final String KEY_RECENT = "iris_memc_recent_";
    private static final int MAX_RECENT = 5;

    private IrisApps() {
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /** The user's entries, or the built-in list until they change something. */
    static Set<String> getEntries(Context context) {
        Set<String> saved = prefs(context).getStringSet(KEY_ENTRIES, null);
        return new ArraySet<>(saved != null ? saved : Arrays.asList(
                context.getResources().getStringArray(R.array.iris_memc_activities)));
    }

    static void setEntries(Context context, Set<String> entries) {
        prefs(context).edit().putStringSet(KEY_ENTRIES, new ArraySet<>(entries)).apply();
    }

    /** Activities of the package last seen on top, newest first. */
    static List<String> getRecent(Context context, String packageName) {
        String saved = prefs(context).getString(KEY_RECENT + packageName, "");
        return saved.isEmpty() ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(saved.split("\n")));
    }

    // ponytail: one small preference per package ever opened; prune on
    // package removal if the file grows.
    static void addRecent(Context context, ComponentName top) {
        List<String> recent = getRecent(context, top.getPackageName());
        if (!recent.isEmpty() && recent.get(0).equals(top.getClassName())) {
            return;
        }
        recent.remove(top.getClassName());
        recent.add(0, top.getClassName());
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        prefs(context).edit().putString(KEY_RECENT + top.getPackageName(),
                TextUtils.join("\n", recent)).apply();
    }
}
