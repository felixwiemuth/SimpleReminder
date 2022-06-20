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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.kelmer.android.fabmenu.fab.FloatingActionButton;
import com.kelmer.android.fabmenu.linear.AdvancedFabMenu;

import java.util.Calendar;
import java.util.EnumMap;
import java.util.Objects;

import felixwiemuth.simplereminder.Prefs;
import felixwiemuth.simplereminder.R;
import felixwiemuth.simplereminder.data.Reminder;
import felixwiemuth.simplereminder.util.DateTimeUtil;

/**
 * Base class for the reminder dialog activity which is used to add and edit reminders.
 */
public abstract class ReminderDialogActivity extends AppCompatActivity {
    protected AutoCompleteTextView nameTextView;
    private AdvancedFabMenu reminderTypeFabMenu;
    private Button addButton;
    private Button dateMinusButton;
    private Button datePlusButton;
    private TextView dateDisplay;
    private TimePicker timePicker;

    private DateSelectionMode dateSelectionMode;
    protected ReminderType reminderType;
    private final EnumMap<ReminderType, ReminderTypeFabInfo> reminderTypeMap = new EnumMap<>(ReminderType.class);
    /**
     * The currently selected date. It is an invariant that after every user interaction this
     * date is in the future (except initially, before changing time or date).
     */
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

    protected enum ReminderType {
        NORMAL,
        NAGGING,
        ALARM,
    }

    /**
     * Simple helper class to connect info about reminder type FABs. Should be only used in the context of reminderTypeMap.
     */
    private static class ReminderTypeFabInfo {
        public FloatingActionButton fab;
        public int viewIdRes;
        public Drawable icon;

        public ReminderTypeFabInfo(@Nullable FloatingActionButton fab, int viewIdRes, Drawable icon) {
            this.fab = fab;
            this.viewIdRes = viewIdRes;
            this.icon = icon;
        }
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
                (view, year, month, dayOfMonth) -> setDateAction(year, month, dayOfMonth),
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show());

        reminderType = ReminderType.NORMAL;
        reminderTypeFabMenu = findViewById(R.id.reminder_type_menu);

        reminderTypeMap.put(ReminderType.NORMAL, new ReminderTypeFabInfo(null,
                R.id.reminder_type_normal,
                AppCompatResources.getDrawable(this, R.drawable.ic_reminder_type_normal)));
        reminderTypeMap.put(ReminderType.NAGGING, new ReminderTypeFabInfo(null,
                R.id.reminder_type_nagging,
                AppCompatResources.getDrawable(this, R.drawable.ic_reminder_type_nagging)));
        reminderTypeMap.put(ReminderType.ALARM, new ReminderTypeFabInfo(null,
                R.id.reminder_type_alarm,
                AppCompatResources.getDrawable(this, R.drawable.ic_reminder_type_alarm)));

        for (ReminderType type : ReminderType.values()) {
            ReminderTypeFabInfo fabInfo = reminderTypeMap.get(type);
            assert fabInfo != null; // if another type is added to ReminderType later, this assertion will trip
            FloatingActionButton fab = findViewById(fabInfo.viewIdRes);

            // TODO: remove if-block when alarm reminders are implemented
            if (type.equals(ReminderType.ALARM)) {
                fab.setOnClickListener(v -> {
                    Toast.makeText(this, R.string.add_reminder_toast_alarm_coming_soon, Toast.LENGTH_SHORT).show();
                });

                fab.setOnLongClickListener(v -> {
                    Toast.makeText(this, R.string.add_reminder_toast_alarm_coming_soon, Toast.LENGTH_SHORT).show();
                    return true;
                });
            } else {
                fab.setOnClickListener(v -> {
                    reminderType = type;
                    reminderTypeFabMenu.close(true);

                    updateReminderTypeFabMenu(type);
                });
            }

            fabInfo.fab = fab;
        }

        Objects.requireNonNull(reminderTypeMap.get(ReminderType.NAGGING)).fab.setOnLongClickListener(v -> {
            showChooseNaggingRepeatIntervalDialog();
            return true;
        });

        updateReminderTypeFabMenu(reminderType);

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

