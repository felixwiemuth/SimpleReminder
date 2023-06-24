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
package felixwiemuth.simplereminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import felixwiemuth.simplereminder.data.Reminder
import felixwiemuth.simplereminder.data.Reminder.Status
import felixwiemuth.simplereminder.ui.EditReminderDialogActivity
import felixwiemuth.simplereminder.util.AlarmManagerUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

/**
 * Manages current reminders by allowing to add, update and remove reminders. Also handles scheduling of notifications.
 */
object ReminderManager {

    /**
     * ID of the main notification channel "Reminder".
     */
    const val NOTIFICATION_CHANNEL_REMINDER = "Reminder"

    private const val OFFSET_REQUEST_CODE_ADD_REMINDER_DIALOG_ACTIVITY_PENDING_INTENT = Reminder.MAX_REMINDER_ID + 1

    /**
     * Request code for a pending intent to be used to start [felixwiemuth.simplereminder.ui.EditReminderDialogActivity].
     */
    private fun getRequestCodeEditReminderDialogActivityPendingIntent(reminderID: Int): Int {
        return OFFSET_REQUEST_CODE_ADD_REMINDER_DIALOG_ACTIVITY_PENDING_INTENT + reminderID
    }

    private var DEFAULT_SOUND: Uri? = null
        get() {
            if (field == null) {
                field = Uri.parse("content://settings/system/notification_sound")
            }
            return field
        }

    /**
     * Describes an action to be performed on a reminder. Provides [PendingIntent]s to perform
     * the different actions at a later time.
     * Used when actions have to be initiated from outside the app (e.g. for scheduled actions
     * or from a notification).
     */
    @Serializable
    sealed class ReminderAction {
        abstract val reminderId: Int

        /**
         * Show the reminder and set its status to [Status.NOTIFIED]. Should be used on due reminders.
         * Schedules the next nag event if the reminder is nagging.
         */
        @Serializable
        class Notify(override val reminderId: Int) : ReminderAction() {
            /**
             * Get a minimal [PendingIntent] suitable to cancel one created with [toPendingIntent]
             * (i.e., a matching one).
             */
            fun getCancelPendingIntent(context: Context): PendingIntent =
                makePendingIntent(context)
        }

        /**
         * Repeat the notification and schedule the next repetition according to the reminder's
         * [Reminder.naggingRepeatInterval].
         */
        @Serializable
        class Nag(override val reminderId: Int) : ReminderAction()

        /**
         * Mark the reminder done (set its status to [Status.DONE] and cancel any current
         * notifications or scheduled actions).
         */
        @Serializable
        class MarkDone(override val reminderId: Int) : ReminderAction()

        fun toJson(): String = Json.encodeToString(this)

        companion object {
            private const val EXTRA_STRING_ACTION =
                "felixwiemuth.simplereminder.ReminderManager.extra.ACTION"

            fun fromJson(serialized: String): ReminderAction = Json.decodeFromString(serialized)
            fun getSerializedReminderActionFromIntent(intent: Intent): String =
                requireNotNull(intent.getStringExtra(EXTRA_STRING_ACTION)) { "Intent does not contain extra $EXTRA_STRING_ACTION" }
        }

        /**
         * Run the action.
         */
        fun run(context: Context) {
            val reminder = ReminderStorage.getReminder(context, reminderId)
            when (this) {
                is Notify -> {
                    showReminder(context, reminder)
                }
                is Nag -> {
                    // Send the same notification again (replaces the previous)
                    sendNotification(context, reminder, displayOriginalDueTime = Prefs.isDisplayOriginalDueTimeNag(context))
                    // Schedule next repetition. This calculates the next occurrence based on the original due date which makes it
                    // unnecessary to save it in the reminder action and in case the execution of this action is delayed more than
                    // one repeat interval, this prevents showing all missed occurrences in a row.
                    scheduleNextNag(context, reminder)
                }
                is MarkDone -> {
                    // Cancel possible further alarms (nagging reminders)
                    cancelReminder(context, reminder.id)
                    reminder.status = Status.DONE
                    updateReminder(context, reminder, false)
                }
            }
        }

        /**
         * Get the request code used for notifications and pending intents for this reminder.
         * It depends on the action and relies on reminder IDs being even.
         */
        private fun getRequestCode(): Int =
            when (this) {
                is Notify, is Nag -> reminderId
                is MarkDone -> reminderId + 1
            }

        protected fun makePendingIntent(context: Context, extras: Bundle? = null): PendingIntent {
            val intent = Intent()
            intent.setClass(context.applicationContext, ReminderBroadcastReceiver::class.java)
            extras?.let { intent.putExtras(it) }
            val flags =
                /* Using a mutable pending intent might be necessary because of scheduling with AlarmManager and the use in notifications
                   (see https://developer.android.com/guide/components/intents-filters#DeclareMutabilityPendingIntent).
                   As we use an explicit intent, this should be fine security-wise.
                 */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                else
                    PendingIntent.FLAG_CANCEL_CURRENT
            return PendingIntent.getBroadcast(
                context,
                getRequestCode(),
                intent,
                flags
            )
        }

