/*
 * Copyright (C) 2019 Felix Wiemuth
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

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;

import felixwiemuth.simplereminder.Prefs;
import felixwiemuth.simplereminder.R;
import felixwiemuth.simplereminder.ReminderManager;
import felixwiemuth.simplereminder.data.Reminder;

/**
 * Shows a dialog allowing to add a reminder. Finishes with {@link #RESULT_OK} if the reminder has been added.
 * Can be started in "edit reminder" mode by providing extra {@link AddReminderDialogActivity#EXTRA_REMINDER_ID}. In that case, the text is set to that of the reminder of the given ID, and that reminder will be replaced rather than a new one added. In addition the dialog shows a different title.
 */
public class AddReminderDialogActivity extends AppCompatActivity {
    /**
     * If the activity is started with an intent containing this int extra, the text will be set to that of the reminder with the given ID.
     */
    public static final String EXTRA_REMINDER_ID = "felixwiemuth.simplereminder.ui.AddReminderDialogActivity.extra.ID";

    /**
     * The ID of the reminder to be updated. "-1" means that a new reminder should be created.
     */
    private int reminderToUpdate = -1;

    public static Intent getIntentEditReminder(Context context, int reminderId) {
        return new Intent(context, AddReminderDialogActivity.class)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_reminder_dialog);

        final AutoCompleteTextView nameTextView = findViewById(R.id.nameTextView);
        final Button addButton = findViewById(R.id.addButton);
        final TimePicker timePicker = findViewById(R.id.timePicker);

        nameTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameTextView.setImeActionLabel(getString(R.string.keyboard_action_add_reminder), EditorInfo.IME_ACTION_DONE);
        nameTextView.requestFocus();
        nameTextView.setRawInputType(InputType.TYPE_CLASS_TEXT);
        nameTextView.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addButton.callOnClick();
                return true;
            }
            return false;
        });

        timePicker.setIs24HourView(true);

        setupActivityWithPotentialReminder(getIntent());

        addButton.setOnClickListener(v -> {
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

            Reminder.ReminderBuilder reminderBuilder = Reminder.builder()
                    .date(time.getTime())
                    .text(nameTextView.getText().toString());
            if (reminderToUpdate == -1) { // A new reminder should be created
                ReminderManager.addReminder(AddReminderDialogActivity.this, reminderBuilder);
            } else { // A reminder should be replaced
                reminderBuilder.id(reminderToUpdate);
                ReminderManager.updateReminder(AddReminderDialogActivity.this, reminderBuilder.build(), true);
                reminderToUpdate = -1;
            }

            // Create relative description of due date
            String relativeDueDate = DateUtils.getRelativeTimeSpanString(time.getTimeInMillis(), System.currentTimeMillis(), 0).toString();
            // Convert first letter to lower case to use it in a sentence
            if (relativeDueDate.length() > 0) {
                relativeDueDate = relativeDueDate.substring(0, 1).toLowerCase() + relativeDueDate.substring(1);
            }

            // Create toast
            String toastText = getString(R.string.toast_reminder_due, relativeDueDate);
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(AddReminderDialogActivity.this, toastText, duration);
            toast.show();

            setResult(RESULT_OK);
            finish(); //TODO change animation
        });

        Prefs.setAddReminderDialogUsed(this);
    }

    /**
     * Setup or reset the activity depending on whether a reminder should be edited or created (see {@link AddReminderDialogActivity#EXTRA_REMINDER_ID}).
     * Sets {@link #reminderToUpdate}, content of the text view, as well as the activity's title.
     *
     * @param intent
     */
    private void setupActivityWithPotentialReminder(Intent intent) {
        final int reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1);
        final AutoCompleteTextView nameTextView = findViewById(R.id.nameTextView);
        if (reminderId != -1) {
            try {
                Reminder reminder = ReminderManager.getReminder(this, reminderId);
                nameTextView.setText(reminder.getText());
                // Move cursor to end of text
                nameTextView.setSelection(nameTextView.length());
                setTitle(R.string.edit_reminder_title);
                reminderToUpdate = reminderId;
            } catch (ReminderManager.ReminderNotFoundException e) {
                Log.w("AddReminder", "Intent contains invalid reminder ID.");
                reminderToUpdate = -1;
            }
        } else {
            setTitle(R.string.add_reminder_title);
            nameTextView.setText("");
            reminderToUpdate = -1;
        }
    }

    /**
     * Process a new intent to this activity, updating its appereance and content depending on whether a reminder should be edited or created (see {@link AddReminderDialogActivity#EXTRA_REMINDER_ID}).
     *
     * @param intent
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setupActivityWithPotentialReminder(intent);
    }
}
