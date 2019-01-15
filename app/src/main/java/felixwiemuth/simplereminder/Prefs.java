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

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import static android.content.Context.MODE_PRIVATE;

/**
 * @author Felix Wiemuth
 */
public class Prefs {
    /**
     * Name of preferences that store the internal state of the app, like scheduled notifications.
     */
    public static String PREFS_STATE = "state";
    public static String PREF_STATE_NEXTID = "nextid";

    public static String PREFS_SETTINGS = "settings";

    /**
     * GSON-serialized list of {@link felixwiemuth.simplereminder.data.Reminder}s.
     */
    public static String PREF_STATE_CURRENT_REMINDERS = "reminders";

    public static SharedPreferences getStatePrefs(Context context) {
        return context.getSharedPreferences(PREFS_STATE, MODE_PRIVATE);
    }

    public static SharedPreferences getSettings(Context context) {
        return context.getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
    }


    /**
     * Get a string from settings preferences using a key from a string resource.
     *
     * @param key         {@link} the resource id of the key
     * @param defValue    the default value to be used it the preference is not set
     * @param context
     * @return
     */
    public static String getStringPref(@StringRes int key, String defValue, Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(key), defValue);
    }

    /**
     * Get a boolean from default preferences using a key from a string resource.
     *
     * @param key         {@link} the resource id of the key
     * @param defValue    the default value to be used it the preference is not set
     * @param context
     * @return
     */
    public static boolean getBooleanPref(@StringRes int key, boolean defValue, Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(key), defValue);
    }
}