        /**
         * Create a pending intent that will start [ReminderBroadcastReceiver] to process this action.
         * Sets correct request code.
         * Uses flag [PendingIntent.FLAG_CANCEL_CURRENT] to make sure no old intent is reused.
         *
         * @param context
         * @return
         */
        fun toPendingIntent(context: Context): PendingIntent {
            val extras = Bundle().apply {
                putString(EXTRA_STRING_ACTION, toJson())
            }
            return makePendingIntent(context, extras)
        }
    }

    /**
     * Process a serialized reminder action stored in the given intent.
     * @See [processReminderAction]
     */
    fun processReminderAction(context: Context, intent: Intent) {
        processReminderAction(context, ReminderAction.getSerializedReminderActionFromIntent(intent))
    }

    /**
     * Process a serialized reminder action.
     */
    fun processReminderAction(context: Context, serializedReminderAction: String) {
        val reminderAction: ReminderAction = ReminderAction.fromJson(serializedReminderAction)
        reminderAction.run(context)
    }

    /**
     * Schedule a reminder to be processed at its due time.
     */
    private fun scheduleReminder(context: Context, reminder: Reminder) {
        val action = ReminderAction.Notify(reminder.id)
        AlarmManagerUtil.scheduleExact(context, reminder.date, action.toPendingIntent(context))
    }

    /**
     * Cancel a reminder, i.e., cancel if scheduled, remove notification if present.
     */
    private fun cancelReminder(context: Context, id: Int) {
        // Cancel possible notification
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(id)

        // Cancel possibly scheduled alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(ReminderAction.Notify(id).getCancelPendingIntent(context))
    }


    /**
     * Show the reminder and update its status. Should be used on due reminders.
     * Schedules the next nag event if the reminder is nagging.
     *
     * @param context
     * @param reminder
     */
    private fun showReminder(context: Context, reminder: Reminder) {
        sendNotification(context, reminder, displayOriginalDueTime = Prefs.isDisplayOriginalDueTimeNormal(context))
        reminder.status = Status.NOTIFIED
        updateReminder(context, reminder, false)
        if (reminder.isNagging) {
            scheduleNextNag(context, reminder)
        }
    }

    private fun scheduleReminderAction(context: Context, date: Date, action: ReminderAction) {
        AlarmManagerUtil.scheduleExact(context, date, action.toPendingIntent(context))
    }

    /**
     * Schedule the next [ReminderAction.Nag] action for a nagging reminder
     * at the next occurrence in the future according to its original schedule.
     */
    private fun scheduleNextNag(context: Context, reminder: Reminder) {
        assert(reminder.isNagging)
        val nextNagTime = calculateNextNagTime(reminder)
        scheduleReminderAction(context, Date(nextNagTime), ReminderAction.Nag(reminder.id))
    }

    /**
     * Calculate the next occurrence of a nagging reminder in the future based on its original due date.
     */
    private fun calculateNextNagTime(reminder: Reminder): Long {
        assert(reminder.isNagging)
        val d = reminder.naggingRepeatIntervalInMillis
        val now = System.currentTimeMillis()
        val sinceDue = now - reminder.date.time
        val sinceLastNag = sinceDue % d
        val untilNextNag = d - sinceLastNag
        val nextNag = now + untilNextNag
        return nextNag
    }

