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

import android.app.DatePickerDialog;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.Calendar;

import felixwiemuth.simplereminder.Prefs;
import felixwiemuth.simplereminder.R;
import felixwiemuth.simplereminder.data.Reminder;
import felixwiemuth.simplereminder.util.DateTimeUtil;

/**
 * Base class for the reminder dialog activity which is used to add and edit reminders.
 */
public abstract class ReminderDialogActivity extends AppCompatActivity {
    protected AutoCompleteTextView nameTextView;
    protected SwitchCompat naggingSwitch;
    private Button addButton;
    private Button dateMinusButton;
    private Button datePlusButton;
    private TextView dateDisplay;
    private TimePicker timePicker;

    private DateSelectionMode dateSelectionMode;
    private Calendar selectedDate;

    protected int naggingRepeatInterval;

    private enum DateSelectionMode {
        /**
         * The date is derived from the chosen time, so that this time lies within the next 24 hours.
         */
        NEXT24,
        /**
         * The date is selected manually.
         */
        MANUAL
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder_dialog);

        nameTextView = findViewById(R.id.nameTextView);
        addButton = findViewById(R.id.addButton);
        dateMinusButton = findViewById(R.id.dateMinusButton);
        dateMinusButton.setOnClickListener(v -> decrementDateAction());
        datePlusButton = findViewById(R.id.datePlusButton);
        datePlusButton.setOnClickListener(v -> incrementDateAction());

        dateDisplay = findViewById(R.id.dateDisplay);
        dateDisplay.setOnClickListener(v -> new DatePickerDialog(
                        ReminderDialogActivity.this,
                        (view, year, month, dayOfMonth) -> {
                            Calendar newSelectedDate = Calendar.getInstance();
                            newSelectedDate.set(Calendar.YEAR, year);
                            newSelectedDate.set(Calendar.MONTH, month);
                            newSelectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                            newSelectedDate.set(Calendar.HOUR_OF_DAY, selectedDate.get(Calendar.HOUR_OF_DAY));
                            newSelectedDate.set(Calendar.MINUTE, selectedDate.get(Calendar.MINUTE));
                            // Validate new selected date
                            if (newSelectedDate.before(Calendar.getInstance())) {
                                Toast.makeText(this, R.string.add_reminder_toast_invalid_date, Toast.LENGTH_LONG).show();
                            } else {
                                setSelectedDateTimeAndSelectionMode(newSelectedDate);
                            }
                        },
                        selectedDate.get(Calendar.YEAR),
                        selectedDate.get(Calendar.MONTH),
                        selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show());

        naggingSwitch = findViewById(R.id.naggingSwitch);
        naggingSwitch.setOnClickListener(v -> {
            if (naggingSwitch.isChecked()) {
                showToastNaggingRepeatInterval();
            }
        });
        naggingSwitch.setOnLongClickListener(view -> {
            showChooseNaggingRepeatIntervalDialog();
            return true;
        });
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

        selectedDate = Calendar.getInstance(); // Initialize calendar variable
        setSelectedDateTimeAndSelectionMode(Calendar.getInstance());

        timePicker.setIs24HourView(true);
        timePicker.setOnTimeChangedListener((view, hourOfDay, minute) -> {
            if (dateSelectionMode == DateSelectionMode.NEXT24) {
                selectedDate = getTimeWithinNext24Hours(hourOfDay, minute);
            } else {
                selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                selectedDate.set(Calendar.MINUTE, minute);
            }
            renderSelectedDate();
        });

        addButton.setOnClickListener(v -> onDone());

        naggingRepeatInterval = Prefs.getNaggingRepeatInterval(this);

        renderSelectedDate();
    }

