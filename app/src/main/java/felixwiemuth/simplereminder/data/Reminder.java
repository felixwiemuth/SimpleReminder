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

package felixwiemuth.simplereminder.data;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Felix Wiemuth
 */
@Getter
public class Reminder implements Comparable<Reminder> {
    private static Gson gson;

    private static final int OFFSET_REQUEST_CODE_ADD_REMINDER_DIALOG_ACTIVITY_PENDING_INTENT = 1000000;

    /**
     * Status of saved reminders.
     */
    public enum Status {
        /**
         * The reminder has been scheduled but is not due yet.
         */
        SCHEDULED,
        /**
         * The reminder is due and the notification has been sent.
         */
        NOTIFIED,
        /**
         * The reminder has been marked as "done" by the user.
         */
        DONE
    }

    /**
     * ID of the reminder, also used for notifications. Must be in the interval [0,{@link #OFFSET_REQUEST_CODE_ADD_REMINDER_DIALOG_ACTIVITY_PENDING_INTENT}) and even (used by {@link felixwiemuth.simplereminder.ReminderService for correct scheduling}).
     */
    private final int id;
    /**
     * Reminder's due date.
     */
    private @Setter Date date;
    /**
     * The interval in minutes this reminder should be repeated until dismissed.
     * This field is optional. A value <= 0 (or omitting in JSON) means that nagging is disabled, which is the default.
     * @since 0.9.9
     */
    private final int naggingRepeatInterval;
    private @Setter String text;
    private @Setter ReminderType reminderType;
    private @Setter Status status;

    public enum ReminderType {
        NORMAL,
        NAGGING,
        ALARM,
    }

    @Builder //(builderClassName = "Builder")
    public Reminder(int id, @NonNull Date date, int naggingRepeatInterval, @NonNull String text, @NonNull ReminderType reminderType) {
        if (id < 0) {
            throw new IllegalArgumentException("Id must be >= 0.");
        }
        this.id = id;
        this.date = date;
        this.naggingRepeatInterval = naggingRepeatInterval;
        this.text = text;
        this.reminderType = reminderType;
        this.status = Status.SCHEDULED;
    }

    public Calendar getCalendar() {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        return c;
    }

    @Override
    public int compareTo(Reminder reminder) {
        return date.compareTo(reminder.date);
    }

    public static String toJson(List<Reminder> reminders) {
        return getGson().toJson(reminders);
    }

    public static List<Reminder> fromJson(String json) {
        Type collectionType = new TypeToken<Collection<Reminder>>() {
        }.getType();
        return getGson().fromJson(json, collectionType);
    }

    private static Gson getGson() {
        if (gson == null) {
            gson = new GsonBuilder()
                    .registerTypeAdapter(Date.class, (JsonDeserializer<Date>) (json, typeOfT, context) -> new Date(json.getAsJsonPrimitive().getAsLong()))
                    .registerTypeAdapter(Date.class, (JsonSerializer<Date>) (date, type, jsonSerializationContext) -> new JsonPrimitive(date.getTime()))
                    .create();
        }
        return gson;
    }

    public int getRequestCodeAddReminderDialogActivityPendingIntent() {
        return getRequestCodeAddReminderDialogActivityPendingIntent(id);
    }

    public static int getRequestCodeAddReminderDialogActivityPendingIntent(int reminderID) {
        return OFFSET_REQUEST_CODE_ADD_REMINDER_DIALOG_ACTIVITY_PENDING_INTENT + reminderID;
    }

    public boolean isNagging() {
        return reminderType.equals(ReminderType.NAGGING);
    }

    public long getNaggingRepeatIntervalInMillis() {
        return 60*1000*naggingRepeatInterval;
    }
}
