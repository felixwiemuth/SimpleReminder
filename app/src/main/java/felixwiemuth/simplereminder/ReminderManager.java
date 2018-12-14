package felixwiemuth.simplereminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import static android.content.Context.ALARM_SERVICE;

/**
 * Manages reminders by allowing to add and change reminders.
 *
 * @author Felix Wiemuth
 */
public class ReminderManager {
    public static void addReminder(Context context, long time, String text) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        Intent processIntent = new Intent(context, ReminderService.class);
        PendingIntent alarmIntent = PendingIntent.getService(context, 0, processIntent, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, alarmIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, time, alarmIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, time, alarmIntent);
        }

    }
}
