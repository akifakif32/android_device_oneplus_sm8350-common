/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.iris;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.ArrayMap;

import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.device.DeviceSettings.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Picks where motion smoothing is requested. Without arguments it lists the
 * installed apps; with a package it lists that app's screens.
 */
public class IrisAppsFragment extends SettingsBasePreferenceFragment {

    private static final String ARG_PACKAGE = "package";

    private final Map<String, Preference> mApps = new ArrayMap<>();
    private final List<Preference> mScreens = new ArrayList<>();
    private String mPackage;
    private CharSequence mTitle;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);
        mPackage = getArguments() != null ? getArguments().getString(ARG_PACKAGE) : null;
        if (mPackage == null) {
            mTitle = getString(R.string.iris_memc_apps_title);
            addApps(context, screen);
        } else {
            addScreens(context, screen);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(mTitle);
        }
        Set<String> entries = IrisApps.getEntries(getContext());
        for (Map.Entry<String, Preference> app : mApps.entrySet()) {
            app.getValue().setSummary(appSummary(entries, app.getKey()));
        }
    }

    private Preference hint(Context context, int text) {
        Preference hint = new Preference(context);
        hint.setSummary(text);
        hint.setSelectable(false);
        return hint;
    }

    private CharSequence appSummary(Set<String> entries, String packageName) {
        if (entries.contains(packageName)) {
            return getString(R.string.iris_memc_whole_app);
        }
        int screens = 0;
        for (String entry : entries) {
            if (entry.startsWith(packageName + "/")) {
                screens++;
            }
        }
        return screens == 0 ? null : getResources().getQuantityString(
                R.plurals.iris_memc_screens, screens, screens);
    }

    // ponytail: labels and icons are loaded on the main thread, about a
    // second with a few hundred apps; move to a background load if it drags.
    private void addApps(Context context, PreferenceScreen screen) {
        PackageManager pm = context.getPackageManager();
        Set<String> entries = IrisApps.getEntries(context);
        Map<String, ApplicationInfo> apps = new ArrayMap<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
            apps.put(info.activityInfo.packageName, info.activityInfo.applicationInfo);
        }
        Map<String, String> labels = new ArrayMap<>();
        for (ApplicationInfo app : apps.values()) {
            labels.put(app.packageName, app.loadLabel(pm).toString());
        }
        List<ApplicationInfo> sorted = new ArrayList<>(apps.values());
        // Apps that are already set up first, then by name.
        sorted.sort((a, b) -> {
            boolean aOn = appSummary(entries, a.packageName) != null;
            boolean bOn = appSummary(entries, b.packageName) != null;
            return aOn != bOn ? (aOn ? -1 : 1) : labels.get(a.packageName)
                    .compareToIgnoreCase(labels.get(b.packageName));
        });

        screen.addPreference(hint(context, R.string.iris_memc_apps_hint));
        for (ApplicationInfo app : sorted) {
            Preference pref = new Preference(context);
            pref.setTitle(labels.get(app.packageName));
            pref.setIcon(app.loadIcon(pm));
            pref.setFragment(IrisAppsFragment.class.getName());
            pref.getExtras().putString(ARG_PACKAGE, app.packageName);
            screen.addPreference(pref);
            mApps.put(app.packageName, pref);
        }
    }

    private void addScreens(Context context, PreferenceScreen screen) {
        PackageManager pm = context.getPackageManager();
        Set<String> entries = IrisApps.getEntries(context);
        Set<String> all = new TreeSet<>();
        try {
            mTitle = pm.getApplicationInfo(mPackage, 0).loadLabel(pm);
            ActivityInfo[] activities = pm.getPackageInfo(mPackage,
                    PackageManager.GET_ACTIVITIES
                            | PackageManager.MATCH_DISABLED_COMPONENTS).activities;
            if (activities != null) {
                for (ActivityInfo activity : activities) {
                    all.add(activity.name);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            mTitle = mPackage;
        }
        // Saved screens the app no longer declares stay listed so they can
        // be removed.
        for (String entry : entries) {
            if (entry.startsWith(mPackage + "/")) {
                all.add(entry.substring(mPackage.length() + 1));
            }
        }
        List<String> recent = IrisApps.getRecent(context, mPackage);
        all.removeAll(recent);

        SwitchPreferenceCompat whole = new SwitchPreferenceCompat(context);
        whole.setPersistent(false);
        whole.setTitle(R.string.iris_memc_whole_app);
        whole.setSummary(R.string.iris_memc_whole_app_summary);
        whole.setChecked(entries.contains(mPackage));
        whole.setOnPreferenceChangeListener((pref, value) -> {
            setEntry(mPackage, (Boolean) value);
            for (Preference item : mScreens) {
                item.setEnabled(!(Boolean) value);
            }
            return true;
        });
        screen.addPreference(whole);

        PreferenceCategory recentGroup = new PreferenceCategory(context);
        recentGroup.setTitle(R.string.iris_memc_recent_screens);
        screen.addPreference(recentGroup);
        if (recent.isEmpty()) {
            recentGroup.addPreference(hint(context, R.string.iris_memc_recent_hint));
        }
        for (String name : recent) {
            addScreen(context, recentGroup, entries, name);
        }

        PreferenceCategory allGroup = new PreferenceCategory(context);
        allGroup.setTitle(R.string.iris_memc_all_screens);
        screen.addPreference(allGroup);
        for (String name : all) {
            addScreen(context, allGroup, entries, name);
        }
        for (Preference item : mScreens) {
            item.setEnabled(!whole.isChecked());
        }
    }

    private void addScreen(Context context, PreferenceGroup group, Set<String> entries,
            String className) {
        String entry = mPackage + "/" + className;
        CheckBoxPreference pref = new CheckBoxPreference(context);
        pref.setPersistent(false);
        pref.setTitle(className.substring(className.lastIndexOf('.') + 1));
        pref.setSummary(className);
        pref.setChecked(entries.contains(entry));
        pref.setOnPreferenceChangeListener((p, value) -> {
            setEntry(entry, (Boolean) value);
            return true;
        });
        group.addPreference(pref);
        mScreens.add(pref);
    }

    private void setEntry(String entry, boolean on) {
        Set<String> entries = IrisApps.getEntries(getContext());
        if (on) {
            entries.add(entry);
        } else {
            entries.remove(entry);
        }
        IrisApps.setEntries(getContext(), entries);
    }
}
