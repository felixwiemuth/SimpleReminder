/*
 * Copyright (C) 2018 Felix Wiemuth
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

package felixwiemuth.simplereminder;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import felixwiemuth.simplereminder.data.Reminder;
import felixwiemuth.simplereminder.util.DateTimeUtil;
import felixwiemuth.simplereminder.util.ImplementationError;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemindersListFragment extends Fragment {
    /**
     * A GSON-serialized list of {@link Reminder.Status} values.
     */
    private static final String ARG_STRING_REMINDER_FILTER = "felixwiemuth.simplereminder.arg.REMINDER_FILTER";

    private List<Reminder.Status> reminderFilter;
    /**
     * Filtered list of reminders to be displayed in this fragment.
     */
    private SortedList<Reminder> reminders;

    private RecyclerView remindersListRecyclerView;

    public RemindersListFragment() {
        // Required empty public constructor
    }

    /**
     * @param reminderFilter A filter of which reminders to show (with which status) (required)
     * @return
     */
    public static RemindersListFragment newInstance(List<Reminder.Status> reminderFilter) {
        RemindersListFragment fragment = new RemindersListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_STRING_REMINDER_FILTER, new Gson().toJson(reminderFilter));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        reloadRemindersList();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_reminders_list, container, false);
        remindersListRecyclerView = rootView.findViewById(R.id.reminders_list);
        remindersListRecyclerView.setAdapter(new ReminderItemRecyclerViewAdapter());
        return rootView;
    }

    /**
     * Call when the reminders list has changed, to reload all items.
     */
    public void reloadRemindersList() {
        if (getArguments() != null) {
            // Get filter from arguments
            String reminderFilterJson = getArguments().getString(ARG_STRING_REMINDER_FILTER);
            Type collectionType = new TypeToken<Collection<Reminder.Status>>(){}.getType();
            reminderFilter = new Gson().fromJson(reminderFilterJson, collectionType);

            // Create reminders list
            reminders = new SortedList<>(Reminder.class, new SortedListCallback());
            // Filter
            for (Reminder reminder : ReminderManager.getReminders(getContext())) {
                if (reminderFilter.contains(reminder.getStatus())) {
                    reminders.add(reminder);
                }
            }
        }
        if (remindersListRecyclerView != null) {
            remindersListRecyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    public class SortedListCallback extends SortedList.Callback<Reminder> {
        Map<Reminder.Status, Integer> statusVals = new HashMap<Reminder.Status, Integer>() {{
            put(Reminder.Status.NOTIFIED, 0);
            put(Reminder.Status.SCHEDULED, 1);
            put(Reminder.Status.DONE, 2);
            put(Reminder.Status.CANCELLED, 2);
        }};

        @Override
        public int compare(Reminder o1, Reminder o2) {
            // Reminders are sorted by status first, then by date (increasing or decreasing)
            if (statusVals.get(o1.getStatus()) < statusVals.get(o2.getStatus())) {
                return -1;
            } else if (statusVals.get(o1.getStatus()) > statusVals.get(o2.getStatus())) {
                return 1;
            } else {
                switch (o1.getStatus()) {
                    case SCHEDULED:
                        return o1.getDate().compareTo(o2.getDate());
                    case NOTIFIED:
                    case DONE:
                    case CANCELLED:
                        return -o1.getDate().compareTo(o2.getDate());
                }
            }
            throw new ImplementationError("Incomplete sorting criterea");
        }

        @Override
        public void onChanged(int position, int count) {

        }

        @Override
        public boolean areContentsTheSame(Reminder oldItem, Reminder newItem) {
            // if both are null, it is okay to return false
            return oldItem != null
                    && newItem != null
//                    && oldItem.getId() == newItem.getId() // ID is not shown
                    && oldItem.getStatus() == newItem.getStatus()
                    && oldItem.getDate().equals(newItem.getDate())
                    && oldItem.getText().equals(newItem.getText());
        }

        @Override
        public boolean areItemsTheSame(Reminder item1, Reminder item2) {
            // if both are null, it is okay to return false
            return item1 != null
                    && item2 != null
                    && item1.getId() == item2.getId();
        }

        @Override
        public void onInserted(int position, int count) {

        }

        @Override
        public void onRemoved(int position, int count) {

        }

        @Override
        public void onMoved(int fromPosition, int toPosition) {

        }
    }

    public class ReminderItemRecyclerViewAdapter extends RecyclerView.Adapter<ReminderItemRecyclerViewAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.reminder_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
            holder.dateView.setText(DateTimeUtil.formatDateTime(reminders.get(position).getDate()));
            holder.descriptionView.setText(reminders.get(position).getText());
            int color;
            switch (reminders.get(position).getStatus()) {
                case SCHEDULED:
                    color = ContextCompat.getColor(getContext(), R.color.bg_scheduled);
                    break;
                case NOTIFIED:
                    color = ContextCompat.getColor(getContext(), R.color.bg_notified);
                    break;
                case DONE:
                    color = ContextCompat.getColor(getContext(), R.color.bg_done);
                    break;
                case CANCELLED:
                    color = ContextCompat.getColor(getContext(), R.color.bg_cancelled);
                    break;
                default:
                    color = 0;
                    Log.e("RemindersListFragment", "Unknown color requested.");
            }
            holder.dateView.setBackgroundColor(color);
        }


        @Override
        public int getItemCount() {
            return reminders.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            public final View view;
            public final TextView dateView;
            public final TextView descriptionView;

            public ViewHolder(View view) {
                super(view);
                this.view = view;
                dateView = view.findViewById(R.id.date);
                descriptionView = view.findViewById(R.id.description);
            }

            @Override
            public String toString() {
                return super.toString() + " '" + descriptionView.getText() + "'";
            }
        }
    }

}
