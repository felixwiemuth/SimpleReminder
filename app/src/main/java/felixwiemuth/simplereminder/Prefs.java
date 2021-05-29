/*
 * Copyright (C) 2018-2021 Felix Wiemuth and contributors (see CONTRIBUTORS.md)
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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import static android.content.Context.MODE_PRIVATE;

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

    public static void setRemindersUpdated(boolean b, Context context) {
        getStatePrefs(context).edit().putBoolean(PREF_STATE_REMINDERS_UPDATED, b).commit();
    }

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
     * Check whether reschedule on boot is activated. If yes, check whether the required permission is granted (if not, deactivate this option). If not, reschedule reminders.
     *
     * @param context
     * @return if true, schedule on boot is not activated and it should be manually rescheduled at the start of the app
     */
    public static void checkRescheduleOnBoot(Context context) {
        if (getBooleanPref(R.string.prefkey_run_on_boot, false, context)) {
            if (ContextCompat.checkSelfPermission(context.getApplicationContext(), Manifest.permission.RECEIVE_BOOT_COMPLETED) != PackageManager.PERMISSION_GRANTED) {
                PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREF_KEY_RUN_ON_BOOT, false).apply();
                BootReceiver.setBootReceiverEnabled(context, false);
            }
        } else {
            ReminderManager.scheduleAllReminders(context);
        }
    }

    /**
     * Try to enable running on boot; if the required permission is not granted, ask the user on the given activity.
     *
     * @param context
     * @param activity
     */
    public static void enableRunOnBoot(Context context, Activity activity) {
        // If the required permission is not granted yet, ask the user
        if (ContextCompat.checkSelfPermission(context.getApplicationContext(), Manifest.permission.RECEIVE_BOOT_COMPLETED) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECEIVE_BOOT_COMPLETED}, PERMISSION_REQUEST_CODE_BOOT);
        }
        // If permission is now given, enable run on boot
        if (ContextCompat.checkSelfPermission(context.getApplicationContext(), Manifest.permission.RECEIVE_BOOT_COMPLETED) == PackageManager.PERMISSION_GRANTED) {
            BootReceiver.setBootReceiverEnabled(context, true);
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREF_KEY_RUN_ON_BOOT, true).apply();
        } else {
            Toast.makeText(context, R.string.toast_permission_not_granted, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Check the system settings on whether battery optimization is disabled for this app.
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

    /**
     * Get a string from settings preferences using a key from a string resource.
     *
     * @param key      {@link} the resource id of the key
     * @param defValue the default value to be used it the preference is not set
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
     * @param defValue the default value to be used it the preference is not set
     * @param context
     * @return
     */
    public static boolean getBooleanPref(@StringRes int key, boolean defValue, Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(key), defValue);
    }
}
