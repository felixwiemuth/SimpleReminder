/*
 * Copyright (C) 2018-2023 Felix Wiemuth and contributors (see CONTRIBUTORS.md)
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

package felixwiemuth.simplereminder;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

/**
 * Stores preferences and current status of the app.
 *
 * @author Felix Wiemuth
 */
public class Prefs {
    public static final String PREF_KEY_RUN_ON_BOOT = "run_on_boot";

    /**
     * Name of preferences that store the internal state of the app, like scheduled notifications.
     */
    private static final String PREFS_STATE = "state";

    /**
     * The version of the format reminders are saved at key {@link #PREF_STATE_CURRENT_REMINDERS}.
     */
    private static final String PREF_STATE_REMINDERS_FORMAT_VERSION = "remindersFormatVersion";

    /**
     * The next ID for a reminder.
     */
    static final String PREF_STATE_NEXTID = "nextid";

    /**
     * GSON-serialized list of {@link felixwiemuth.simplereminder.data.Reminder}s.
     */
    static final String PREF_STATE_CURRENT_REMINDERS = "reminders";

    /**
     * Indicates whether the list of reminders {@link #PREF_STATE_CURRENT_REMINDERS} has been updated.
     */
    private static final String PREF_STATE_REMINDERS_UPDATED = "remindersUpdated";
    private static final String PREF_STATE_WELCOME_MESSAGE_SHOWN = "welcomeMessageShown";
    private static final String PREF_STATE_ADD_REMINDER_DIALOG_USED = "AddReminderDialogUsed";

    private static final String PREF_STATE_BATTERY_OPTIMIZATION_DONT_SHOW_AGAIN = "battery_optimization_dont_show_again";
    private static final String PREF_STATE_RUN_ON_BOOT_DONT_SHOW_AGAIN = "run_on_boot_dont_show_again";

    public static final int PERMISSION_REQUEST_CODE_BOOT = 1;

    static SharedPreferences getStatePrefs(Context context) {
        return context.getSharedPreferences(PREFS_STATE, MODE_PRIVATE);
    }

    public static boolean isRemindersUpdated(Context context) {
        return getStatePrefs(context).getBoolean(PREF_STATE_REMINDERS_UPDATED, false);
    }

    @SuppressLint("ApplySharedPref")
    public static void setRemindersUpdated(boolean b, Context context) {
        getStatePrefs(context).edit().putBoolean(PREF_STATE_REMINDERS_UPDATED, b).commit();
    }

    @SuppressLint("ApplySharedPref")
    public static int getStoredRemindersListFormatVersion(Context context) {
        SharedPreferences prefs = getStatePrefs(context);
        if (!prefs.contains(PREF_STATE_REMINDERS_FORMAT_VERSION)) {
            prefs.edit().putInt(PREF_STATE_REMINDERS_FORMAT_VERSION, Main.REMINDERS_LIST_FORMAT_VERSION).commit();
        }
        return prefs.getInt(PREF_STATE_REMINDERS_FORMAT_VERSION, Main.REMINDERS_LIST_FORMAT_VERSION);
    }

    /**
     * Checks whether the welcome message has been shown and if not, saves the version at which it now is shown.
     *
     * @param context
     * @return
     */
    public static boolean checkAndUpdateWelcomeMessageShown(Context context) {
        int lastShown = getStatePrefs(context).getInt(PREF_STATE_WELCOME_MESSAGE_SHOWN, -1);
        if (lastShown == -1) {
            getStatePrefs(context).edit().putInt(PREF_STATE_WELCOME_MESSAGE_SHOWN, BuildConfig.VERSION_CODE).apply();
            return false;
        } else {
            return true;
        }
    }

    public static boolean isAddReminderDialogUsed(Context context) {
        return getStatePrefs(context).getBoolean(PREF_STATE_ADD_REMINDER_DIALOG_USED, false);
    }

    public static void setAddReminderDialogUsed(Context context) {
        getStatePrefs(context).edit().putBoolean(PREF_STATE_ADD_REMINDER_DIALOG_USED, true).apply();
    }

    public static boolean isBatteryOptimizationDontShowAgain(Context context) {
        return getStatePrefs(context).getBoolean(PREF_STATE_BATTERY_OPTIMIZATION_DONT_SHOW_AGAIN, false);
    }

