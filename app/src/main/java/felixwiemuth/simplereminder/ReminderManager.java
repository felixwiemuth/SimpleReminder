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

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import felixwiemuth.simplereminder.data.Reminder;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static felixwiemuth.simplereminder.SharedPrefs.PREF_STATE_CURRENT_REMINDERS;
import static felixwiemuth.simplereminder.SharedPrefs.PREF_STATE_NEXTID;

/**
 * Manages reminders by allowing to add and change reminders.
 *
 * @author Felix Wiemuth
 */
public class ReminderManager {
    @SuppressLint("ApplySharedPref")
    public static void addReminder(Context context, long date, String text) {
        // Get next alarm ID
        SharedPreferences prefs = context.getSharedPreferences(SharedPrefs.PREFS_STATE, MODE_PRIVATE);
        final int nextId = prefs.getInt(PREF_STATE_NEXTID, 0);
        Reminder reminder = new Reminder(nextId, date, text);

        List<Reminder> currentReminders = getRemindersFromPrefs(prefs);
        List<Reminder> updatedReminders = new ArrayList<>(currentReminders);
        updatedReminders.add(reminder);

        prefs.edit()
                .putInt(PREF_STATE_NEXTID, nextId + 1)
                .putString(PREF_STATE_CURRENT_REMINDERS, Reminder.toJson(updatedReminders))
                .commit(); // use commit so that the ID really cannot be used again


        // Prepare pending intent
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        Intent processIntent = new Intent(context, ReminderService.class);
        processIntent
                .putExtra(ReminderService.EXTRA_INT_ID, nextId)
                .putExtra(ReminderService.EXTRA_STRING_REMINDER_TEXT, text);
        PendingIntent alarmIntent = PendingIntent.getService(context, nextId, processIntent, 0);

        // Schedule alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date, alarmIntent);
            Log.d("ReminderManager", "Set alarm (\"exact and allow while idle\") for " + DateFormat.getDateTimeInstance().format(date));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, date, alarmIntent);
            Log.d("ReminderManager", "Set alarm (\"exact\") for " + DateFormat.getDateTimeInstance().format(date));
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, date, alarmIntent);
            Log.d("ReminderManager", "Set alarm for " + DateFormat.getDateTimeInstance().format(date));
        }
    }

    public static List<Reminder> getReminders(Context context) {
        return getRemindersFromPrefs(SharedPrefs.getStatePrefs(context));
    }

    /**
     * Returns an immutable list of the saved reminders.
     *
     * @param prefs
     * @return
     */
    public static List<Reminder> getRemindersFromPrefs(SharedPreferences prefs) {
        return Reminder.fromJson(prefs.getString(PREF_STATE_CURRENT_REMINDERS, "[]")); //TODO check
    }
}