    protected void updateReminderTypeFabMenu(ReminderType newType) {
        ReminderTypeFabInfo fi = reminderTypeMap.get(newType);
        assert fi != null; // if another type is added to ReminderType later, this assertion will trip
        reminderTypeFabMenu.setIcon(fi.icon);
        reminderTypeFabMenu.menuButton.setColors(
                fi.fab.getColorNormal(),
                reminderTypeFabMenu.menuButton.getColorPressed(),
                reminderTypeFabMenu.menuButton.getColorRipple());
        reminderTypeFabMenu.menuButton.updateBackground();

        for (ReminderType type : ReminderType.values()) {
            ReminderTypeFabInfo fabInfo = reminderTypeMap.get(type);
            assert fabInfo != null;
            FloatingActionButton fab = fabInfo.fab;

            if (newType.equals(type)) {
                fab.showProgressBar();
            } else {
                fab.hideProgress();
            }
        }
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
     * Set the selected time to that of the given calendar, setting seconds to 0.
     * Does not render the date/time display.
     *
     * @param calendar
     */
    protected void setSelectedDateTime(Calendar calendar) {
        selectedDate.setTime(calendar.getTime());
        selectedDate.set(Calendar.SECOND, 0); // We leave milliseconds as-is, as a little randomness in time is probably good
    }

    /**
     * Set the selected and displayed date/time to that of the given calendar (seconds are set to 0).
     * Also sets the {@link #dateSelectionMode} based on whether the selected time lies within the next 24 hours.
     * Renders the result.
     *
     * @param calendar
     */
    protected void setSelectedDateTimeAndSelectionMode(Calendar calendar) {
        setSelectedDateTime(calendar);

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
     * If the selected date is the current day, switch to NEXT24 mode and correct the date
     * to the next day if the currently selected time is in the past (as by definition of
     * NEXT24 mode).
     *
     * @return whether the selected date was the current day
     */
    private boolean switchToNEXT24IfToday() {
        if (DateTimeUtil.isToday(selectedDate.getTime())) {
            dateSelectionMode = DateSelectionMode.NEXT24;
            if (selectedDate.before(Calendar.getInstance())) {
                incrementDate();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get the number of day changes between the current date and the selected date.
     * If this number is greater than {@link Integer#MAX_VALUE}, the return value is undefined.
     *
     * @return
     */
    private int getDiffSelectedDate() {
        return (int) DateTimeUtil.dayChangesBetween(Calendar.getInstance(), selectedDate);
    }

    /**
     * Set the selected date to the given year, month and day-of-month and render.
     * If the chosen date is before the current day, do not set the date and show an error toast.
     * If the chosen date is the current day, switch to NEXT24 mode, otherwise switch to MANUAL mode.
     * When switching to NEXT24 mode and the selected time is in the past, correct the date to the
     * next day (as per definition of NEXT24 mode).
     *
     * @param year
     * @param month
     * @param dayOfMonth
     */
    private void setDateAction(int year, int month, int dayOfMonth) {
        Calendar newSelectedDate = Calendar.getInstance();
        newSelectedDate.set(Calendar.YEAR, year);
        newSelectedDate.set(Calendar.MONTH, month);
        newSelectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        newSelectedDate.set(Calendar.HOUR_OF_DAY, selectedDate.get(Calendar.HOUR_OF_DAY));
        newSelectedDate.set(Calendar.MINUTE, selectedDate.get(Calendar.MINUTE));

        if (newSelectedDate.before(Calendar.getInstance()) && !DateTimeUtil.isToday(newSelectedDate.getTime())) {
            Toast.makeText(this, R.string.add_reminder_toast_invalid_date, Toast.LENGTH_LONG).show();
        } else { // newSelectedDate is today or any future day
            setSelectedDateTime(newSelectedDate);
            if (!switchToNEXT24IfToday()) {
                dateSelectionMode = DateSelectionMode.MANUAL;
            }
            renderSelectedDate();
        }
    }

    /**
     * Increment the date, set mode to MANUAL and render.
     */
    private void incrementDateAction() {
        dateSelectionMode = DateSelectionMode.MANUAL;
        incrementDate();
        renderSelectedDate();
    }

    /**
     * In MANUAL mode, decrement the date if it is not on the current day.
     * If the resulting date is on the current day, switch to NEXT24 mode, and if it is in the past,
     * increment it again to the next day (as per definition of NEXT24 mode).
     * In NEXT24 mode this does not apply and is ignored.
     * Renders the result.
     */
    private void decrementDateAction() {
        if (dateSelectionMode == DateSelectionMode.NEXT24 || DateTimeUtil.isToday(selectedDate.getTime())) {
            return;
        }
        decrementDate();
        switchToNEXT24IfToday();

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

                    reminderType = ReminderType.NAGGING;
                    reminderTypeFabMenu.close(true);

                    updateReminderTypeFabMenu(reminderType);
                })
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {})
                .show();
    }

    private void showToastNaggingRepeatInterval() {
        Toast.makeText(ReminderDialogActivity.this, getString(R.string.add_reminder_toast_nagging_enabled, DateTimeUtil.formatMinutes(naggingRepeatInterval, this)), Toast.LENGTH_SHORT).show();
    }

    protected Reminder.ReminderBuilder buildReminderWithTimeTextNagging() {
        Reminder.ReminderBuilder reminderBuilder = Reminder.builder()
                .date(selectedDate.getTime())
                .text(nameTextView.getText().toString());
        // TODO: add ReminderType

        if (reminderType.equals(ReminderType.NAGGING)) {
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
        Calendar now = Calendar.getInstance();
        String toastText;
        if (reminder.getCalendar().before(now)) { // This is a rare case / does not happen in a usual use case.
            toastText = getString(R.string.add_reminder_toast_due_in_past);
        } else {
            String relativeDueDate;
            DateTimeUtil.Duration durationUntilDue = DateTimeUtil.daysHoursMinutesBetween(now, reminder.getCalendar());
            if (durationUntilDue.isZero()) {
                // This happens when the reminder is due in less than a minute, as the seconds were cut off.
                relativeDueDate = getString(R.string.duration_less_than_a_minute);
            } else {
                // Note that the string cannot be empty when the duration is not zero with the chosen rounding (either their is at least one day, or at least one minute or hour).
                relativeDueDate = durationUntilDue.toString(DateTimeUtil.Duration.Resolution.MINUTES_IF_0_DAYS, DateTimeUtil.Duration.RoundingMode.CLOSEST, this);
            }
            if (reminder.isNagging()) {
                toastText = getString(R.string.add_reminder_toast_due_nagging, relativeDueDate);
            } else {
                toastText = getString(R.string.add_reminder_toast_due, relativeDueDate);
            }
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
