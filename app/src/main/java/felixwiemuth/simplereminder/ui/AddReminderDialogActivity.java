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

import android.os.Bundle;

import felixwiemuth.simplereminder.Prefs;
import felixwiemuth.simplereminder.R;
import felixwiemuth.simplereminder.ReminderManager;
import felixwiemuth.simplereminder.data.Reminder;

/**
 * Shows a dialog allowing to add a reminder. Finishes with {@link #RESULT_OK} if the reminder has been added.
 */
public class AddReminderDialogActivity extends ReminderDialogActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.add_reminder_title);
    }

    @Override
    protected void onDone() {
        Reminder reminder = ReminderManager.addReminder(this, buildReminderWithTimeAndText());
        makeToast(reminder.getCalendar());
        completeActivity();
        Prefs.setAddReminderDialogUsed(this);
    }
}
