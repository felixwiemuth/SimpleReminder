/*
 * Copyright (C) 2018-2022 Felix Wiemuth and contributors (see CONTRIBUTORS.md)
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
package felixwiemuth.simplereminder.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.preference.*
import androidx.preference.Preference.SummaryProvider
import felixwiemuth.simplereminder.BootReceiver
import felixwiemuth.simplereminder.Prefs
import felixwiemuth.simplereminder.R
import felixwiemuth.simplereminder.ReminderManager
import felixwiemuth.simplereminder.ui.util.UIUtils
import felixwiemuth.simplereminder.util.DateTimeUtil

class SettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val runOnBootPref = findPreference<SwitchPreferenceCompat>(getString(R.string.prefkey_run_on_boot))
        runOnBootPref!!.summaryOff = UIUtils.makeAlertText(R.string.preference_run_on_boot_summary_off, context)
        runOnBootPref.setSummaryOn(R.string.preference_run_on_boot_summary_on)
        val batPref = findPreference<Preference>(getString(R.string.prefkey_disable_battery_optimization))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            updateBatteryPrefDescription(batPref)
        } else {
            batPref!!.parent!!.removePreference(batPref)
        }
        val naggingRepeatIntervalPref = findPreference<EditTextPreference>(getString(R.string.prefkey_nagging_repeat_interval))
        naggingRepeatIntervalPref!!.setOnBindEditTextListener { editText: EditText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }
        naggingRepeatIntervalPref.summaryProvider = SummaryProvider { _: Preference? ->
            DateTimeUtil.formatMinutes(Prefs.getNaggingRepeatInterval(context).toLong(), context)
        }
        // Validation
        naggingRepeatIntervalPref.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener listener@{ _: Preference?, newValue: Any ->
                try {
                    val i = newValue.toString().toInt()
                    if (i > 0) {
                        return@listener true
                    }
                } catch (ex: NumberFormatException) {
                    // Incorrect format, handled below
                }
                Toast.makeText(context, R.string.preference_nagging_repeat_interval_format_error, Toast.LENGTH_LONG).show()
                false
            }

        // Priority/Sound settings only work for Android < 8
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationsPrefGroup = findPreference<PreferenceCategory>(getString(R.string.prefkey_notifications))
            notificationsPrefGroup!!.removePreference(findPreference(getString(R.string.prefkey_priority))!!)
            notificationsPrefGroup.removePreference(findPreference(getString(R.string.prefkey_enable_sound))!!)
            val notificationChannelPreference = Preference(requireContext())
            notificationChannelPreference.setTitle(R.string.preference_notification_channel_settings)
            notificationChannelPreference.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    intent.putExtra(Settings.EXTRA_CHANNEL_ID, ReminderManager.NOTIFICATION_CHANNEL_REMINDER)
                    startActivity(intent)
                    true
                }
            notificationChannelPreference.isIconSpaceReserved = false
            notificationsPrefGroup.addPreference(notificationChannelPreference)
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
        findPreference<Preference>(getString(R.string.prefkey_disable_battery_optimization))?.let {
            // This condition should hold when the preference is present (but it is necessary for type checking)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                updateBatteryPrefDescription(it)
            }
        }
    }

    override fun onPause() {
        preferenceManager.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            Prefs.PREF_KEY_RUN_ON_BOOT ->
                if (sharedPreferences.getBoolean(key, false)) {
                    // Note: This will set the preference again through SharedPreferences, but that should not result in calling this listener again.
                    Prefs.enableRunOnBoot(context, activity)
                } else {
                    // Disable run on boot
                    BootReceiver.setBootReceiverEnabled(context, false)
                }
        }
    }

    /**
     * Update description and on-click listener of the battery optimization preference.
     * @param batPref
     */
    @RequiresApi(23)
    private fun updateBatteryPrefDescription(batPref: Preference?) {
        if (Prefs.isIgnoringBatteryOptimization(context)) {
            batPref!!.setSummary(R.string.preference_disable_battery_optimization_summary_yes)
            batPref.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                    updateBatteryPrefDescription(batPref)
                    true
                }
        } else {
            // NOTE: As the text should change with "setSummary" here, the markup should apply. Should the text be equal, would need a workaround.
            batPref!!.summary = UIUtils.makeAlertText(R.string.preference_disable_battery_optimization_summary_no, context)
            batPref.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    startActivity(Prefs.getIntentDisableBatteryOptimization(context))
                    updateBatteryPrefDescription(batPref)
                    true
                }
        }
    }
}