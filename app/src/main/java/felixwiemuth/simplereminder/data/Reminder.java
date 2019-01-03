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

package felixwiemuth.simplereminder.data;

import android.support.annotation.NonNull;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Type;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * @author Felix Wiemuth
 */
@Getter
public class Reminder {
    // NOTE: when changing this class, check sorting criterea in RemindersListFragment.SortedListCallback

    private static Gson gson;

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
        DONE,
        /**
         * The reminder has been cancelled by the user before it was due.
         */
        CANCELLED
    }

    /**
     * ID of the reminder, also used for notifications. Must be >= 0.
     */
    private int id;
    /**
     * Reminder's due date.
     */
    private @Setter Date date;
    private @Setter String text;
    private @Setter Status status;

    @Builder //(builderClassName = "Builder")
    public Reminder(int id, @NonNull Date date, @NonNull String text) {
        if (id < 0) {
            throw new IllegalArgumentException("Id must be >= 0.");
        }
        this.id = id;
        this.date = date;
        this.text = text;
        this.status = Status.SCHEDULED;
    }

    public Calendar getCalendar() {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        return c;
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
}
