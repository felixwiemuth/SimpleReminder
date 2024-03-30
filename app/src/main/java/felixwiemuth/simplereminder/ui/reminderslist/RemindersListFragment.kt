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
package felixwiemuth.simplereminder.ui.reminderslist

import android.app.Activity
import android.content.*
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.SparseArray
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.arch.core.util.Function
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.util.valueIterator
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import felixwiemuth.simplereminder.Prefs
import felixwiemuth.simplereminder.R
import felixwiemuth.simplereminder.ReminderManager
import felixwiemuth.simplereminder.ReminderStorage
import felixwiemuth.simplereminder.data.Reminder
import felixwiemuth.simplereminder.ui.EditReminderDialogActivity
import felixwiemuth.simplereminder.ui.reminderslist.RemindersListFragment.Companion.BROADCAST_REMINDERS_UPDATED
import felixwiemuth.simplereminder.util.DateTimeUtil
import felixwiemuth.simplereminder.util.ImplementationError
import java.util.*

/**
 * A fragment displaying a list of reminders. May only be used in an [AppCompatActivity] with a toolbar. Displays reminders in sections:
 * - A "Due" section: SCHEDULED and NOTIFIED reminders which are due according to the current time, sorted descending by date)
 * - One section for each of the next [maxDaySections] days (including today) for the reminders scheduled for those days, each sorted ascending by date
 * - A "Future" section for the remaining scheduled reminders, sorted ascending by date
 * - A "Done" section for reminders with status DONE, sorted descending by date
 *
 * The list of reminders is updated whenever a [BROADCAST_REMINDERS_UPDATED] broadcast is received.
 */
class RemindersListFragment : Fragment() {
    private lateinit var broadcastReceiver: BroadcastReceiver

    /**
     * Maximum number of sections (days in the future) for the recycler view to display scheduled reminders in their own section.
     */
    val maxDaySections = 7

    /**
     * Mapping containing currently displayed reminders, the key being the reminder ID. May only be updated via [reloadRemindersListAndUpdateRecyclerView].
     */
    private lateinit var reminders: SparseArray<Reminder>
    private lateinit var remindersListRecyclerView: RecyclerView
    private lateinit var concatAdapter: ConcatAdapter

    /**
     * The current selection of items in [remindersListRecyclerView] (reminder IDs). Must be updated when reminders are removed.
     */
    private lateinit var selection // initialized in onCreate; using IDs as reminder content can change
            : MutableSet<Int>

    /**
     * The current action mode or null if not active.
     */
    private var actionMode: ActionMode? = null
    private var menuActionReschedule: MenuItem? = null
    private var menuActionCopyText: MenuItem? = null
    private var menuActionMarkDone: MenuItem? = null

    private val actionModeCallback: ActionMode.Callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val inflater = mode.menuInflater
            inflater.inflate(R.menu.menu_reminders_list_actions, menu)
            menuActionReschedule = menu.findItem(R.id.action_reschedule)
            menuActionCopyText = menu.findItem(R.id.action_copy_text)
            menuActionMarkDone = menu.findItem(R.id.action_mark_done)
            //            menuActionEdit = menu.findItem(R.id.action_edit);
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            actionMode = mode
            updateAvailableActions()
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            // When action change reminders, this will result in a [BROADCAST_REMINDERS_UPDATED]
            // which results in the RecyclerView being reloaded.
            when (item.itemId) {
                R.id.action_reschedule -> {
                    startActivity(
                        EditReminderDialogActivity.getIntentEditReminder(
                            context,
                            onlySelectedReminder.id
                        )
                    )
                    // The following is currently not necessary as reminder updates result in a broadcast received by the fragment.
                    // startEditReminderDialogActivityAndReloadOnOK(onlySelectedReminder.id)
                    mode.finish()
                }
                R.id.action_copy_text -> {
                    val clipboardManager =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboardManager.setPrimaryClip(
                        ClipData.newPlainText(
                            "Reminder text",
                            onlySelectedReminder.text
                        )
                    )
                    if (Build.VERSION.SDK_INT < 33) { // On API 33+, the system shows a visual confirmation (clipboard preview)
                        Toast.makeText(
                            context,
                            getString(R.string.reminder_list_action_copy_text_feedback),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                R.id.action_mark_done -> {
                    ReminderManager.updateReminders(
                        requireContext(),
                        { r: Reminder -> r.status = Reminder.Status.DONE },
                        selection,
                        true
                    ) // have to reschedule as some might still be scheduled
                    mode.finish()
                }
                R.id.action_add_template ->
                    Toast.makeText(
                        context,
                        getString(R.string.reminder_list_action_placeholder),
                        Toast.LENGTH_SHORT
                    ).show()
                R.id.action_delete -> {
                    ReminderManager.removeReminders(requireContext(), selection)
                    mode.finish()
                }
                R.id.action_select_all -> {
                    selectAll()
                    updateAvailableActions()
                }
                else -> throw ImplementationError("Action not implemented.")
            }
            return true
        }

        private val onlySelectedReminder: Reminder
            get() {
                if (selection.size != 1) {
                    throw ImplementationError("Selection must have size 1.")
                }
                return reminders[selection.iterator().next()]
            }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            unselectAll()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selection = HashSet()
        reminders = SparseArray()
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Prefs.setRemindersUpdated(false, getContext()) // clear flag
                reloadRemindersListAndUpdateRecyclerView()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_reminders_list, container, false)
        remindersListRecyclerView = rootView.findViewById(R.id.reminders_list)
        reloadRemindersListAndUpdateRecyclerView()
        return rootView
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(
            broadcastReceiver, IntentFilter(
                BROADCAST_REMINDERS_UPDATED
            )
        )
        if (Prefs.isRemindersUpdated(context)) {
            Prefs.setRemindersUpdated(false, context)
            reloadRemindersListAndUpdateRecyclerView()
        }
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

    /*
     * Start [EditReminderDialogActivity] and reload reminders list when the activity finishes
     * with [Activity.RESULT_OK].
     */
    private fun startEditReminderDialogActivityAndReloadOnOK(reminderId: Int) {
        val intent = EditReminderDialogActivity.getIntentEditReminder(
            context,
            reminderId
        )
        val startActivityForResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
                if (activityResult.resultCode == Activity.RESULT_OK) {
                    reloadRemindersListAndUpdateRecyclerView()
                }
            }
        startActivityForResult.launch(intent)
    }

