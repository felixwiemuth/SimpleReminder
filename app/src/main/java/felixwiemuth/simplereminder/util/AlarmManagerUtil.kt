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

package felixwiemuth.simplereminder.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.Date

object AlarmManagerUtil {
    /**
     * Schedule an exact alarm (and normal alarm if not possible). Delegates to the correct method depending on SDK version.
     */
    fun scheduleExact(context: Context, date: Date, pendingIntent: PendingIntent) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // From API 31, need to check for permission, otherwise get SecurityException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w("Scheduling", "Missing permission to schedule exact alarm")
            // Falling back to less precise alarm
            alarmManager.set(AlarmManager.RTC_WAKEUP, date.time, pendingIntent)
            Log.d("Scheduling", "Set alarm for " + DateTimeUtil.formatDateTime(date))
            return
        }

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    date.time,
                    pendingIntent
                )
                Log.d("Scheduling", "Set alarm (\"exact and allow while idle\") for " + DateTimeUtil.formatDateTime(date))
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, date.time, pendingIntent)
                Log.d("Scheduling", "Set alarm (\"exact\") for " + DateTimeUtil.formatDateTime(date))
            }

            else -> {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date.time, pendingIntent)
                Log.d("Scheduling", "Set alarm for " + DateTimeUtil.formatDateTime(date))
            }
        }
    }
}