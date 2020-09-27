/*
 * Copyright (C) 2018 Felix Wiemuth
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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.annotation.StringRes;
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
    private static String PREFS_STATE = "state";


    // User settings are in the default shared preferences
//    /**
//     * Name of preferences that store user settings.
//     */
//    private static String PREFS_SETTINGS = "settings";

    /**
     * The version of the format reminders are saved at key {@link #PREF_STATE_CURRENT_REMINDERS}.
     */
    private static String PREF_STATE_REMINDERS_FORMAT_VERSION = "remindersFormatVersion";

    /**
     * The next ID for a reminder.
     */
    static String PREF_STATE_NEXTID = "nextid";

    /**
     * GSON-serialized list of {@link felixwiemuth.simplereminder.data.Reminder}s.
     */
    static String PREF_STATE_CURRENT_REMINDERS = "reminders";

    /**
     * Indicates whether the list of reminders {@link #PREF_STATE_CURRENT_REMINDERS} has been updated.
     */
    private static String PREF_STATE_REMINDERS_UPDATED = "remindersUpdated";

    private static String PREF_STATE_WELCOME_MESSAGE_SHOWN = "welcomeMessageShown";

    static SharedPreferences getStatePrefs(Context context) {
        return context.getSharedPreferences(PREFS_STATE, MODE_PRIVATE);
    }

//    public static SharedPreferences getSettings(Context context) {
//        return context.getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
//    }

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
     * Checks whether the welcome message for the current version has already been shown and updates the shown status to the current version.
     *
     * @param context
     * @return
     */
    public static boolean checkWelcomeMessageShown(Context context) {
        int lastShown = getStatePrefs(context).getInt(PREF_STATE_WELCOME_MESSAGE_SHOWN, -1);
        int currentVersion;
        try {
            currentVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Cannot show welcome message", e);
        }
        if (lastShown != currentVersion) {
            getStatePrefs(context).edit().putInt(PREF_STATE_WELCOME_MESSAGE_SHOWN, currentVersion).apply();
            return false;
        } else {
            return true;
        }
    }

    /**
     * Check whether reschedule on boot is activated. If yes, check whether the required permission is granted (if not, deactivate this option). If no, reschedule reminders.
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
