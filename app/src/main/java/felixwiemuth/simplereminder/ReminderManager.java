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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.text.DateFormat;

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static felixwiemuth.simplereminder.Constants.PREF_STATE_NEXTID;

/**
 * Manages reminders by allowing to add and change reminders.
 *
 * @author Felix Wiemuth
 */
public class ReminderManager {
    public static void addReminder(Context context, long time, String text) {
        // Get next alarm ID
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_STATE, MODE_PRIVATE);
        final int nextId = prefs.getInt(PREF_STATE_NEXTID, 0);
        prefs.edit().putInt(PREF_STATE_NEXTID, nextId + 1).commit(); // use commit so that the ID really cannot be used again


        // Prepare pending intent
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        Intent processIntent = new Intent(context, ReminderService.class);
        processIntent
                .putExtra(ReminderService.EXTRA_INT_ID, nextId)
                .putExtra(ReminderService.EXTRA_STRING_REMINDER_TEXT, text);
        PendingIntent alarmIntent = PendingIntent.getService(context, nextId, processIntent, 0);

        // Schedule alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, alarmIntent);
            Log.d("ReminderManager", "Set alarm (\"exact and allow while idle\") for " + DateFormat.getDateTimeInstance().format(time));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, time, alarmIntent);
            Log.d("ReminderManager", "Set alarm (\"exact\") for " + DateFormat.getDateTimeInstance().format(time));
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, time, alarmIntent);
            Log.d("ReminderManager", "Set alarm for " + DateFormat.getDateTimeInstance().format(time));
        }
    }
}