    /**
     * Given an hour and minute, returns a date representing the next occurrence of this time within the next 24 hours. The seconds are set to 0, milliseconds become the value within the current second.
     */
    private Calendar getTimeWithinNext24Hours(int hourOfDay, int minute) {
        Calendar date = Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY, hourOfDay);
        date.set(Calendar.MINUTE, minute);
        date.set(Calendar.SECOND, 0);
        // If the resulting date is in the past, the next day is meant
        if (date.before(Calendar.getInstance())) {
            date.add(Calendar.DAY_OF_MONTH, 1);
        }
        return date;
    }

    /**
     * Set the selected and displayed date/time to that of the given calendar (seconds are set to 0).
     * Also sets the {@link #dateSelectionMode} based on whether the selected time lies within the next 24 hours.
     * @param calendar
     */
    protected void setSelectedDateTimeAndSelectionMode(Calendar calendar) {
        selectedDate.setTime(calendar.getTime());
        selectedDate.set(Calendar.SECOND, 0); // We leave milliseconds as-is, as a little randomness in time is probably good

        // Determine date selection mode based on the given date.
        // Check whether the selected time is within the next 24 hours (i.e., decrementing it by one day would move it to the past).
        decrementDate();
        if (selectedDate.before(Calendar.getInstance())) {
            dateSelectionMode = DateSelectionMode.NEXT24;
        } else {
            dateSelectionMode = DateSelectionMode.MANUAL;
        }
        incrementDate();
        renderSelectedDate();

        // Set the clock widget to the time of the selected date.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.setHour(calendar.get(Calendar.HOUR_OF_DAY));
            timePicker.setMinute(calendar.get(Calendar.MINUTE));
        } else {
            timePicker.setCurrentHour(calendar.get(Calendar.HOUR_OF_DAY));
            timePicker.setCurrentMinute(calendar.get(Calendar.MINUTE));
        }
    }

    /**
     * Get the number of day changes between the current date and the selected date.
     * If this number is greater than {@link Integer#MAX_VALUE}, the return value is undefined.
     * @return
     */
    private int getDiffSelectedDate() {
        return (int) DateTimeUtil.dayChangesBetween(Calendar.getInstance(), selectedDate);
    }

    /**
     * If in NEXT24 mode, switches to MANUAL mode and adds a day if not
     * already at +1; in MANUAL mode just adds a day.
     */
    private void incrementDateAction() {
        if (dateSelectionMode == DateSelectionMode.NEXT24) {
            dateSelectionMode = DateSelectionMode.MANUAL;
            if (getDiffSelectedDate() == 0) {
                incrementDate();
            }
        } else {
            incrementDate();
        }
        renderSelectedDate();
    }

    /**
     * In MANUAL mode, decrement the date if it is not within the next 24 hours. If the resulting date is
     * within the next 24 hours, switch to NEXT24 mode.
     * In NEXT24 mode this does not apply and is ignored.
     */
    private void decrementDateAction() {
        if (dateSelectionMode == DateSelectionMode.NEXT24) {
            return;
        }
        decrementDate();
        // Check whether we already were within the next 24 hours (or more precisely, whether decrementing moved the date to the past).
        // In that case, by definition of NEXT24 mode, the next day is meant, so we have to increment the date again.
        if (selectedDate.before(Calendar.getInstance())) {
            incrementDate();
            dateSelectionMode = DateSelectionMode.NEXT24;
        } else {
            // Otherwise check whether decrementing moved the date to within the next 24 hours (by checking whether further decrementing it by one day moves it to the past).
            decrementDate();
            if (selectedDate.before(Calendar.getInstance())) {
                dateSelectionMode = DateSelectionMode.NEXT24;
            }
            incrementDate();
        }
        renderSelectedDate();
    }

    private void incrementDate() {
        selectedDate.add(Calendar.DAY_OF_MONTH, 1);
    }

    private void decrementDate() {
        selectedDate.add(Calendar.DAY_OF_MONTH, -1);
    }

    /**
     * Display day and month of {@link #selectedDate} in {@link #dateDisplay} and the number of calendar days
     * this date lies ahead. If {@link #dateSelectionMode} is {@link DateSelectionMode#MANUAL}, show
     * everything in orange, otherwise show the "+1" (for "one day ahead" if applicable) in accent color.
     */
    private void renderSelectedDate() {
        int diff = getDiffSelectedDate();
        String sDiff = "";
        if (diff != 0) {
            sDiff += " (+" + getDiffSelectedDate() + ")";
        }
        SpannableString spBase = new SpannableString(DateTimeUtil.formatDate(this, selectedDate.getTime()));
        SpannableString spDiff = new SpannableString(sDiff);
        if (dateSelectionMode == DateSelectionMode.MANUAL) {
            spBase.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.orange)), 0, spBase.length(), 0);
            spDiff.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.orange)), 0, spDiff.length(), 0);
        } else {
            spDiff.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorAccent)), 0, spDiff.length(), 0);
        }
        dateDisplay.setText(new SpannableStringBuilder().append(spBase).append(spDiff));
    }

    private void showChooseNaggingRepeatIntervalDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_number_picker, null);
        NumberPicker naggingRepeatIntervalNumberPicker = dialogView.findViewById(R.id.numberPicker);
        naggingRepeatIntervalNumberPicker.setMinValue(1);
        naggingRepeatIntervalNumberPicker.setMaxValue(Integer.MAX_VALUE);
        naggingRepeatIntervalNumberPicker.setWrapSelectorWheel(false);
        naggingRepeatIntervalNumberPicker.setValue(naggingRepeatInterval);
        new AlertDialog.Builder(this, R.style.dialog_narrow)
                .setView(dialogView)
                .setTitle(R.string.dialog_choose_repeat_interval_title)
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                    naggingRepeatInterval = naggingRepeatIntervalNumberPicker.getValue();
                    // Show the toast about the set nagging repeat interval (also when nagging was already enabled)
                    showToastNaggingRepeatInterval();
                    naggingSwitch.setChecked(true); // Note: this does not trigger the on-cilck listener.
                })
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {})
                .show();
    }

    private void showToastNaggingRepeatInterval() {
        Toast.makeText(ReminderDialogActivity.this, getString(R.string.add_reminder_toast_nagging_enabled, naggingRepeatInterval), Toast.LENGTH_SHORT).show();
    }

    protected Reminder.ReminderBuilder buildReminderWithTimeTextNagging() {
        Reminder.ReminderBuilder reminderBuilder = Reminder.builder()
                .date(selectedDate.getTime())
                .text(nameTextView.getText().toString());

        if (naggingSwitch.isChecked()) {
            reminderBuilder.naggingRepeatInterval(naggingRepeatInterval);
        }

        return reminderBuilder;
    }

    /**
     * Action to be executed on hitting the main "Add" or "OK" button.
     */
    abstract protected void onDone();

    protected void makeToast(Reminder reminder) {
        // Create relative description of due date
        String relativeDueDate = DateUtils.getRelativeTimeSpanString(reminder.getCalendar().getTimeInMillis(), System.currentTimeMillis(), 0).toString();
        // Convert first letter to lower case to use it in a sentence
        if (relativeDueDate.length() > 0) {
            relativeDueDate = relativeDueDate.substring(0, 1).toLowerCase() + relativeDueDate.substring(1);
        }

        // Create toast
        String toastText;
        if (reminder.isNagging()) {
            toastText = getString(R.string.add_reminder_toast_due_nagging, relativeDueDate);
        } else {
            toastText = getString(R.string.add_reminder_toast_due, relativeDueDate);
        }
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
