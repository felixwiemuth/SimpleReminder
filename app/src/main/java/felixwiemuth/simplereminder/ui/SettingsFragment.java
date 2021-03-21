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


import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import felixwiemuth.simplereminder.BootReceiver;
import felixwiemuth.simplereminder.Prefs;
import felixwiemuth.simplereminder.R;
import felixwiemuth.simplereminder.ReminderService;
import felixwiemuth.simplereminder.ui.util.UIUtils;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        SwitchPreference runOnBootPref = getPreferenceScreen().findPreference(getString(R.string.prefkey_run_on_boot));
        runOnBootPref.setSummaryOff(UIUtils.makeAlertText(R.string.preference_run_on_boot_summary_off, getContext()));
        runOnBootPref.setSummaryOn(R.string.preference_run_on_boot_summary_on);

        Preference batPref = getPreferenceScreen().findPreference(getString(R.string.prefkey_disable_battery_optimization));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            updateBatteryPrefDescription(batPref);
        } else {
            batPref.getParent().removePreference(batPref);
        }

        // Priority/Sound settings only work for Android < 8
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PreferenceCategory notificationsPrefGroup = getPreferenceScreen().findPreference(getString(R.string.prefkey_notifications));
            notificationsPrefGroup.removePreference(getPreferenceScreen().findPreference(getString(R.string.prefkey_priority)));
            notificationsPrefGroup.removePreference(getPreferenceScreen().findPreference(getString(R.string.prefkey_enable_sound)));

            Preference notificationChannelPreference = new Preference(getContext());
            notificationChannelPreference.setTitle(R.string.preference_notification_channel_settings);
            notificationChannelPreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
                intent.putExtra(Settings.EXTRA_CHANNEL_ID, ReminderService.NOTIFICATION_CHANNEL_REMINDER);
                startActivity(intent);
                return true;
            });
            notificationsPrefGroup.addPreference(notificationChannelPreference);
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
        if (Prefs.isIgnoringBatteryOptimization(getContext())) {
            batPref.setSummary(R.string.preference_disable_battery_optimization_summary_yes);
            batPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
                updateBatteryPrefDescription(batPref);
                return true;
            });
        } else {
            // NOTE: As the text should change with "setSummary" here, the markup should apply. Should the text be equal, would need a workaround.
            batPref.setSummary(UIUtils.makeAlertText(R.string.preference_disable_battery_optimization_summary_no, getContext()));

            batPref.setOnPreferenceClickListener(preference -> {
                startActivity(Prefs.getIntentDisableBatteryOptimization(getContext()));
                updateBatteryPrefDescription(batPref);
                return true;
            });
        }
    }
}
