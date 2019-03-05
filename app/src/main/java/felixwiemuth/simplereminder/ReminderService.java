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
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import felixwiemuth.simplereminder.data.Reminder;
import felixwiemuth.simplereminder.ui.RemindersListActivity;
import felixwiemuth.simplereminder.util.DateTimeUtil;
import felixwiemuth.simplereminder.util.EnumUtil;
import felixwiemuth.simplereminder.util.ImplementationError;
import lombok.Builder;

/**
 * Responsible for reminder scheduling and notifications. Handles scheduled reminders when they are due. May only be started with an intent created via the provided intent builder ({@link #intentBuilder()}).
 *
 * @author Felix Wiemuth
 */
public class ReminderService extends IntentService {
    public static final String CHANNEL_REMINDER = "Reminder";
    public static final String EXTRA_INT_ID = "felixwiemuth.simplereminder.ReminderService.extra.ID";

    private static Uri defaultSound;

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

            /**
             * Create a pending intent that will start the service. Sets correct request code.
             *
             * @param context
             * @return
             */
            public PendingIntent buildPendingIntent(Context context) {
                // NOTE: This relies on reminder IDs always being even integers.
                int requestCode;
                switch (action) {
                    case NOTIFY:
                        requestCode = id;
                        break;
                    case MARK_DONE:
                        requestCode = id + 1;
                        break;
                    default:
                        throw new ImplementationError("Unkown action.");
                }
                return PendingIntent.getService(context, requestCode, build(context), 0);
            }
        }
    }

    public static Arguments.ArgumentsBuilder intentBuilder() {
        return Arguments.builder();
    }

    /**
     * Get a pending intent to be used to cancel a pending {@link Action#NOTIFY} intent created with {@link #intentBuilder()}.
     *
     * @param context
     * @param id      the ID of the reminder to be cancelled
     * @return
     */
    public static PendingIntent getCancelNotifyIntent(Context context, int id) {
        return PendingIntent.getService(context, id, new Intent(context, ReminderService.class), 0); // must use equal intent and same request code as when scheduled
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
        PendingIntent markDoneIntent = intentBuilder()
                .id(id)
                .action(Action.MARK_DONE)
                .buildPendingIntent(context);

        Intent intent = new Intent(context, RemindersListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openRemindersListIntent = PendingIntent.getActivity(context, 0, intent, 0); //TODO will this request code interfere with ID=0 reminder? (should not, as different kinds of pending intent)

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDER)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openRemindersListIntent)
                .setDeleteIntent(markDoneIntent)
                .setPriority(Integer.valueOf(Prefs.getStringPref(R.string.prefkey_priority, "0", context)));

        if (Prefs.getBooleanPref(R.string.prefkey_enable_sound, false, context)) {
            builder.setSound(getDefaultSound()); // Set default notification sound
        }

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
        PendingIntent notifyIntent = intentBuilder()
                .id(reminder.getId())
                .action(Action.NOTIFY)
                .buildPendingIntent(context);

        // Schedule alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.getDate().getTime(), notifyIntent);
            Log.d("ReminderManager", "Set alarm (\"exact and allow while idle\") for " + DateTimeUtil.formatDateTime(reminder.getDate()));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminder.getDate().getTime(), notifyIntent);
            Log.d("ReminderManager", "Set alarm (\"exact\") for " + DateTimeUtil.formatDateTime(reminder.getDate()));
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.getDate().getTime(), notifyIntent);
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
        alarmManager.cancel(getCancelNotifyIntent(context, id));
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

    private static Uri getDefaultSound() {
        if (defaultSound == null) {
            defaultSound = Uri.parse("content://settings/system/notification_sound");
        }
        return defaultSound;
    }
}
