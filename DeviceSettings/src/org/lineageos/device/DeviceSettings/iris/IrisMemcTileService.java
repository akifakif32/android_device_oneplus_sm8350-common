/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.iris;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

/** Quick settings tile for the motion smoothing switch. */
public class IrisMemcTileService extends TileService {

    @Override
    public void onStartListening() {
        update();
    }

    @Override
    public void onClick() {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(IrisMemcService.KEY_MEMC, !IrisMemcService.isEnabled(this))
                .apply();
        IrisMemcService.sync(this);
        update();
    }

    private void update() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(IrisMemcService.isEnabled(this)
                ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
