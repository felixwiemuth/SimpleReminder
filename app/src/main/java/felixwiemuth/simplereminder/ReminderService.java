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

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.util.Log;
import felixwiemuth.simplereminder.data.Reminder;
import felixwiemuth.simplereminder.util.DateTimeUtil;
import felixwiemuth.simplereminder.util.EnumUtil;
import lombok.Builder;

/**
 * Responsible for reminder scheduling and notifications. Handles scheduled reminders when they are due. May only be started with an intent created via the provided intent builder ({@link #intentBuilder()}).
 *
 * @author Felix Wiemuth
 */
public class ReminderService extends IntentService {
    public static final String CHANNEL_REMINDER = "Reminder";
    public static final String EXTRA_INT_ID = "felixwiemuth.simplereminder.ReminderService.extra.ID";

    /**
     * Specifies the arguments to call this service.
     */
    @Builder
    public static class Arguments {
        @Builder.Default
        private int id = -1;
        private Action action;

        public static class ArgumentsBuilder {

            public static class IncompleteArgumentsException extends RuntimeException {
                public IncompleteArgumentsException(String message) {
                    super(message);
                }
            }

            /**
             * Create the intent. May only be called after all fields have been set.
             *
             * @param context
             * @return
             * @throws IncompleteArgumentsException if not all fields have been set
             */
            public Intent build(Context context) throws IncompleteArgumentsException {
                Intent intent = new Intent(context, ReminderService.class);

                if (id < 0) {
                    throw new IncompleteArgumentsException("Id not specified or not valid (must be >=0).");
                }
                if (action == null) {
                    throw new IncompleteArgumentsException("Action not specified.");
                }

                intent.putExtra(ReminderService.EXTRA_INT_ID, id);
                EnumUtil.serialize(action).to(intent);

                return intent;
            }
        }
    }

    public static Arguments.ArgumentsBuilder intentBuilder() {
        return Arguments.builder();
    }

    /**
     * Get an intent to be used to cancel a pending intent created with {@link #intentBuilder()}.
     *
     * @param context
     * @return
     */
    public static Intent getCancelIntent(Context context) {
        return new Intent(context, ReminderService.class);
    }

    public ReminderService() {
        super("SimpleReminder Reminder Service");
    }

    interface ReminderAction {
        void run(Context context, Reminder reminder);
    }

    enum Action {
        NOTIFY(
                (context, reminder) -> {
                    sendNotification(context, reminder.getId(), reminder.getText());
                    reminder.setStatus(Reminder.Status.NOTIFIED);
                    ReminderManager.updateReminder(context, reminder, false);
                }
        ),
        MARK_DONE(
                (context, reminder) -> {
                    reminder.setStatus(Reminder.Status.DONE);
                    ReminderManager.updateReminder(context, reminder, false);
                }
        );

        private ReminderAction reminderAction;

        Action(ReminderAction reminderAction) {
            this.reminderAction = reminderAction;
        }

        void run(Context context, Reminder reminder) {
            reminderAction.run(context, reminder);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) {
            Log.w("ReminderService", "Service called with no intent.");
            return;
        }
        int id = intent.getExtras().getInt(EXTRA_INT_ID, -1);
        Action action = EnumUtil.deserialize(Action.class).from(intent);
        Reminder reminder = ReminderManager.getReminder(this, id);
        action.run(this, reminder);
    }

    private static void sendNotification(Context context, int id, String text) {
        Intent markDoneIntent = intentBuilder()
                .id(id)
                .action(Action.MARK_DONE)
                .build(context);

        PendingIntent deleteIntent = PendingIntent.getService(context, (int) System.nanoTime(), markDoneIntent, 0); // using lower bits of nano-time as request code to approximate uniqueness

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDER)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(text)
                .setDeleteIntent(deleteIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(id, builder.build());
    }

    /**
     * Schedules a reminder if its time is not in the past.
     *
     * @param context
     * @param reminder
     */
    public static void scheduleReminder(Context context, Reminder reminder) {
        // Prepare pending intent
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        Intent notifyIntent = ReminderService.intentBuilder()
                .id(reminder.getId())
                .action(ReminderService.Action.NOTIFY)
                .build(context);
        PendingIntent alarmIntent = PendingIntent.getService(context, (int) System.nanoTime(), notifyIntent, 0); // using lower bits of nano-time as request code to approximate uniqueness

        // Schedule alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.getDate().getTime(), alarmIntent);
            Log.d("ReminderManager", "Set alarm (\"exact and allow while idle\") for " + DateTimeUtil.formatDateTime(reminder.getDate()));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminder.getDate().getTime(), alarmIntent);
            Log.d("ReminderManager", "Set alarm (\"exact\") for " + DateTimeUtil.formatDateTime(reminder.getDate()));
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.getDate().getTime(), alarmIntent);
            Log.d("ReminderManager", "Set alarm for " + DateTimeUtil.formatDateTime(reminder.getDate()));
        }
    }

    /**
     * Cancel a pending or already due reminder (remove the notification).
     *
     * @param context
     * @param id
     */
    public static void cancelReminder(Context context, int id) {
        // Cancel possible notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(id);

        // Cancel possibly scheduled alarm
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        Intent cancelIntent = getCancelIntent(context);
        PendingIntent cancelPendingIntent = PendingIntent.getService(context, (int) System.nanoTime(), cancelIntent, 0); // must use equal intent as when scheduled (request code can be different); using lower bits of nano-time as request code to approximate uniqueness
        alarmManager.cancel(cancelPendingIntent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_REMINDER, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
