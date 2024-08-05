/*
 * Copyright (C) 2018-2024 Felix Wiemuth and contributors (see CONTRIBUTORS.md)
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

import android.app.AlarmManager
import android.content.Context
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
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import felixwiemuth.simplereminder.BootReceiver
import felixwiemuth.simplereminder.Prefs
import felixwiemuth.simplereminder.R
import felixwiemuth.simplereminder.ReminderManager
import felixwiemuth.simplereminder.ui.util.UIUtils
import felixwiemuth.simplereminder.util.DateTimeUtil

class SettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<SwitchPreferenceCompat>(getString(R.string.prefkey_run_on_boot))?.apply {
            summaryOff = UIUtils.makeAlertText(R.string.preference_run_on_boot_summary_off, requireContext())
            setSummaryOn(R.string.preference_run_on_boot_summary_on)
        }

        findPreference<Preference>(getString(R.string.prefkey_disable_battery_optimization))?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                updateBatteryPrefDescription(this)
            } else {
                parent?.removePreference(this)
            }
        }

        findPreference<EditTextPreference>(getString(R.string.prefkey_nagging_repeat_interval))?.apply {
            setOnBindEditTextListener { editText: EditText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
            summaryProvider = SummaryProvider { _: Preference? ->
                DateTimeUtil.formatMinutes(Prefs.getNaggingRepeatInterval(context).toLong(), context)
            }
            // Validation
            onPreferenceChangeListener =
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
        }

        // Reminder dialog customizations only apply for Android >= 5.0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            findPreference<PreferenceCategory>(getString(R.string.prefkey_cat_ui))?.apply {
                parent?.removePreference(this)
            }
        }

        // Priority/Sound settings only work for Android < 8
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationsPrefGroup = findPreference<PreferenceCategory>(getString(R.string.prefkey_cat_notifications))
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

        findPreference<Preference>(getString(R.string.prefkey_reset_dont_show_again))?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                Prefs.resetAllDontShowAgain(context)
                Toast.makeText(context, R.string.toast_reset_dont_show_again, Toast.LENGTH_LONG).show()
                true
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) {
            return
        }
        when (key) {
            Prefs.PREF_KEY_RUN_ON_BOOT ->
                if (sharedPreferences.getBoolean(key, false)) {
                    // Note: This will set the preference again through SharedPreferences, but that should not result in calling this listener again.
                    Prefs.enableRunOnBoot(context, activity)
                } else {
                    // Disable run on boot
                    BootReceiver.setBootReceiverEnabled(requireContext(), false)
                }
        }
    }

    /**
     * Update description and on-click listener of the battery optimization preference.
     * @param batPref
     */
    @RequiresApi(23)
    private fun updateBatteryPrefDescription(batPref: Preference) {
        val api = Build.VERSION.SDK_INT
        val canScheduleExact =

            if (Prefs.isIgnoringBatteryOptimization(context)) {
                batPref.summary = getString(
                    when {
                        api >= 33 -> R.string.preference_disable_battery_optimization_summary_yes_API33
                        api >= 31 -> R.string.preference_disable_battery_optimization_summary_yes_API31
                        else -> R.string.preference_disable_battery_optimization_summary_yes
                    }
                )
                batPref.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                        updateBatteryPrefDescription(batPref)
                        true
                    }
            } else {
                // NOTE: As the text should change with "setSummary" here, the markup should apply. Should the text be equal, would need a workaround.
                batPref.summary =
                    when {
                        api >= 33 -> getString(R.string.preference_disable_battery_optimization_summary_no_API33)
                        // noinspection NewApi
                        api >= 31 -> if ((requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms())
                            getString(R.string.preference_disable_battery_optimization_summary_no_API31_exactAllowed)
                        else
                            UIUtils.makeAlertText(
                                R.string.preference_disable_battery_optimization_summary_no_API31_exactNotAllowed,
                                requireContext()
                            )

                        else -> UIUtils.makeAlertText(R.string.preference_disable_battery_optimization_summary_no, requireContext())
                    }
                batPref.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            && !(requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
                        ) {
                            startActivity(Prefs.getIntentScheduleExactSettings(context))
                        } else {
                            startActivity(Prefs.getIntentDisableBatteryOptimization(context))
                        }
                        updateBatteryPrefDescription(batPref)
                        true
                    }
            }
    }
}