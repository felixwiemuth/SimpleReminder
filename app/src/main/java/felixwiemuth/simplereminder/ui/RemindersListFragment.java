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

import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import felixwiemuth.simplereminder.R;
import felixwiemuth.simplereminder.ReminderManager;
import felixwiemuth.simplereminder.data.Reminder;
import felixwiemuth.simplereminder.util.DateTimeUtil;
import felixwiemuth.simplereminder.util.ImplementationError;
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionParameters;
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter;
import io.github.luizgrp.sectionedrecyclerviewadapter.StatelessSection;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A fragment displaying a list of reminders. May only be used in an {@link AppCompatActivity} with a toolbar.
 */
public class RemindersListFragment extends Fragment {

    /**
     * Mapping containing currently displayed reminders, the key being the reminder ID. May only be updated via {@link #reloadRemindersListAndUpdateRecyclerView()}.
     */
    private SparseArray<Reminder> reminders;

    private RecyclerView remindersListRecyclerView;

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
                        selection.add(reminders.get(i).getId());
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

        SectionedRecyclerViewAdapter sectionAdapter = new SectionedRecyclerViewAdapter();
        Collections.sort(remindersList);
        //TODO add sections for each status
        sectionAdapter.addSection(new ReminderItemSection("All", remindersList));
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
//            ((ReminderItemRecyclerViewAdapter.ViewHolder) remindersListRecyclerView.findViewHolderForAdapterPosition(i)).setSelected();
//        }
//    }

    public class ReminderItemSection extends StatelessSection {
        private String title;
        private List<Reminder> reminders;

        public ReminderItemSection(@NonNull String title, @NonNull List<Reminder> reminders) {
            super(SectionParameters.builder()
                    .itemResourceId(R.layout.reminder_item)
                    .headerResourceId(R.layout.reminder_section_header)
                    .build());
            this.title = title;
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
        public RecyclerView.ViewHolder getItemViewHolder(View view) {
            return new ItemViewHolder(view);
        }

        @Override
        public void onBindItemViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
            ItemViewHolder holder = (ItemViewHolder) viewHolder;

            Reminder reminder = reminders.get(position);
            holder.dateView.setText(DateTimeUtil.formatDateTime(reminder.getDate()));
            holder.descriptionView.setText(reminder.getText());

            // Set color of dateView
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
            holder.dateView.setBackgroundColor(dateColor);

            // Set selection mode of holder
            if (selection.contains(reminder.getId())) {
                holder.setSelected();
            } else {
                holder.setUnselected();
            }

            holder.view.setOnLongClickListener(view -> {
                if (actionMode != null) {
                    return false;
                }
                selection.add(reminder.getId()); // selection must be up-to-date when initializing action-mode
                ((AppCompatActivity) getActivity()).startSupportActionMode(actionModeCallback);
                holder.setSelected();
                return true;
            });

            holder.view.setOnClickListener(view -> {
                if (actionMode != null) {
                    if (selection.contains(reminder.getId())) {
                        selection.remove(reminder.getId());
                        holder.setUnselected();
                        if (selection.isEmpty()) {
                            actionMode.finish();
                        }
                    } else {
                        selection.add(reminder.getId());
                        holder.setSelected();
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

        public class ItemViewHolder extends RecyclerView.ViewHolder {

            private final View view;
            private final TextView dateView;
            private final TextView descriptionView;

            public ItemViewHolder(View view) {
                super(view);
                this.view = view;
                dateView = view.findViewById(R.id.date);
                descriptionView = view.findViewById(R.id.description);
            }

            @Override
            public String toString() {
                return super.toString() + " '" + descriptionView.getText() + "'";
            }

            public void setSelected() {
                view.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.bg_selected));
            }

            public void setUnselected() {
                view.setBackground(null);
            }
        }
    }
}