    /**
     * Send a notification with swipe and click actions related to the reminder.
     *
     * @param context
     * @param reminder
     * @param silent whether the notification should be shown silently without any alert
     */
    private fun sendNotification(context: Context, reminder: Reminder, displayOriginalDueTime: Boolean = false, silent: Boolean = false) {
        val markDoneAction = ReminderAction.MarkDone(reminder.id)
        val markDoneIntent = markDoneAction.toPendingIntent(context)
        val editReminderIntent = EditReminderDialogActivity.getIntentEditReminder(context, reminder.id)
        val editReminderPendingIntent = PendingIntent.getActivity(
            context,
            getRequestCodeEditReminderDialogActivityPendingIntent(reminder.id),
            editReminderIntent,
            /* Using a mutable pending intent might be necessary because of scheduling with AlarmManager and the use in notifications
               (see https://developer.android.com/guide/components/intents-filters#DeclareMutabilityPendingIntent).
               As we use an explicit intent, this should be fine security-wise.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )
        val builder = NotificationCompat.Builder(
            context,
            NOTIFICATION_CHANNEL_REMINDER
        ).also {
            if (displayOriginalDueTime)
                it.setWhen(reminder.date.time).setShowWhen(true)
        }
            .setSilent(silent)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setContentIntent(editReminderPendingIntent)
            .setDeleteIntent(markDoneIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Applies for Android < 8
            .setPriority(
                Integer.valueOf(
                    Prefs.getStringPref(
                        R.string.prefkey_priority,
                        "0",
                        context
                    )
                )
            )

        // Applies for Android < 8
        if (Prefs.getBooleanPref(R.string.prefkey_enable_sound, false, context)) {
            builder.setSound(DEFAULT_SOUND) // Set default notification sound
        }
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(reminder.id, builder.build())
    }

    /**
     * Add the reminder described by the given builder and schedule it.
     * A new ID is assigned by the store.
     *
     * @param context
     * @param reminderBuilder
     * @return the resulting reminder
     */
    @JvmStatic
    fun addReminder(context: Context, reminderBuilder: Reminder.Builder): Reminder {
        val reminder = ReminderStorage.addReminder(context, reminderBuilder)
        scheduleReminder(context, reminder)
        return reminder
    }

    /**
     * Add the given reminder (with the given ID) and schedule it.
     *
     * @param context
     * @param reminder
     */
    private fun addReminder(context: Context, reminder: Reminder) {
        ReminderStorage.addReminder(context, reminder)
        scheduleReminder(context, reminder)
    }

    /**
     * Replaces the reminder which has the ID of the given reminder with the given reminder.
     * If no reminder with that ID exists, the reminder will be created.
     *
     * @param context
     * @param reminder
     * @param reschedule if true, checks whether the reminder should be rescheduled: If the given reminder's status is not [Status.SCHEDULED] or its time is not in the future, a possible scheduled notification is cancelled. If the status is [Status.SCHEDULED] and its time is in the future, a notification is scheduled.
     */
    @JvmStatic
    fun updateReminder(context: Context, reminder: Reminder, reschedule: Boolean) {
        ReminderStorage.updateReminder(context, reminder)
        if (reschedule) {
            rescheduleReminder(context, reminder)
        }
    }

    /**
     * For each given reminder, replaces the existing reminder which has the ID of the given reminder with the given reminder.
     *
     * @param context
     * @param reminders
     * @param reschedule if true, checks whether the reminders should be rescheduled: If the given reminder's status is not [Status.SCHEDULED] or its time is not in the future, a possible scheduled notification is cancelled. If the status is [Status.SCHEDULED] and its time is in the future, a notification is scheduled.
     */
    fun updateReminders(context: Context, reminders: Iterable<Reminder>, reschedule: Boolean) {
        ReminderStorage.updateReminders(context, reminders)
        if (reschedule) {
            reminders.forEach { rescheduleReminder(context, it) }
        }
    }

    /**
     * Update the reminders with the given IDs with the given transformation.
     *
     * @param context
     * @param transformation
     * @param ids
     * @param reschedule if true, checks whether the reminders should be rescheduled: If the given reminder's status is not [Status.SCHEDULED] or its time is not in the future, a possible scheduled notification is cancelled. If the status is [Status.SCHEDULED] and its time is in the future, a notification is scheduled.
     */
    fun updateReminders(
        context: Context,
        transformation: (Reminder) -> Unit,
        ids: Set<Int>,
        reschedule: Boolean
    ) {
        val updated = ReminderStorage.updateReminders(context, transformation, ids)
        if (reschedule) {
            updated.forEach { rescheduleReminder(context, it) }
        }
    }

    /**
     * Cancel potential existing scheduling and notification for the given reminder and reschedule it if its status is [Status.SCHEDULED] and its time is in the future.
     *
     * @param context
     * @param reminder
     */
    private fun rescheduleReminder(context: Context, reminder: Reminder) {
        cancelReminder(context, reminder.id)
        val isFuture = reminder.date.time > System.currentTimeMillis()
        if (reminder.status === Status.SCHEDULED && isFuture) {
            scheduleReminder(context, reminder)
        }
    }

    /**
     * Schedule all future reminders and show all due, but not yet notified, reminders.
     * Schedule also the next nag for due nagging reminders.
     * If some of the reminders are already scheduled, the new registration should replace the previous.
     * Due reminders are re-shown silently.
     *
     * @param context
     */
    @JvmStatic
    fun scheduleAndReshowAllReminders(context: Context) {
        Log.d("SchedulingShowing", "Rescheduling all alarms and reshowing all notifications")
        val currentTime = System.currentTimeMillis()
        for (r in ReminderStorage.getReminders(context)) {
            when (r.status) {
                Status.SCHEDULED -> if (r.date.time <= currentTime) showReminder(context, r) else scheduleReminder(context, r)
                Status.NOTIFIED -> {
                    sendNotification(context, r, silent = true, displayOriginalDueTime = Prefs.isDisplayOriginalDueTimeRecreate(context))
                    if (r.isNagging) scheduleNextNag(context, r)
                }
                Status.DONE -> {}
            }
        }
    }

    /**
     * Remove the reminders with the given IDs from the current reminders. Cancels pending notifications.
     *
     * @param context
     * @param ids
     */
    fun removeReminders(context: Context, ids: Set<Int>) {
        ReminderStorage.removeReminders(context, ids)
        for (id in ids) {
            cancelReminder(context, id)
        }
    }

    @JvmStatic
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = context.getString(R.string.channel_name)
            val description = context.getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_REMINDER, name, importance)
            channel.description = description
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