    /**
     * Call when the reminders list has changed, to reload all items.
     */
    fun reloadRemindersListAndUpdateRecyclerView() {
        // Load reminders list
        val remindersList = ReminderStorage.getReminders(requireContext())
        // Add entries to map (SparseArray)
        reminders.clear()
        for (reminder in remindersList) {
            reminders.put(reminder.id, reminder)
        }
        concatAdapter = ConcatAdapter()
        val addSection = Function { section: ReminderSection ->
            concatAdapter.addAdapter(HeaderAdapter(section.title))
            concatAdapter.addAdapter(RemindersListAdapter(section.reminders, section.timeOnly))
        }

        // Section reminders by status
        val remindersDue: MutableList<Reminder> = ArrayList()
        val remindersScheduled: MutableList<Reminder> = ArrayList()
        val remindersDone: MutableList<Reminder> = ArrayList()
        for (reminder in remindersList) {
            when (reminder.status) {
                Reminder.Status.NOTIFIED -> remindersDue.add(reminder)
                Reminder.Status.SCHEDULED -> remindersScheduled.add(reminder)
                Reminder.Status.DONE -> remindersDone.add(reminder)
            }
        }

        // Sort scheduled and done reminders
        remindersScheduled.sort()
        remindersDone.sortWith { o1: Reminder, o2: Reminder -> -o1.compareTo(o2) }

        // Further section scheduled reminders
        val now = Calendar.getInstance()
        val currentTime = Calendar.getInstance() // represents the day for the current section
        var it = remindersScheduled.listIterator() // iterates through all reminders to be divided among the sections

        // If some of the scheduled reminders are actually already due (in mean time or because the status was not correctly updated) move them to the due list
        for (reminder in it) {
            if (!reminder.date.after(now.time)) {
                remindersDue.add(reminder)
                it.remove()
            } else {
                break // Reminders are sorted, so the condition will never hold
            }
        }

        // Sort due reminders after being composed completely
        remindersDue.sortWith { o1: Reminder, o2: Reminder -> -o1.compareTo(o2) }

        // Section for due reminders (with a date not in the future)
        if (remindersDue.isNotEmpty()) {
            addSection.apply(
                ReminderSection(
                    getString(R.string.reminder_section_due),
                    DisplayType.TIME_ONLY_IF_TODAY,
                    remindersDue
                )
            )
        }
        it = remindersScheduled.listIterator()

        // Construct sections for the next MAX_DAY_SECTIONS days
        if (maxDaySections != 0) {
            var dayOffset = 0 // days from the current day
            val makeSectionTitle = Function { d: Int ->
//                String date = DateTimeUtil.formatDateWithDayOfWeek(getContext(), currentTime.getTime()); // same as below but with all abbreviated
                val date = DateUtils.formatDateTime(
                    context, currentTime.timeInMillis,
                    DateUtils.FORMAT_SHOW_DATE
                            or DateUtils.FORMAT_SHOW_WEEKDAY
                            or DateUtils.FORMAT_ABBREV_MONTH
                            or DateUtils.FORMAT_NO_YEAR
                )
                if (d < 2) { // Use relative notion of the day only for "today" and "tomorrow"
                    return@Function "${
                        DateUtils.getRelativeTimeSpanString(
                            currentTime.timeInMillis,
                            now.timeInMillis,
                            DateUtils.DAY_IN_MILLIS,
                            DateUtils.FORMAT_SHOW_WEEKDAY
                        )
                    } \u2014 $date"
                } else { // Use the full name of the day of week otherwise
                    return@Function date
                    // return DateFormatSymbols.getInstance().getWeekdays()[currentTime.get(Calendar.DAY_OF_WEEK)]; // just show weekday
                }
            }
            var remindersCurrentDay: MutableList<Reminder> = ArrayList()
            var section = ReminderSection(
                makeSectionTitle.apply(dayOffset),
                DisplayType.TIME_ONLY,
                remindersCurrentDay
            ) // the current section
            iteratorLoop@ for (reminder in it) {
                // If the current reminder does not belong to the current day, advance the current day until it matches the reminder's or the maximum day is reached
                while (!DateTimeUtil.isSameDay(reminder.date, currentTime.time)) {
                    // If there were reminders for the current section, add it to the adapter and create a new list for the next section
                    if (remindersCurrentDay.isNotEmpty()) {
                        addSection.apply(section)
                        remindersCurrentDay = ArrayList()
                    }
                    // Now remindersCurrentDay is empty and can take the reminders for the next day
                    dayOffset++
                    if (dayOffset == maxDaySections) { // The maximum allowed sections are already reached (maximum offset = MAX_DAY_SECTIONS-1)
                        it.previous() // The current reminder has to be processed with the remaining reminders
                        break@iteratorLoop
                    }
                    currentTime.add(Calendar.DAY_OF_MONTH, 1)
                    // Create the new section
                    section = ReminderSection(
                        makeSectionTitle.apply(dayOffset),
                        DisplayType.TIME_ONLY,
                        remindersCurrentDay
                    )
                }
                remindersCurrentDay.add(reminder)
            }

            // The last section may not have been added yet (if the dayOffset has not been tried to be raised above maximum when the iterator reached the end of the list)
            if (remindersCurrentDay.isNotEmpty()) {
                addSection.apply(section)
            }
        }
        // Scheduled reminders which are further in the future than the days which have an own section
        val futureReminders: MutableList<Reminder> = ArrayList()
        for (reminder in it) {
            futureReminders.add(reminder)
        }
        if (futureReminders.isNotEmpty()) {
            addSection.apply(
                ReminderSection(
                    getString(R.string.reminder_section_future),
                    DisplayType.FULL,
                    futureReminders
                )
            )
        }

