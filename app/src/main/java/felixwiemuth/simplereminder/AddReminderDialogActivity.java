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

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TimePicker;
import android.widget.Toast;
import felixwiemuth.simplereminder.data.Reminder;

import java.text.DateFormat;
import java.util.Calendar;

/**
 * Shows a dialog allowing to add a reminder. Finishes with {@link #RESULT_OK} if the reminder has been added.
 */
public class AddReminderDialogActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_reminder_dialog);

        final AutoCompleteTextView nameTextView = findViewById(R.id.nameTextView);
        nameTextView.requestFocus();

        final TimePicker timePicker = findViewById(R.id.timePicker);
        timePicker.setIs24HourView(true);

        Button addButton = findViewById(R.id.addButton);
        addButton.setOnClickListener(v -> {
            int hour;
            int minute;
            boolean tomorrow = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hour = timePicker.getHour();
                minute = timePicker.getMinute();
            } else {
                hour = timePicker.getCurrentHour();
                minute = timePicker.getCurrentMinute();
            }

            // We use the current day when clicking "Add" as reference
            Calendar time = Calendar.getInstance();
            time.set(Calendar.HOUR_OF_DAY, hour);
            time.set(Calendar.MINUTE, minute);
            time.set(Calendar.SECOND, 0);
            // We leave milliseconds as-is, as a little randomness in time is probably good

            // If the resulting date is in the past, assume that the next day is meant
            if (time.compareTo(Calendar.getInstance()) <= 0) { // AlarmManager also seems to fire (directly) when date is in the past, so it is no problem when in the mean time (from this check to scheduling alarm), the date moves to the past
                time.add(Calendar.DAY_OF_MONTH, 1);  // wraps over end of month
                tomorrow = true;
            }

            Reminder.ReminderBuilder reminderBuilder = Reminder.builder()
                    .date(time.getTime())
                    .text(nameTextView.getText().toString());
            ReminderManager.addReminder(AddReminderDialogActivity.this, reminderBuilder)
            ;
            String toastText = getString(R.string.toast_reminder_added_for);
            if (tomorrow) {
                toastText += getString(R.string.toast_reminder_added_tomorrow);
            }
            toastText += DateFormat.getTimeInstance(DateFormat.SHORT).format(time.getTime());
            int duration = Toast.LENGTH_LONG;

            Toast toast = Toast.makeText(AddReminderDialogActivity.this, toastText, duration);
            toast.show();

            setResult(RESULT_OK);
            finish(); //TODO change animation
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Intent content is not used
    }
}
