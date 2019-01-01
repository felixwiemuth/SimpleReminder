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

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

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

    public RemindersListFragment() {
        // Required empty public constructor
    }

    /**
     *
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_reminders_list, container, false);
        RecyclerView remindersListRecyclerView = rootView.findViewById(R.id.reminders_list);
        remindersListRecyclerView.setAdapter(new ReminderItemRecyclerViewAdapter());
        return rootView;
    }

    // TODO implement based on current sorting criterea
    public class SortedListCallback extends SortedList.Callback<Reminder> {

        @Override
        public int compare(Reminder o1, Reminder o2) {
            return 0;
        }

        @Override
        public void onChanged(int position, int count) {

        }

        @Override
        public boolean areContentsTheSame(Reminder oldItem, Reminder newItem) {
            return false;
        }

        @Override
        public boolean areItemsTheSame(Reminder item1, Reminder item2) {
            return false;
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

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.reminder_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
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