    public static void setBatteryOptimizationDontShowAgain(Context context) {
        getStatePrefs(context).edit().putBoolean(PREF_STATE_BATTERY_OPTIMIZATION_DONT_SHOW_AGAIN, true).apply();
    }

    public static boolean isRunOnBootDontShowAgain(Context context) {
        return getStatePrefs(context).getBoolean(PREF_STATE_RUN_ON_BOOT_DONT_SHOW_AGAIN, false);
    }

    public static void setRunOnBootDontShowAgain(Context context) {
        getStatePrefs(context).edit().putBoolean(PREF_STATE_RUN_ON_BOOT_DONT_SHOW_AGAIN, true).apply();
    }

    public static boolean isRunOnBoot(Context context) {
        return getBooleanPref(R.string.prefkey_run_on_boot, false, context);
    }

    /**
     * Sets the run-on-boot preference and enables/disables the boot receiver.
     * @param context
     * @param value
     */
    public static void setRunOnBoot(Context context, Boolean value) {
        edit(context).putBoolean(Prefs.PREF_KEY_RUN_ON_BOOT, value).apply();
        BootReceiver.setBootReceiverEnabled(context, value);
    }

    /**
     * Try to enable running on boot; if the required permission is not granted, ask the user on the given activity.
     *
     * @param context
     * @param activity
     */
    public static void enableRunOnBoot(Context context, Activity activity) {
        // If the required permission is not granted yet, ask the user
        if (!BootReceiver.isPermissionGranted(context.getApplicationContext())) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECEIVE_BOOT_COMPLETED}, PERMISSION_REQUEST_CODE_BOOT);
        }
        // If permission is now given, enable run on boot
        if (BootReceiver.isPermissionGranted(context.getApplicationContext())) {
            setRunOnBoot(context, true);
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREF_KEY_RUN_ON_BOOT, true).apply();
        } else {
            Toast.makeText(context, R.string.toast_permission_not_granted, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Check the system settings on whether battery optimization is disabled for this app.
     *
     * @param context
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean isIgnoringBatteryOptimization(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    @RequiresApi(23)
    public static Intent getIntentDisableBatteryOptimization(Context context) {
        @SuppressLint("BatteryLife") Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        return intent;
    }

    public static int getNaggingRepeatInterval(Context context) {
        return Integer.parseInt(getStringPref(R.string.prefkey_nagging_repeat_interval, "1", context));
    }

    public static int getReminderDialogTimePickerTextSize(Context context) {
        return Integer.parseInt(getStringPref(R.string.prefkey_reminder_dialog_timepicker_text_size, "12", context));
    }

    public static int getReminderDialogTimePickerHeight(Context context) {
        return Integer.parseInt(getStringPref(R.string.prefkey_reminder_dialog_timepicker_height, "175", context));
    }

    public static boolean isDisplayOriginalDueTimeNormal(Context context) {
        return getBooleanPref(R.string.prefkey_display_original_due_time_normal, false, context);
    }

    public static boolean isDisplayOriginalDueTimeNag(Context context) {
        return getBooleanPref(R.string.prefkey_display_original_due_time_nag, false, context);
    }

    public static boolean isDisplayOriginalDueTimeRecreate(Context context) {
        return getBooleanPref(R.string.prefkey_display_original_due_time_recreate, false, context);
    }

    /**
     * Get editor for default shared preferences.
     *
     * @return
     */
    private static SharedPreferences.Editor edit(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).edit();
    }

    /**
     * Get a string from settings preferences using a key from a string resource.
     *
     * @param key      {@link} the resource id of the key
     * @param defValue the default value to be used if the preference is not set
     * @param context
     * @return
     */
    public static String getStringPref(@StringRes int key, String defValue, Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(key), defValue);
    }

    /**
     * Get a boolean from default preferences using a key from a string resource.
     *
     * @param key      {@link} the resource id of the key
     * @param defValue the default value to be used if the preference is not set
     * @param context
     * @return
     */
    public static boolean getBooleanPref(@StringRes int key, boolean defValue, Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(key), defValue);
    }

    /**
     * Get an int from default preferences using a key from a string resource.
     *
     * @param key      {@link} the resource id of the key
     * @param defValue the default value to be used if the preference is not set
     * @param context
     * @return
     */
    public static int getIntPref(@StringRes int key, int defValue, Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(context.getString(key), defValue);
    }
}
