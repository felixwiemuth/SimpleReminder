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

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import felixwiemuth.simplereminder.data.Reminder;
import felixwiemuth.simplereminder.util.EnumUtil;

/**
 * Handles scheduled reminders when they are due. May only be started with an intent containing an {@link #EXTRA_INT_ID} extra with a valid reminder ID and an {@link Action} value add with {@link EnumUtil#serialize(Enum)}.
 *
 * @author Felix Wiemuth
 */
public class ReminderService extends IntentService {
    public static final String CHANNEL_REMINDER = "Reminder";
    public static final String EXTRA_INT_ID = "felixwiemuth.simplereminder.ReminderService.extra.ID";
//    public static final String EXTRA_ACTION = "felixwiemuth.simplereminder.ReminderService.extra.ACTION";

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
        int id = intent.getExtras().getInt(EXTRA_INT_ID);
        Action action = EnumUtil.deserialize(Action.class).from(intent);
        Reminder reminder = ReminderManager.getReminder(this, id);
        action.run(this, reminder);
    }

    private static void sendNotification(Context context, int id, String text) {
        Intent markDoneIntent = new Intent(context, ReminderService.class);
        EnumUtil.serialize(Action.MARK_DONE).to(markDoneIntent);
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

    public static void cancelPendingNotification(Context context, int id) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(id);
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
