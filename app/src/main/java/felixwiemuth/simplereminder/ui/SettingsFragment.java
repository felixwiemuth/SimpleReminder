/*
 * Copyright (C) 2019 Felix Wiemuth
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package felixwiemuth.simplereminder.ui;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import felixwiemuth.simplereminder.Prefs;
import felixwiemuth.simplereminder.R;
import felixwiemuth.simplereminder.BootReceiver;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        Preference batPref = getPreferenceScreen().findPreference(getString(R.string.prefkey_disable_battery_optimization));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            updateBatteryPrefDescription(batPref);
        } else {
            batPref.getParent().removePreference(batPref);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        Preference batPref = getPreferenceScreen().findPreference(getString(R.string.prefkey_disable_battery_optimization));
        if (batPref != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // the second condition should already follow from the first
            updateBatteryPrefDescription(batPref);
        }
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case Prefs.PREF_KEY_RUN_ON_BOOT:
                if (sharedPreferences.getBoolean(key, false)) {
                    // Note: this will set the preference again through SharedPreferences, but that should not result in calling this listener again.
                    Prefs.enableRunOnBoot(getContext(), getActivity());
                } else {
                    // Disable run on boot
                    BootReceiver.setBootReceiverEnabled(getContext(), false);
                }
                break;
        }
    }

    /**
     * Update description and on-click listener of the battery optimization preference.
     * @param batPref
     */
    @RequiresApi(23)
    private void updateBatteryPrefDescription(Preference batPref) {
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        if (pm.isIgnoringBatteryOptimizations(getContext().getPackageName())) {
            batPref.setSummary(R.string.preference_disable_battery_optimization_summary_yes);
            batPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
                updateBatteryPrefDescription(batPref);
                return true;
            });
        } else {
            batPref.setSummary(R.string.preference_disable_battery_optimization_summary_no);
            batPref.setOnPreferenceClickListener(preference -> {
                @SuppressLint("BatteryLife") Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                startActivity(intent);
                updateBatteryPrefDescription(batPref);
                return true;
            });
        }
    }
}
