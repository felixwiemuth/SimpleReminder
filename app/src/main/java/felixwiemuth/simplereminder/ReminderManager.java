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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static android.content.Context.ALARM_SERVICE;
import static felixwiemuth.simplereminder.SharedPrefs.PREF_STATE_CURRENT_REMINDERS;
import static felixwiemuth.simplereminder.SharedPrefs.PREF_STATE_NEXTID;

/**
 * Manages reminders by allowing to add and change reminders.
 *
 * @author Felix Wiemuth
 */
public class ReminderManager {
    /**
     * Lock guarding the state preferences ({@link SharedPrefs#PREFS_STATE}). This reference being null is equivalent to the lock not being aquired.
     */
    private static Lock prefStateLock;

    private static void lock() {
        if (prefStateLock == null) {
            prefStateLock = new ReentrantLock();
        }
        prefStateLock.lock();
    }

    private static void unlock() {
        if (prefStateLock == null) {
            return;
        }
    }

    interface StatePrefEditOperation {
        void edit(SharedPreferences prefs, SharedPreferences.Editor editor);
    }

    /**
     * Edit the state preferences ({@link SharedPrefs#PREFS_STATE}) exclusively and commit after the operation has successfully completed. This ensures that different threads editing these preferences do not overwrite their changes.
     *
     * @param context
     * @param operation
     */
    @SuppressLint("ApplySharedPref")
    private static void performExclusivelyOnStatePrefsAndCommit(Context context, StatePrefEditOperation operation) {
        lock();
        try {
            SharedPreferences prefs = SharedPrefs.getStatePrefs(context);
            SharedPreferences.Editor editor = prefs.edit();
            operation.edit(prefs, editor);
            editor.commit();
        } finally {
            unlock();
        }
    }

    @SuppressLint("ApplySharedPref")
    public static void addReminder(Context context, long date, String text) {
        performExclusivelyOnStatePrefsAndCommit(context, (prefs, editor) -> {

            // Get next alarm ID
            final int nextId = prefs.getInt(PREF_STATE_NEXTID, 0);
            Reminder reminder = new Reminder(nextId, date, text);

            List<Reminder> currentReminders = getRemindersFromPrefs(prefs);
            List<Reminder> updatedReminders = new ArrayList<>(currentReminders);
            updatedReminders.add(reminder);

            editor
                    .putInt(PREF_STATE_NEXTID, nextId + 1)
                    .putString(PREF_STATE_CURRENT_REMINDERS, Reminder.toJson(updatedReminders));

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
        });
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
