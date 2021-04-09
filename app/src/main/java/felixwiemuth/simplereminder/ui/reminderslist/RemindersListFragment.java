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

package felixwiemuth.simplereminder.ui.reminderslist;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.arch.core.util.Function;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import felixwiemuth.simplereminder.Prefs;
import felixwiemuth.simplereminder.R;
import felixwiemuth.simplereminder.ReminderManager;
import felixwiemuth.simplereminder.data.Reminder;
import felixwiemuth.simplereminder.ui.AddReminderDialogActivity;
import felixwiemuth.simplereminder.util.DateTimeUtil;
import felixwiemuth.simplereminder.util.ImplementationError;
import io.github.luizgrp.sectionedrecyclerviewadapter.CustomViewType;
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionParameters;
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter;
import io.github.luizgrp.sectionedrecyclerviewadapter.StatelessSection;

/**
 * A fragment displaying a list of reminders. May only be used in an {@link AppCompatActivity} with a toolbar. Displays reminders in sections:
 * - A "Due" section: SCHEDULED and NOTIFIED reminders which are due according to the current time, sorted descending by date
 * NOTIFIED)
 * - One section for each of the next 7 (MAX_DAY_SECTIONS) days (including today) for the reminders schedules for those days, each sorted ascending by date
 * - A "Future" section for the remaining scheduled reminders, sorted ascending by date
 * - A "Done" section for reminders with status DONE, sorted descending by date
 */
public class RemindersListFragment extends Fragment {

    /**
     * Ways of displaying the date of a reminder item.
     */
    enum DisplayType {
        /**
         * Display only hour and minute.
         */
        TIME_ONLY,
        /**
         * Display day, month, year, hour and minute.
         */
        FULL,
        /**
         * Like {@link #TIME_ONLY} if the date is the current day, like {@link #FULL} otherwise.
         */
        TIME_ONLY_IF_TODAY
    }

    public static final String BROADCAST_REMINDERS_UPDATED = "felixwiemuth.simplereminder.ui.reminderslist.BROADCAST_REMINDERS_UPDATED";
    private BroadcastReceiver broadcastReceiver;

    private CustomViewType reminderCardTimeOnlyViewType = new CustomViewType(TimeOnlyItemViewHolder.class, R.layout.reminder_card);
    private CustomViewType reminderCardFullDateViewType = new CustomViewType(FullDateItemViewHolder.class, R.layout.reminder_card);

    /**
     * Maximum number of sections (days in the future) for the recycler view to display scheduled reminders in their own section.
     */
    private final int MAX_DAY_SECTIONS = 7;

    /**
     * Mapping containing currently displayed reminders, the key being the reminder ID. May only be updated via {@link #reloadRemindersListAndUpdateRecyclerView()}.
     */
    private SparseArray<Reminder> reminders;

    private RecyclerView remindersListRecyclerView;
    private SectionedRecyclerViewAdapter sectionAdapter;

    /**
     * The current selection of items in {@link #remindersListRecyclerView} (reminder IDs). Must be updated when reminders are removed.
     */
    private Set<Integer> selection; // using Reminder objects might be dangerous as the objects might change when reloading the view (even when IDs stay the same)

    /**
     * The current action mode or null.
     */
    private ActionMode actionMode;
    private MenuItem menuActionReschedule;
    private MenuItem menuActionCopyText;
    private MenuItem menuActionMarkDone;
//    private MenuItem menuActionEdit;

    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.menu_reminders_list_actions, menu);
            menuActionReschedule = menu.findItem(R.id.action_reschedule);
            menuActionCopyText = menu.findItem(R.id.action_copy_text);
            menuActionMarkDone = menu.findItem(R.id.action_mark_done);
//            menuActionEdit = menu.findItem(R.id.action_edit);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            actionMode = mode;
            updateAvailableActions();
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                // NOTE: an "edit" action is probably not needed for reminders list
//                case R.id.action_edit:
//                    Toast.makeText(getContext(), getString(R.string.reminder_list_action_placeholder), Toast.LENGTH_SHORT).show();
//                    mode.finish();
//                    break;
                case R.id.action_reschedule:
                    Intent intent = AddReminderDialogActivity.getIntentEditReminder(getContext(), getOnlySelectedReminder().getId());
                    startActivityForResult(intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP), 0);
                    mode.finish();
                    break;
                case R.id.action_copy_text:
                    ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("Reminder text", getOnlySelectedReminder().getText()));
                    Toast.makeText(getContext(), getString(R.string.reminder_list_action_copy_text_feedback), Toast.LENGTH_SHORT).show();
                    break;
                case R.id.action_mark_done:
                    ReminderManager.updateReminders(getContext(), r -> r.setStatus(Reminder.Status.DONE), selection, true); // have to reschedule as some might still be scheduled
                    mode.finish();
                    break;
                case R.id.action_add_template:
                    //TODO implement
                    Toast.makeText(getContext(), getString(R.string.reminder_list_action_placeholder), Toast.LENGTH_SHORT).show();
