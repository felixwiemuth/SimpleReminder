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
import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

/**
 * Handles scheduled reminders when they are due.
 *
 * @author Felix Wiemuth
 */
public class ReminderService extends IntentService {
    public static final String CHANNEL_REMINDER = "Reminder";
    public static final String EXTRA_INT_ID = "felixwiemuth.simplereminder.ReminderService.ID";
    public static final String EXTRA_STRING_REMINDER_TEXT = "felixwiemuth.simplereminder.ReminderService.REMINDER_TEXT";

    public ReminderService() {
        super("SimpleReminder Reminder Service");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        int id =  intent.getExtras().getInt(EXTRA_INT_ID);
        if (intent == null) {
            return;
        }
        String text = intent.getExtras().getString(EXTRA_STRING_REMINDER_TEXT);
        if (text == null) {
            text = "Reminder!";
        }
        sendNotification(id, text);
    }

    @SuppressLint("ApplySharedPref")
    private void sendNotification(int id, String text) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_REMINDER)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(id, builder.build()); //TODO save ID to list of currently scheduled reminders

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