        // Section for DONE reminders
        if (remindersDone.isNotEmpty()) {
            addSection.apply(
                ReminderSection(
                    getString(R.string.reminder_section_done),
                    DisplayType.FULL,
                    remindersDone
                )
            )
        }

        remindersListRecyclerView.adapter = concatAdapter // This relayouts the view
    }

    /**
     * Update the available actions for action mode based on the current selection.
     */
    private fun updateAvailableActions() {
        setMenuItemAvailability(
            menuActionReschedule,
            selection.size == 1
        )
        setMenuItemAvailability(
            menuActionCopyText,
            selection.size == 1
        )
        var selectionContainsDone = false
        for (i in selection) {
            if (reminders[i].status == Reminder.Status.DONE) {
                selectionContainsDone = true
            }
        }
        setMenuItemAvailability(
            menuActionMarkDone,
            !selectionContainsDone
        )
    }

    private fun setMenuItemAvailability(menuItem: MenuItem?, available: Boolean) {
        menuItem!!.isEnabled = available
        menuItem.isVisible = available
    }

    fun selectAll() {
        // Set selected state for all [ReminderViewHolder]s
        for (i in 0 until remindersListRecyclerView.adapter!!.itemCount) {
            val viewHolder = remindersListRecyclerView.findViewHolderForAdapterPosition(i)
            if (viewHolder is ReminderViewHolder) {
                viewHolder.setSelected(requireContext())
            }
        }
        // Add all reminders to selection
        for ((id) in reminders.valueIterator()) {
            selection.add(id)
        }
    }

    fun unselectAll() {
        // Set selected state for all [ReminderViewHolder]s
        for (i in 0 until remindersListRecyclerView.adapter!!.itemCount) {
            val viewHolder = remindersListRecyclerView.findViewHolderForAdapterPosition(i)
            if (viewHolder is ReminderViewHolder) {
                viewHolder.setUnselected()
            }
        }
        // Remove all reminders from selection
        selection.clear()
    }

    private class ReminderSection(
        val title: String,
        val timeOnly: DisplayType,
        val reminders: List<Reminder>
    )

    companion object {
        const val BROADCAST_REMINDERS_UPDATED =
            "felixwiemuth.simplereminder.ui.reminderslist.BROADCAST_REMINDERS_UPDATED"

        fun getRemindersUpdatedBroadcastIntent(): Intent = Intent(BROADCAST_REMINDERS_UPDATED)

        /**
         * Create a new instance of this fragment.
         *
         * @return
         */
        @JvmStatic
        fun newInstance(): RemindersListFragment {
            return RemindersListFragment()
        }
    }

    /**
     * Adapter for a list for reminders, belonging to one "section" (prepended by a [HeaderAdapter]).
     * It has a [DisplayType] determining how the reminder entries should be displayed.
     */
    private inner class RemindersListAdapter(
        private val reminders: List<Reminder>,
        private val displayType: DisplayType
    ) : RecyclerView.Adapter<ReminderViewHolder>() {

        /**
         * Returns the resource id of the layout for the date field the [ReminderViewHolder] will use.
         */
        override fun getItemViewType(position: Int): Int =
            when (displayType) {
                DisplayType.TIME_ONLY -> R.layout.reminder_card_datefield_time_only
                DisplayType.FULL -> R.layout.reminder_card_datefield_full_date
                DisplayType.TIME_ONLY_IF_TODAY ->
                    if (DateTimeUtil.isToday(reminders[position].date)) {
                        R.layout.reminder_card_datefield_time_only
                    } else {
                        R.layout.reminder_card_datefield_full_date
                    }
            }

        override fun getItemCount(): Int {
            return reminders.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
            val cardView: CardView =
                LayoutInflater.from(context)
                    .inflate(R.layout.reminder_card, parent, false) as CardView
            return when (viewType) {
                // We do not use the resource id directly because we have to instantiate the
                // correct subclass anyway.
                R.layout.reminder_card_datefield_time_only -> TimeOnlyReminderViewHolder(cardView)
                R.layout.reminder_card_datefield_full_date -> FullDateReminderViewHolder(cardView)
                else -> error("Unknown viewType")
            }
        }

        override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
            val reminder = reminders[position]
            holder.descriptionView.text = reminder.text
            holder.timeView.text = DateTimeUtil.formatTime(reminder.date)
            holder.initializeDateView(reminder.date, requireContext())

            // Set color of datefield
            val dateColor: Int = when (reminder.status) {
                Reminder.Status.SCHEDULED -> ContextCompat.getColor(
                    requireContext(),
                    R.color.bg_date_scheduled
                )
                Reminder.Status.NOTIFIED -> ContextCompat.getColor(
                    requireContext(),
                    R.color.bg_date_notified
                )
                Reminder.Status.DONE -> ContextCompat.getColor(
                    requireContext(),
                    R.color.bg_date_done
                )
            }
            holder.datefieldView.setBackgroundColor(dateColor)

            // Set selection mode of holder
            if (selection.contains(reminder.id)) {
                holder.setSelected(requireContext())
            } else {
                holder.setUnselected()
            }

            holder.itemView.setOnLongClickListener {
                if (actionMode != null) {
                    return@setOnLongClickListener false
                }
                selection.add(reminder.id) // selection must be up-to-date when initializing action-mode
                (activity as AppCompatActivity).startSupportActionMode(actionModeCallback)
                holder.setSelected(requireContext())
                true
            }

            holder.itemView.setOnClickListener {
                if (actionMode == null) {
                    startActivity(
                        EditReminderDialogActivity.getIntentEditReminder(
                            context,
                            reminder.id
                        )
                    )
                    // The following is currently not necessary as reminder updates result in a broadcast received by the fragment.
                    // startEditReminderDialogActivityAndReloadOnOK(reminder.id)
                } else {
                    if (selection.contains(reminder.id)) {
                        selection.remove(reminder.id)
                        holder.setUnselected()
                        if (selection.isEmpty()) {
                            actionMode!!.finish()
                        }
                    } else {
                        selection.add(reminder.id)
                        holder.setSelected(requireContext())
                    }
                    updateAvailableActions()
                }
            }
        }
    }

    /**
     * An adapter representing a header entry. It will always contain one [HeaderViewHolder].
     */
    private inner class HeaderAdapter(val title: String) :
        RecyclerView.Adapter<HeaderViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
            val viewHolder = LayoutInflater.from(context)
                .inflate(R.layout.reminder_section_header, parent, false)
            return HeaderViewHolder(viewHolder)
        }

        override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
            holder.titleView.text = title
        }

        override fun getItemCount(): Int {
            return 1
        }
    }
}