//                    mode.finish();
                    break;
                case R.id.action_delete:
                    ReminderManager.removeReminders(getContext(), selection);
                    mode.finish();
                    break;
                case R.id.action_select_all:
                    selectAll();
                    updateAvailableActions();
                    break;
                default:
                    throw new ImplementationError("Action not implemented.");
            }
            return true;
        }

        private Reminder getOnlySelectedReminder() {
            if (selection.size() != 1) {
                throw new ImplementationError("Selection must have size 1.");
            }
            return reminders.get(selection.iterator().next());
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            unselectAll();
        }
    };

    public static Intent getRemindersUpdatedBroadcastIntent() {
        return new Intent(BROADCAST_REMINDERS_UPDATED);
    }

    public RemindersListFragment() {
        // Required empty public constructor
    }

    /**
     * Create a new instance of this fragment.
     *
     * @return
     */
    public static RemindersListFragment newInstance() {
        return new RemindersListFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selection = new HashSet<>();
        reminders = new SparseArray<>();


        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Prefs.setRemindersUpdated(false, getContext()); // clear flag
                reloadRemindersListAndUpdateRecyclerView();
            }
        };

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_reminders_list, container, false);
        remindersListRecyclerView = rootView.findViewById(R.id.reminders_list);
        reloadRemindersListAndUpdateRecyclerView();
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(broadcastReceiver, new IntentFilter(BROADCAST_REMINDERS_UPDATED));
        if (Prefs.isRemindersUpdated(getContext())) {
            Prefs.setRemindersUpdated(false, getContext());
            reloadRemindersListAndUpdateRecyclerView();
        }
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

    /**
     * Call when the reminders list has changed, to reload all items.
     */
    void reloadRemindersListAndUpdateRecyclerView() {
        // Load reminders list
        List<Reminder> remindersList = ReminderManager.getReminders(getContext());
        // Add entries to map (SparseArray)
        reminders.clear();
        for (Reminder reminder : remindersList) {
            reminders.put(reminder.getId(), reminder);
        }

        sectionAdapter = new SectionedRecyclerViewAdapter(reminderCardTimeOnlyViewType, reminderCardFullDateViewType);

        // Section reminders by status
        List<Reminder> remindersDue = new ArrayList<>();
        List<Reminder> remindersScheduled = new ArrayList<>();
        List<Reminder> remindersDone = new ArrayList<>();
        for (Reminder reminder : remindersList) {
            switch (reminder.getStatus()) {
                case NOTIFIED:
                    remindersDue.add(reminder);
                    break;
                case SCHEDULED:
                    remindersScheduled.add(reminder);
                    break;
                case DONE:
                    remindersDone.add(reminder);
                    break;
            }
        }

        // Sort scheduled and done reminders
        Collections.sort(remindersScheduled);
        Collections.sort(remindersDone, (o1, o2) -> -o1.compareTo(o2));

        // Further section scheduled reminders
        Calendar now = Calendar.getInstance();
        Calendar currentTime = Calendar.getInstance(); // represents the day for the current section
        ListIterator<Reminder> it = remindersScheduled.listIterator(); // iterates through all reminders to be divided among the sections

        // If some of the scheduled reminders are actually already due (in mean time or because the status was not correctly updated) move them to the due list
        while (it.hasNext()) {
            Reminder reminder = it.next();
            if (!reminder.getDate().after(now.getTime())) {
                remindersDue.add(reminder);
                it.remove();
            } else {
                break; // Reminders are sorted, so the condition will never hold
            }
        }

        // Sort due reminders after being composed completely
        Collections.sort(remindersDue, (o1, o2) -> -o1.compareTo(o2));

        // Section for due reminders (with a date not in the future)
        sectionAdapter.addSection(new ReminderItemSection(getString(R.string.reminder_section_due), DisplayType.TIME_ONLY_IF_TODAY, remindersDue));

        it = remindersScheduled.listIterator();

        // Construct sections for the next MAX_DAY_SECTIONS days
        if (MAX_DAY_SECTIONS != 0) {
            int dayOffset = 0; // days from the current day
            Function<Integer, String> makeSectionTitle = (Integer d) -> {
//                String date = DateTimeUtil.formatDateWithDayOfWeek(getContext(), currentTime.getTime()); // same as below but with all abbreviated
                String date = DateUtils.formatDateTime(getContext(), currentTime.getTimeInMillis(),
                        DateUtils.FORMAT_SHOW_DATE
                                |DateUtils.FORMAT_SHOW_WEEKDAY
                                | DateUtils.FORMAT_ABBREV_MONTH
                                | DateUtils.FORMAT_NO_YEAR);
                if (d < 2) { // Use relative notion of the day only for "today" and "tomorrow"
                    return DateUtils.getRelativeTimeSpanString(currentTime.getTimeInMillis(), now.getTimeInMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_SHOW_WEEKDAY).toString()
                            + " \u2014 " + date;
                } else { // Use the full name of the day of week otherwise
                    return date;
//                    return DateFormatSymbols.getInstance().getWeekdays()[currentTime.get(Calendar.DAY_OF_WEEK)]; // just show weekday
                }
            };

            List<Reminder> remindersCurrentDay = new ArrayList<>();
            ReminderItemSection section = new ReminderItemSection(makeSectionTitle.apply(dayOffset), DisplayType.TIME_ONLY, remindersCurrentDay); // the current section

            iteratorLoop:
            while (it.hasNext()) {
                Reminder reminder = it.next();
                // If the current reminder does not belong to the current day, advance the current day until it matches the reminder's or the maximum day is reached
                while (!DateTimeUtil.isSameDay(reminder.getDate(), currentTime.getTime())) {
                    // If there were reminders for the current section, add it to the adapter and create a new list for the next section
                    if (!remindersCurrentDay.isEmpty()) {
                        sectionAdapter.addSection(section);
                        remindersCurrentDay = new ArrayList<>();
                    }
                    // Now remindersCurrentDay is empty and can take the reminders for the next day
                    dayOffset++;
                    if (dayOffset == MAX_DAY_SECTIONS) { // The maximum allowed sections are already reached (maximum offset = MAX_DAY_SECTIONS-1)
                        it.previous(); // The current reminder has to be processed with the remaining reminders
                        break iteratorLoop;
                    }
                    currentTime.add(Calendar.DAY_OF_MONTH, 1);
                    // Create the new section
                    section = new ReminderItemSection(makeSectionTitle.apply(dayOffset), DisplayType.TIME_ONLY, remindersCurrentDay);
                }
                remindersCurrentDay.add(reminder);
            }

            // The last section may not have been added yet (if the dayOffset has not been tried to be raised above maximum when the iterator reached the end of the list)
            if (!remindersCurrentDay.isEmpty()) {
                sectionAdapter.addSection(section);
            }
        }

        List<Reminder> futureReminders = new ArrayList<>(); // Scheduled reminders which are further in the future than the days which have an own section
        while (it.hasNext()) {
            futureReminders.add(it.next());
        }
        sectionAdapter.addSection(new ReminderItemSection(getString(R.string.reminder_section_future), DisplayType.FULL, futureReminders));

        // Section for DONE reminders
        sectionAdapter.addSection(new ReminderItemSection(getString(R.string.reminder_section_done), DisplayType.FULL, remindersDone));

        remindersListRecyclerView.setAdapter(sectionAdapter); // This relayouts the view
    }

    /**
     * Update the available actions for action mode based on the current selection.
     */
    private void updateAvailableActions() {
        setMenuItemAvailability(
                menuActionReschedule,
                selection.size() == 1);
        setMenuItemAvailability(
                menuActionCopyText,
                selection.size() == 1);
        boolean selectionContainsDone = false;
        for (Integer i : selection) {
            if (reminders.get(i).getStatus() == Reminder.Status.DONE) {
                selectionContainsDone = true;
            }
        }
        setMenuItemAvailability(
                menuActionMarkDone,
                !selectionContainsDone);
//        setMenuItemAvailability(
//                menuActionEdit,
//                selection.size() == 1);
    }

    private void setMenuItemAvailability(MenuItem menuItem, boolean available) {
        menuItem.setEnabled(available);
        menuItem.setVisible(available);
    }

    public void selectAll() {
        for (int i = 0; i < remindersListRecyclerView.getAdapter().getItemCount(); i++) {
            RecyclerView.ViewHolder viewHolder = remindersListRecyclerView.findViewHolderForAdapterPosition(i);
            if (viewHolder instanceof ItemViewHolder) {
                ((ItemViewHolder) viewHolder).setSelected(getContext());
            }
        }
        for (int i = 0; i < reminders.size(); i++) {
            selection.add(reminders.valueAt(i).getId());
        }
    }

    public void unselectAll() {
        for (int i = 0; i < remindersListRecyclerView.getAdapter().getItemCount(); i++) {
            RecyclerView.ViewHolder viewHolder = remindersListRecyclerView.findViewHolderForAdapterPosition(i);
            if (viewHolder instanceof ItemViewHolder) {
                ((ItemViewHolder) viewHolder).setUnselected();
            }
        }
        selection.clear();
    }

    private class ReminderItemSection extends StatelessSection {

        private String title;
        private DisplayType displayType;
        private List<Reminder> reminders;

        public ReminderItemSection(@NonNull String title, DisplayType displayType, @NonNull List<Reminder> reminders) {
            super(SectionParameters.builder()
                    .itemResourceId(R.layout.reminder_card)
                    .headerResourceId(R.layout.reminder_section_header)
                    .build());
            this.title = title;
            this.displayType = displayType;
            this.reminders = reminders;
        }

        @Override
        public int getContentItemsTotal() {
            return reminders.size();
        }

        @Override
        public RecyclerView.ViewHolder getHeaderViewHolder(View view) {
            return new HeaderViewHolder(view);
        }

        @Override
        public void onBindHeaderViewHolder(RecyclerView.ViewHolder holder) {
            super.onBindHeaderViewHolder(holder);
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            headerHolder.titleView.setText(title);
        }

        @Override
        public int getItemViewType(int position) {
            Reminder reminder = reminders.get(position);
            if (displayType == DisplayType.TIME_ONLY
                    || displayType == DisplayType.TIME_ONLY_IF_TODAY && DateTimeUtil.isToday(reminder.getDate())) {
                return sectionAdapter.getCustomViewTypeKey(reminderCardTimeOnlyViewType);
            } else {
                return sectionAdapter.getCustomViewTypeKey(reminderCardFullDateViewType);
            }
        }

        @Override
        public RecyclerView.ViewHolder getItemViewHolder(View view) {
            throw new ImplementationError("getItemViewHolder should not be called.");
        }

        @Override
        public void onBindItemViewHolder(RecyclerView.ViewHolder viewHolder, int position) {

            if (viewHolder instanceof SectionedRecyclerViewAdapter.EmptyViewHolder) {
                Log.d("onBindItemViewHolder", "Got EmptyViewHolder at section" + title + " for item at position " + position);
                return;
            }

            Reminder reminder = reminders.get(position);

            ItemViewHolder holder = (ItemViewHolder) viewHolder;
            holder.descriptionView.setText(reminder.getText());
            holder.timeView.setText(DateTimeUtil.formatTime(reminder.getDate()));

            if (reminderCardTimeOnlyViewType.isInstanceOf(viewHolder)) {
                TimeOnlyItemViewHolder timeOnlyHolder = (TimeOnlyItemViewHolder) viewHolder;
            } else if (reminderCardFullDateViewType.isInstanceOf(viewHolder)) {
                FullDateItemViewHolder fullDateHolder = (FullDateItemViewHolder) viewHolder;
                fullDateHolder.dateView.setText(DateTimeUtil.formatDate(getContext(), reminder.getDate()));
            }

            // Set color of datefield
            int dateColor;
            switch (reminder.getStatus()) {
                case SCHEDULED:
                    dateColor = ContextCompat.getColor(getContext(), R.color.bg_date_scheduled);
                    break;
                case NOTIFIED:
                    dateColor = ContextCompat.getColor(getContext(), R.color.bg_date_notified);
                    break;
                case DONE:
                    dateColor = ContextCompat.getColor(getContext(), R.color.bg_date_done);
                    break;
                default:
                    dateColor = 0;
                    Log.e("RemindersListFragment", "Unknown color requested.");
            }
            holder.datefieldView.setBackgroundColor(dateColor);

            // Set selection mode of holder
            if (selection.contains(reminder.getId())) {
                holder.setSelected(getContext());
            } else {
                holder.setUnselected();
            }

            holder.itemView.setOnLongClickListener(view -> {
                if (actionMode != null) {
                    return false;
                }
                selection.add(reminder.getId()); // selection must be up-to-date when initializing action-mode
                ((AppCompatActivity) getActivity()).startSupportActionMode(actionModeCallback);
                holder.setSelected(getContext());
                return true;
            });

            holder.itemView.setOnClickListener(view -> {
                if (actionMode != null) {
                    if (selection.contains(reminder.getId())) {
                        selection.remove(reminder.getId());
                        holder.setUnselected();
                        if (selection.isEmpty()) {
                            actionMode.finish();
                        }
                    } else {
                        selection.add(reminder.getId());
                        holder.setSelected(getContext());
                    }
                    updateAvailableActions();
                }
            });

        }

        private class HeaderViewHolder extends RecyclerView.ViewHolder {

            private final TextView titleView;

            HeaderViewHolder(View view) {
                super(view);
                titleView = view.findViewById(R.id.title);
            }
        }
    }

}
