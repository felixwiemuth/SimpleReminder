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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import felixwiemuth.simplereminder.R;
import felixwiemuth.simplereminder.ReminderManager;
import felixwiemuth.simplereminder.data.Reminder;

/**
 * Shows a dialog allowing to edit a reminder. Finishes with {@link #RESULT_OK} if the reminder has been edited.
 * <p>
 * Has to be started with the intent provided by {@link #getIntentEditReminder(Context, int)}.
 */
public class EditReminderDialogActivity extends ReminderDialogActivity {

    private static final String EXTRA_REMINDER_ID = "felixwiemuth.simplereminder.ui.AddReminderDialogActivity.extra.ID";

    /**
     * The ID of the reminder to be updated.
     */
    private int reminderToUpdate = -1;

    public static Intent getIntentEditReminder(Context context, int reminderId) {
        return new Intent(context, EditReminderDialogActivity.class)
                .putExtra(EXTRA_REMINDER_ID, reminderId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.edit_reminder_title);
        setupActivityWithReminder(getIntent());
    }

    /**
     * Process a new intent to this activity, replacing the current content with that of the reminder referenced by the new intent.
     *
     * @param intent
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setupActivityWithReminder(intent);
    }

    /**
     * Setup the state and UI of the activity based on the reminder referenced by the given intent.
     *
     * @param intent
     * @throws IllegalArgumentException if the intent has not the required extra
     */
    private void setupActivityWithReminder(Intent intent) {
        if (!intent.hasExtra(EXTRA_REMINDER_ID)) {
            throw new IllegalArgumentException("EditReminderDialogActivity received intent without reminder ID extra.");
        }
        final int reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1);
        try {
            Reminder reminder = ReminderManager.getReminder(this, reminderId);
            nameTextView.setText(reminder.getText());
            // Move cursor to end of text
            nameTextView.setSelection(nameTextView.length());
            // For scheduled reminders, set the selected time to the due date, otherwise leave it at the current time
            if (reminder.getStatus() == Reminder.Status.SCHEDULED) {
                setSelectedDateTimeAndSelectionMode(reminder.getCalendar());
            }
//            naggingSwitch.setChecked(reminder.isNagging());
            if (reminder.isNagging()) {
                naggingRepeatInterval = reminder.getNaggingRepeatInterval();
            }
            reminderToUpdate = reminderId;
        } catch (ReminderManager.ReminderNotFoundException e) {
            Log.w("AddReminder", "Intent contains invalid reminder ID.");
            Toast.makeText(this, R.string.error_msg_reminder_not_found, Toast.LENGTH_LONG).show();
            completeActivity();
        }
    }

    @Override
    protected void onDone() {
        Reminder.ReminderBuilder reminderBuilder = buildReminderWithTimeTextNagging();
        reminderBuilder.id(reminderToUpdate);
        Reminder reminder = reminderBuilder.build();
        ReminderManager.updateReminder(this, reminder, true);
        makeToast(reminder);
        completeActivity();
    }
}
