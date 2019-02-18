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

package felixwiemuth.simplereminder.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.format.DateUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Felix Wiemuth
 */
public class DateTimeUtil {
    private static DateFormat dfDateTime;
    private static DateFormat dfDate;
    private static DateFormat dfTime;

    private static DateFormat getDateTimeFormat() {
        if (dfDateTime == null) {
            dfDateTime = DateFormat.getDateTimeInstance();
        }
        return dfDateTime;
    }


    private static DateFormat getTimeFormat() {
        if (dfTime == null) {
            dfTime = new SimpleDateFormat("HH:mm");
            DateFormat.getTimeInstance(DateFormat.MEDIUM);
        }
        return dfTime;
    }


    private static DateFormat getDateFormat() {
        if (dfDate == null) {
            dfDate = DateFormat.getDateInstance(DateFormat.MEDIUM);
        }
        return dfDate;
    }

    /**
     * Used to compare whether two dates are on the same day:
     */
    private static SimpleDateFormat dfCompareDay;

    @SuppressLint("SimpleDateFormat")
    private static SimpleDateFormat getDfCompareDay() {
        if (dfCompareDay == null) {
            dfCompareDay = new SimpleDateFormat("ddMMyyyy");
        }
        return dfCompareDay;
    }

    public static String formatDateTime(Date date) {
        return getDateTimeFormat().format(date);
    }

    public static String formatDate(Context context, Date date) {
        return DateUtils.formatDateTime(context, date.getTime(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL);
    }

    public static String formatTime(Date date) {
        return getTimeFormat().format(date);
    }

    /**
     * Check whether two dates are on the same day.
     *
     * @param d1
     * @param d2
     * @return
     */
    public static boolean isSameDay(Date d1, Date d2) {
        getDfCompareDay();
        return dfCompareDay.format(d1).equals(dfCompareDay.format(d2));
    }

    /**
     * Check whether the given date is at the current day.
     * @param d
     * @return
     */
    public static boolean isToday(Date d) {
        return isSameDay(d, new Date());
    }
}
