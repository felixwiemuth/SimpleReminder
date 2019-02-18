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
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.arch.core.util.Function;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import felixwiemuth.simplereminder.R;
import felixwiemuth.simplereminder.ReminderManager;
import felixwiemuth.simplereminder.data.Reminder;
import felixwiemuth.simplereminder.util.DateTimeUtil;
import felixwiemuth.simplereminder.util.ImplementationError;
import io.github.luizgrp.sectionedrecyclerviewadapter.CustomViewType;
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionParameters;
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter;
import io.github.luizgrp.sectionedrecyclerviewadapter.StatelessSection;

import java.text.DateFormatSymbols;
import java.util.*;

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
    private MenuItem menuActionReuse;
    private MenuItem menuActionMarkDone;
    private MenuItem menuActionEdit;

    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.menu_reminders_list_actions, menu);
            menuActionReuse = menu.findItem(R.id.action_reuse);
            menuActionMarkDone = menu.findItem(R.id.action_mark_done);
            menuActionEdit = menu.findItem(R.id.action_edit);
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
                case R.id.action_edit:
                    if (selection.size() != 1) {
                        throw new ImplementationError("Selection must have size 1.");
                    }
                    reminders.get(selection.iterator().next());
                    mode.finish();
                    break;
                case R.id.action_reuse:
                    //TODO implement
                    mode.finish();
                    break;
                case R.id.action_mark_done:
                    ReminderManager.updateReminders(getContext(), r -> r.setStatus(Reminder.Status.DONE), selection, true); // have to reschedule as some might still be scheduled
                    mode.finish();
                    break;
                case R.id.action_add_template:
                    //TODO implement
                    mode.finish();
                    break;
                case R.id.action_delete:
                    ReminderManager.removeReminders(getContext(), selection);
                    mode.finish();
                    break;
                case R.id.action_select_all:
                    for (int i = 0; i < reminders.size(); i++) {
                        selection.add(reminders.valueAt(i).getId());
                        remindersListRecyclerView.getAdapter().notifyItemChanged(i);
                    }
                    updateAvailableActions();
//                    setAllSelected(); // less expensive alternative, but does not work yet
                    break;
                default:
                    throw new ImplementationError("Action not implemented.");
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            selection.clear();
            // For now reload all items as it is difficult to track which changed
            reloadRemindersListAndUpdateRecyclerView(); // This also updates the visual selection state of items
        }
    };

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
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_reminders_list, container, false);
        remindersListRecyclerView = rootView.findViewById(R.id.reminders_list);
        reloadRemindersListAndUpdateRecyclerView();
        return rootView;
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
                if (d < 2) { // Use relative notion of the day only for "today" and "tomorrow"
                    return DateUtils.getRelativeTimeSpanString(currentTime.getTimeInMillis(), now.getTimeInMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_SHOW_WEEKDAY).toString();
                } else { // Use the full name of the day of week otherwise
                    return DateFormatSymbols.getInstance().getWeekdays()[currentTime.get(Calendar.DAY_OF_WEEK)];
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
                menuActionReuse,
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
        setMenuItemAvailability(
                menuActionEdit,
                selection.size() == 1 && reminders.get(selection.iterator().next()).getStatus() == Reminder.Status.SCHEDULED);
    }

    private void setMenuItemAvailability(MenuItem menuItem, boolean available) {
        menuItem.setEnabled(available);
        menuItem.setVisible(available);
    }


    // This is an alternative to rebind all view holders but does not work yet
//    public void setAllSelected() {
//        for (int i = 0; i < remindersListRecyclerView.getAdapter().getItemCount(); i++) {
//            ((ReminderItemRecyclerViewAdapter.ItemViewHolder) remindersListRecyclerView.findViewHolderForAdapterPosition(i)).setSelected();
//        }
//    }

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

    /**
     * The base item view holder.
     */
    private static class ItemViewHolder extends RecyclerView.ViewHolder {
        private final CardView itemView;
        private final View datefieldView;
        private final ColorStateList cardBackgroundColor;
        private final TextView timeView;
        private final TextView descriptionView;

        ItemViewHolder(@LayoutRes int datefieldRes, @NonNull CardView itemView) {
            super(itemView);
            this.itemView = itemView;
            this.descriptionView = itemView.findViewById(R.id.description);
            cardBackgroundColor = itemView.getCardBackgroundColor();
            ViewStub datefieldViewStub = itemView.findViewById(R.id.datefield_stub);
            datefieldViewStub.setLayoutResource(datefieldRes);
            this.datefieldView = datefieldViewStub.inflate();
            this.timeView = itemView.findViewById(R.id.time);
        }

        void setSelected(Context context) {
            itemView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.bg_selected));
        }

        void setUnselected() {
            itemView.setCardBackgroundColor(cardBackgroundColor);
        }
    }

    /**
     * View holder for items where only time is to be shown.
     */
    private static class TimeOnlyItemViewHolder extends ItemViewHolder {

        public TimeOnlyItemViewHolder(@NonNull View itemView) {
            super(R.layout.reminder_card_datefield_time_only, (CardView) itemView);
        }
    }

    /**
     * View holder for items where time and date is to be shown.
     */
    private static class FullDateItemViewHolder extends ItemViewHolder {
        private final TextView dateView;

        public FullDateItemViewHolder(@NonNull View itemView) {
            super(R.layout.reminder_card_datefield_full_date, (CardView) itemView);
            dateView = itemView.findViewById(R.id.date);
        }
    }
}
