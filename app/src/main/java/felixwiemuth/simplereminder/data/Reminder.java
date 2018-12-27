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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

/**
 * @author Felix Wiemuth
 */
public class Reminder {

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
     * ID of the reminder, also used for notifications.
     */
    private int id;
    /**
     * Reminder's due date.
     */
    private long date;
    private String text;
    private Status status;

//    public Reminder(int id, Calendar date, String text) {
//        this.id = id;
//        this.date = date;
//        this.text = text;
//        this.status = Status.SCHEDULED;
//    }

    public Reminder(int id, long date, String text) {
        this.id = id;
        this.date = date;
        this.text = text;
        this.status = Status.SCHEDULED;
    }

    public int getId() {
        return id;
    }

    public Calendar getCalendar() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(date);
        return c;
    }

    public String getText() {
        return text;
    }

    public Status getStatus() {
        return status;
    }

    public static String toJson(List<Reminder> reminders) {
        return new Gson().toJson(reminders);
    }

    public static List<Reminder> fromJson(String json) {
        Type collectionType = new TypeToken<Collection<Reminder>>(){}.getType();
        return new Gson().fromJson(json, collectionType);
    }
}
