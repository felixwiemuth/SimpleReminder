/*
 * Copyright (C) 2018-2021 Felix Wiemuth and contributors (see CONTRIBUTORS.md)
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

package felixwiemuth.simplereminder.ui;

import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;

import felixwiemuth.simplereminder.R;
import felixwiemuth.simplereminder.data.Reminder;

/**
 * Base class for the reminder dialog activity which is used to add and edit reminders.
 */
public abstract class ReminderDialogActivity extends AppCompatActivity {
    protected AutoCompleteTextView nameTextView;
    private Button addButton;
    private TimePicker timePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder_dialog);

        nameTextView = findViewById(R.id.nameTextView);
        addButton = findViewById(R.id.addButton);
        timePicker = findViewById(R.id.timePicker);

        nameTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameTextView.setImeActionLabel(getString(R.string.keyboard_action_add_reminder), EditorInfo.IME_ACTION_DONE);
        nameTextView.requestFocus();
        nameTextView.setRawInputType(InputType.TYPE_CLASS_TEXT);
        nameTextView.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onDone();
                return true;
            }
            return false;
        });

        timePicker.setIs24HourView(true);

        addButton.setOnClickListener(v -> onDone());
    }


    protected Reminder.ReminderBuilder buildReminderWithTimeAndText() {
        int hour;
        int minute;

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
        }

        return Reminder.builder()
                .date(time.getTime())
                .text(nameTextView.getText().toString());

    }

    /**
     * Action to be executed on hitting the main "Add" or "OK" button.
     */
    abstract protected void onDone();

    protected void makeToast(Calendar date) {
        // Create relative description of due date
        String relativeDueDate = DateUtils.getRelativeTimeSpanString(date.getTimeInMillis(), System.currentTimeMillis(), 0).toString();
        // Convert first letter to lower case to use it in a sentence
        if (relativeDueDate.length() > 0) {
            relativeDueDate = relativeDueDate.substring(0, 1).toLowerCase() + relativeDueDate.substring(1);
        }

        // Create toast
        String toastText = getString(R.string.toast_reminder_due, relativeDueDate);
        int duration = Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(this, toastText, duration);
        toast.show();
    }

    /**
     * Finish the activity with, removing the task on Lollipop and above.
     */
    protected void completeActivity() {
        setResult(RESULT_OK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask(); // Adding the reminder completes this task, the dialog should not stay under recent tasks.
        } else {
            finish(); // This will leave the task under recent tasks, but it seems that one needs a workaround to prevent this: https://stackoverflow.com/questions/22166282/close-application-and-remove-from-recent-apps
        }
    }
}
