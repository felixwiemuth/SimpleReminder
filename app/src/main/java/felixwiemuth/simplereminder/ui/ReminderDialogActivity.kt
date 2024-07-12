/*
 * Copyright (C) 2018-2024 Felix Wiemuth and contributors (see CONTRIBUTORS.md)
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
package felixwiemuth.simplereminder.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.DialogInterface
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.DatePicker
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.children
import felixwiemuth.simplereminder.Prefs
import felixwiemuth.simplereminder.R
import felixwiemuth.simplereminder.data.Reminder
import felixwiemuth.simplereminder.data.Reminder.Companion.builder
import felixwiemuth.simplereminder.util.DateTimeUtil
import java.util.Calendar

/**
 * Base class for the reminder dialog activity which is used to add and edit reminders.
 */
abstract class ReminderDialogActivity : AppCompatActivity() {
    private val inputMethodManager by lazy {
        getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    protected lateinit var nameTextView: AutoCompleteTextView
    protected lateinit var naggingSwitch: SwitchCompat
    private lateinit var addButton: Button
    private lateinit var dateMinusButton: Button
    private lateinit var datePlusButton: Button
    private lateinit var dateDisplay: TextView
    private lateinit var timePicker: TimePicker
    private lateinit var dateSelectionMode: DateSelectionMode

    /**
     * The currently selected date. It is an invariant that after every user interaction this
     * date is in the future (except initially, before changing time or date).
     */
    private lateinit var selectedDate: Calendar

    @JvmField
    protected var naggingRepeatInterval = 0

    private enum class DateSelectionMode {
        /**
         * The date is derived from the chosen time, so that this time lies within the next 24 hours.
         */
        NEXT24,

        /**
         * The date is selected manually.
         */
        MANUAL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder_dialog)

        // Note: This adds a warning in logcat "OnBackInvokedCallback is not enabled for the application",
        // but this is about predictive back navigation, and the callback works.
        onBackPressedDispatcher.addCallback { completeActivity() }

        nameTextView = findViewById(R.id.nameTextView)
        addButton = findViewById(R.id.addButton)
        dateMinusButton = findViewById(R.id.dateMinusButton)
        dateMinusButton.setOnClickListener { decrementDateAction() }
        datePlusButton = findViewById(R.id.datePlusButton)
        datePlusButton.setOnClickListener { incrementDateAction() }
        dateDisplay = findViewById(R.id.dateDisplay)
        dateDisplay.setOnClickListener {
            DatePickerDialog(
                this@ReminderDialogActivity,
                { _: DatePicker?, year: Int, month: Int, dayOfMonth: Int -> setDateAction(year, month, dayOfMonth) },
                selectedDate[Calendar.YEAR],
                selectedDate[Calendar.MONTH],
                selectedDate[Calendar.DAY_OF_MONTH]
            ).show()
        }
        naggingSwitch = findViewById(R.id.naggingSwitch)
        naggingSwitch.setOnClickListener {
            if (naggingSwitch.isChecked) {
                showToastNaggingRepeatInterval()
            }
        }
        naggingSwitch.setOnLongClickListener {
            showChooseNaggingRepeatIntervalDialog()
            true
        }

        timePicker = findViewById(R.id.timePicker)

        if (!Prefs.getBooleanPref(R.string.prefkey_reminder_dialog_timepicker_show_keyboard_button, true, this)
            || Prefs.getBooleanPref(R.string.prefkey_reminder_dialog_timepicker_customize_size, true, this)
        ) {
            adaptTimePickerLayout()
        }


        nameTextView.imeOptions = EditorInfo.IME_ACTION_DONE
        nameTextView.setImeActionLabel(getString(R.string.keyboard_action_add_reminder), EditorInfo.IME_ACTION_DONE)
        nameTextView.requestFocus()
        nameTextView.setRawInputType(InputType.TYPE_CLASS_TEXT)
        nameTextView.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onDone()
                return@setOnEditorActionListener true
            }
            false
        }
        selectedDate = Calendar.getInstance() // Initialize calendar variable
        setSelectedDateTimeAndSelectionMode(Calendar.getInstance())

        with(timePicker) {
            setIs24HourView(true)
            setOnTimeChangedListener { _: TimePicker?, hourOfDay: Int, minute: Int ->
                if (dateSelectionMode == DateSelectionMode.NEXT24) {
                    selectedDate = getTimeWithinNext24Hours(hourOfDay, minute)
                } else {
                    selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    selectedDate.set(Calendar.MINUTE, minute)
                }
                renderSelectedDate()
            }
            if (Prefs.getBooleanPref(
                    R.string.prefkey_reminder_dialog_close_keyboard_on_timepicker_use,
                    false,
                    this@ReminderDialogActivity
                )
            ) {
                // Close the keyboard and remove focus from the TextView when touching any view in the TimePicker
                fun addKbCloseOnTouch(view: View) {
                    // noinspection ClickableViewAccessibility
                    view.setOnTouchListener { _, _ ->
                        inputMethodManager.hideSoftInputFromWindow(this.windowToken, 0)
                        nameTextView.clearFocus()
                        false
                    }
                    if (view is ViewGroup) {
                        view.children.forEach { addKbCloseOnTouch(it) }
                    }
                }
                addKbCloseOnTouch(this)
            }
        }
        addButton.setOnClickListener { onDone() }
        naggingRepeatInterval = Prefs.getNaggingRepeatInterval(this)
        renderSelectedDate()
    }

    /**
     * Given an hour and minute, returns a date representing the next occurrence of this time within the next 24 hours. The seconds are set to 0, milliseconds become the value within the current second.
     */
    private fun getTimeWithinNext24Hours(hourOfDay: Int, minute: Int): Calendar {
        val date = Calendar.getInstance()
        date[Calendar.HOUR_OF_DAY] = hourOfDay
        date[Calendar.MINUTE] = minute
        date[Calendar.SECOND] = 0
        // If the resulting date is in the past, the next day is meant
        if (date.before(Calendar.getInstance())) {
            date.add(Calendar.DAY_OF_MONTH, 1)
        }
        return date
    }

    /**
     * Set the selected time to that of the given calendar, setting seconds to 0.
     * Does not render the date/time display.
     *
     * @param calendar
     */
    protected fun setSelectedDateTime(calendar: Calendar) {
        selectedDate.time = calendar.time
        selectedDate[Calendar.SECOND] = 0 // We leave milliseconds as-is, as a little randomness in time is probably good
    }

    /**
     * Set the selected and displayed date/time to that of the given calendar (seconds are set to 0).
     * Also sets the [.dateSelectionMode] based on whether the selected time lies within the next 24 hours.
     * Renders the result.
     *
     * @param calendar
     */
    protected fun setSelectedDateTimeAndSelectionMode(calendar: Calendar) {
        setSelectedDateTime(calendar)

        // Determine date selection mode based on the given date.
        // Check whether the selected time is within the next 24 hours (i.e., decrementing it by one day would move it to the past).
        decrementDate()
        dateSelectionMode = if (selectedDate.before(Calendar.getInstance())) {
            DateSelectionMode.NEXT24
        } else {
            DateSelectionMode.MANUAL
        }
        incrementDate()
        renderSelectedDate()

        // Set the clock widget to the time of the selected date.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.hour = calendar[Calendar.HOUR_OF_DAY]
            timePicker.minute = calendar[Calendar.MINUTE]
        } else @Suppress("DEPRECATION") run {
            timePicker.currentHour = calendar[Calendar.HOUR_OF_DAY]
            timePicker.currentMinute = calendar[Calendar.MINUTE]
        }
    }

    /**
     * If the selected date is the current day, switch to NEXT24 mode and correct the date
     * to the next day if the currently selected time is in the past (as by definition of
     * NEXT24 mode).
     *
     * @return whether the selected date was the current day
     */
    private fun switchToNEXT24IfToday(): Boolean {
        return if (DateTimeUtil.isToday(selectedDate.time)) {
            dateSelectionMode = DateSelectionMode.NEXT24
            if (selectedDate.before(Calendar.getInstance())) {
                incrementDate()
            }
            true
        } else {
            false
        }
    }

    private val diffSelectedDate: Int
        /**
         * Get the number of day changes between the current date and the selected date.
         * If this number is greater than [Integer.MAX_VALUE], the return value is undefined.
         *
         * @return
         */
        get() = DateTimeUtil.dayChangesBetween(Calendar.getInstance(), selectedDate).toInt()

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
    private fun setDateAction(year: Int, month: Int, dayOfMonth: Int) {
        val newSelectedDate = Calendar.getInstance()
        newSelectedDate[Calendar.YEAR] = year
        newSelectedDate[Calendar.MONTH] = month
        newSelectedDate[Calendar.DAY_OF_MONTH] = dayOfMonth
        newSelectedDate[Calendar.HOUR_OF_DAY] = selectedDate[Calendar.HOUR_OF_DAY]
        newSelectedDate[Calendar.MINUTE] = selectedDate[Calendar.MINUTE]
        if (newSelectedDate.before(Calendar.getInstance()) && !DateTimeUtil.isToday(newSelectedDate.time)) {
            Toast.makeText(this, R.string.add_reminder_toast_invalid_date, Toast.LENGTH_LONG).show()
        } else { // newSelectedDate is today or any future day
            setSelectedDateTime(newSelectedDate)
            if (!switchToNEXT24IfToday()) {
                dateSelectionMode = DateSelectionMode.MANUAL
            }
            renderSelectedDate()
        }
    }

    /**
     * Increment the date, set mode to MANUAL and render.
     */
    private fun incrementDateAction() {
        dateSelectionMode = DateSelectionMode.MANUAL
        incrementDate()
        renderSelectedDate()
    }

    /**
     * In MANUAL mode, decrement the date if it is not on the current day.
     * If the resulting date is on the current day, switch to NEXT24 mode, and if it is in the past,
     * increment it again to the next day (as per definition of NEXT24 mode).
     * In NEXT24 mode this does not apply and is ignored.
     * Renders the result.
     */
    private fun decrementDateAction() {
        if (dateSelectionMode == DateSelectionMode.NEXT24 || DateTimeUtil.isToday(selectedDate.time)) {
            return
        }
        decrementDate()
        switchToNEXT24IfToday()
        renderSelectedDate()
    }

    private fun incrementDate() {
        selectedDate.add(Calendar.DAY_OF_MONTH, 1)
    }

    private fun decrementDate() {
        selectedDate.add(Calendar.DAY_OF_MONTH, -1)
    }

    /**
     * Display day and month of [.selectedDate] in [.dateDisplay] and the number of calendar days
     * this date lies ahead. If [.dateSelectionMode] is [DateSelectionMode.MANUAL], show
     * everything in orange, otherwise show the "+1" (for "one day ahead" if applicable) in accent color.
     */
    private fun renderSelectedDate() {
        val diff = diffSelectedDate
        var sDiff = ""
        if (diff != 0) {
            sDiff += " (+$diffSelectedDate)"
        }
        val spBase = SpannableString(DateTimeUtil.formatDate(this, selectedDate.time))
        val spDiff = SpannableString(sDiff)
        if (dateSelectionMode == DateSelectionMode.MANUAL) {
            spBase.setSpan(ForegroundColorSpan(resources.getColor(R.color.orange)), 0, spBase.length, 0)
            spDiff.setSpan(ForegroundColorSpan(resources.getColor(R.color.orange)), 0, spDiff.length, 0)
        } else {
            spDiff.setSpan(ForegroundColorSpan(resources.getColor(R.color.colorAccent)), 0, spDiff.length, 0)
        }
        dateDisplay.text = SpannableStringBuilder().append(spBase).append(spDiff)
    }

    private fun showChooseNaggingRepeatIntervalDialog() {
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_number_picker, null)
        val naggingRepeatIntervalNumberPicker = dialogView.findViewById<NumberPicker>(R.id.numberPicker)
        naggingRepeatIntervalNumberPicker.minValue = 1
        naggingRepeatIntervalNumberPicker.maxValue = Int.MAX_VALUE
        naggingRepeatIntervalNumberPicker.wrapSelectorWheel = false
        naggingRepeatIntervalNumberPicker.value = naggingRepeatInterval
        AlertDialog.Builder(this, R.style.dialog_narrow)
            .setView(dialogView)
            .setTitle(R.string.dialog_choose_repeat_interval_title)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                naggingRepeatInterval = naggingRepeatIntervalNumberPicker.value
                // Show the toast about the set nagging repeat interval (also when nagging was already enabled)
                showToastNaggingRepeatInterval()
                naggingSwitch.isChecked = true // Note: this does not trigger the on-cilck listener.
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showToastNaggingRepeatInterval() {
        Toast.makeText(
            this@ReminderDialogActivity,
            getString(R.string.add_reminder_toast_nagging_enabled, DateTimeUtil.formatMinutes(naggingRepeatInterval.toLong(), this)),
            Toast.LENGTH_SHORT
        ).show()
    }

    protected fun buildReminderWithTimeTextNagging(): Reminder.Builder {
        val reminderBuilder = builder(
            selectedDate.time,
            nameTextView.text.toString()
        )
        if (naggingSwitch.isChecked) {
            reminderBuilder.naggingRepeatInterval = naggingRepeatInterval
        }
        return reminderBuilder
    }

    protected fun setAddButtonText(@StringRes textRes: Int) {
        addButton.setText(textRes)
    }

    /**
     * Action to be executed on hitting the main "Add" or "OK" button.
     */
    protected abstract fun onDone()
    protected fun makeToast(reminder: Reminder) {
        // Create relative description of due date
        val now = Calendar.getInstance()
        val toastText: String
        if (reminder.calendar.before(now)) { // This is a rare case / does not happen in a usual use case.
            toastText = getString(R.string.add_reminder_toast_due_in_past)
        } else {
            val relativeDueDate: String
            val durationUntilDue = DateTimeUtil.daysHoursMinutesBetween(now, reminder.calendar)
            relativeDueDate = if (durationUntilDue.isZero) {
                // This happens when the reminder is due in less than a minute, as the seconds were cut off.
                getString(R.string.duration_less_than_a_minute)
            } else {
                // Note that the string cannot be empty when the duration is not zero with the chosen rounding (either their is at least one day, or at least one minute or hour).
                durationUntilDue.toString(
                    DateTimeUtil.Duration.Resolution.MINUTES_IF_0_DAYS,
                    DateTimeUtil.Duration.RoundingMode.CLOSEST,
                    this
                )
            }
            toastText = if (reminder.isNagging) {
                getString(R.string.add_reminder_toast_due_nagging, relativeDueDate)
            } else {
                getString(R.string.add_reminder_toast_due, relativeDueDate)
            }
        }
        val duration = Toast.LENGTH_LONG
        val toast = Toast.makeText(this, toastText, duration)
        toast.show()
    }

    /**
     * Finish the activity with RESULT_OK, removing the task on Lollipop and above.
     */
    protected fun completeActivity() {
        setResult(RESULT_OK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask() // Adding the reminder completes this task, the dialog should not stay under recent tasks.
        } else {
            finish() // This will leave the task under recent tasks, but it seems that one needs a workaround to prevent this: https://stackoverflow.com/questions/22166282/close-application-and-remove-from-recent-apps
        }
    }

    /**
     * Adapt the time picker layout according to user's set preferences.
     */
    private fun adaptTimePickerLayout() {
        // Removing the whole layout with the toggle button if present (it exists from Android 8.0 on) and disabled by user
        //noinspection DiscouragedApi (have to access the internal system resources by name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && !Prefs.getBooleanPref(R.string.prefkey_reminder_dialog_timepicker_show_keyboard_button, true, this)
        ) {
            val toggleButton = timePicker.findViewById<View>(Resources.getSystem().getIdentifier("toggle_mode", "id", "android"))
            (toggleButton?.parent as? ViewGroup)
                ?.let { toggleButtonLayout ->
                    (toggleButtonLayout.parent as? ViewGroup)
                        ?.let { timePickerRootLayout ->
                            // Remove all views related to the toggle button

                            timePickerRootLayout.removeView(toggleButtonLayout)

                            timePickerRootLayout.removeView(
                                timePickerRootLayout.findViewById(
                                    Resources.getSystem().getIdentifier("input_header", "id", "android")
                                )
                            )

                            timePickerRootLayout.removeView(
                                timePickerRootLayout.findViewById(
                                    Resources.getSystem().getIdentifier("input_mode", "id", "android")
                                )
                            )
                        }
                }
        }

        // Adapting sizes of time display and clock if enabled
        //noinspection DiscouragedApi (have to access the internal system resources by name)
        if (Prefs.getBooleanPref(R.string.prefkey_reminder_dialog_timepicker_customize_size, false, this)) {
            // Changing the height of the time display by changing the font size
            timePicker.findViewById<View>(
                Resources.getSystem().getIdentifier("time_header", "id", "android")
            )?.let { timeHeader ->
                // Adapting the size of all text in the time header (also the AM/PM labels in 12-hour mode).
                // Note that the numbers also serve as buttons to switch between hour and minute selection.

                // In default resource: 60dp
                val timeHeaderTextSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    Prefs.getReminderDialogTimePickerTextSize(this).toFloat(),
                    resources.displayMetrics
                )

                // In default resource: 16dp (smaller than [timeHeaderTextSize] by a factor of 3.75)
                val textSizeAmPmLabel = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    Prefs.getReminderDialogTimePickerTextSize(this).toFloat() / 3.75f,
                    resources.displayMetrics
                )

                timeHeader.findViewById<TextView>(Resources.getSystem().getIdentifier("hours", "id", "android"))
                    ?.let { it.textSize = timeHeaderTextSize }
                timeHeader.findViewById<TextView>(Resources.getSystem().getIdentifier("minutes", "id", "android"))
                    ?.let { it.textSize = timeHeaderTextSize }
                timeHeader.findViewById<TextView>(Resources.getSystem().getIdentifier("separator", "id", "android"))
                    ?.let { it.textSize = timeHeaderTextSize }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // The type of the label views changes on Android 7 (tested with Pixel 5 emulator)
                    timeHeader.findViewById<RadioButton>(Resources.getSystem().getIdentifier("am_label", "id", "android"))
                        ?.let { it.textSize = textSizeAmPmLabel }
                    timeHeader.findViewById<RadioButton>(Resources.getSystem().getIdentifier("pm_label", "id", "android"))
                        ?.let { it.textSize = textSizeAmPmLabel }
                } else {
                    timeHeader.findViewById<CheckedTextView>(Resources.getSystem().getIdentifier("am_label", "id", "android"))
                        ?.let { it.textSize = textSizeAmPmLabel }
                    timeHeader.findViewById<CheckedTextView>(Resources.getSystem().getIdentifier("pm_label", "id", "android"))
                        ?.let { it.textSize = textSizeAmPmLabel }
                }

                // Making the height adapt to the changed font size (however, MATCH_PARENT also seems to work)
                // Note: This expects a LinearLayout.LayoutParams despite the parameter type
                timeHeader.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                val layoutParams = timeHeader.layoutParams as LinearLayout.LayoutParams
                layoutParams.bottomMargin =
                    16 // 16dp is used both as bottom of time header layout and top of time picker, but 16 in total is more symmetric
            }

            // Changing the height of the TimePicker
            timePicker.findViewById<View>(

                Resources.getSystem().getIdentifier("radial_picker", "id", "android")
            )?.let { radialTimePicker ->
                // Note: This expects a LinearLayout.LayoutParams despite the parameter type
                radialTimePicker.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        Prefs.getReminderDialogTimePickerHeight(this).toFloat(),
                        resources.displayMetrics
                    ).toInt()
                )
            }
        }
    }
}
