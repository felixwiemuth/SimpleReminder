/*
 * Copyright (C) 2018-2022 Felix Wiemuth and contributors (see CONTRIBUTORS.md)
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

import androidx.annotation.NonNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import felixwiemuth.simplereminder.R;

/**
 * Utilities for formatting and calculation with date and time.
 * Note that we cannot use java.time because it requires API 26.
 */
public class DateTimeUtil {
    private static DateFormat dfDateTime;
    private static DateFormat dfDate;
    private static DateFormat dfTime;

    /**
     * Represents a duration in days, hours and minutes and whether it is a positive or negative duration.
     */
    public static class Duration {
        /**
         * Which unit to round to.
         */
        public enum Resolution {
            DAYS,
            HOURS,
            MINUTES,
            /**
             * Like {@link #MINUTES} if number of days is zero, like {@link #HOURS} otherwise.
             */
            MINUTES_IF_0_DAYS
        }

        /**
         * How to round to the next unit.
         */
        public enum RoundingMode {
            /**
             * Round towards closest (up when same difference).
             */
            CLOSEST,
            DOWN,
            UP
        }

        private final long days;
        private final long hours;
        private final int minutes;
        private final boolean positive;

        /**
         * Create a positive duration with 0 days.
         *
         * @param hours
         * @param minutes
         * @see #Duration(long, long, int, boolean)
         */
        public Duration(long hours, int minutes) {
            this(0, hours, minutes, true);
        }

        /**
         * Create a duration with 0 days.
         * @param hours
         * @param minutes
         * @param positive
         * @see #Duration(long, long, int, boolean)
         */
        public Duration(long hours, int minutes, boolean positive) {
            this(0, hours, minutes, positive);
        }

        /**
         * Create a positive duration.
         *
         * @param days
         * @param hours
         * @param minutes
         * @see #Duration(long, long, int, boolean)
         */
        public Duration(long days, long hours, int minutes) {
            this(days, hours, minutes, true);
        }

        /**
         * Create a duration. All fields must be non-negative numbers.
         *
         * @param days
         * @param hours
         * @param minutes
         * @param positive
         */
        public Duration(long days, long hours, int minutes, boolean positive) {
            this.days = days;
            this.hours = hours;
            this.minutes = minutes;
            this.positive = positive;
        }

        public long getDays() {
            return days;
        }

        public long getHours() {
            return hours;
        }

        public int getMinutes() {
            return minutes;
        }

        /**
         * Whether the duration is declared positive.
         *
         * @return
         */
        public boolean isPositive() {
            return positive;
        }

        /**
         * Whether days, hours and minutes are all zero.
         * @return
         */
        public boolean isZero() {
            return days == 0 && hours == 0 && minutes == 0;
        }


        /**
         * Get a textual description of the duration with the possibility to round.
         * If {@code isZero() == true}, the empty string is returned.
         *
         * @param resolution which units to show, see {@link Resolution}
         * @param roundingMode how to round, see {@link RoundingMode}
         * @param context
         */
        public String toString(Resolution resolution, RoundingMode roundingMode, Context context) {
            int printMinutes = minutes;
            long printHours = hours;
            long printDays = days;
            // Apply rounding: set those units to 0 which should be rounded away.
            if (resolution == Resolution.HOURS || resolution == Resolution.MINUTES_IF_0_DAYS && days > 0) {
                printMinutes = 0;
                if (roundingMode == RoundingMode.UP && minutes > 0 || roundingMode == RoundingMode.CLOSEST && minutes >= 30) {
                    printHours++;
                    if (printHours == 24) {
                        printDays += 1;
                        printHours -= 24;
                    }
                }
            } else if (resolution == Resolution.DAYS) {
                printMinutes = 0;
                printHours = 0;
                if (roundingMode == RoundingMode.UP && hours > 0 || roundingMode == RoundingMode.CLOSEST && hours >= 12) {
                    printDays++;
                }
            }
            StringBuilder sb = new StringBuilder();
            boolean lastConjunctionUsed = false; // Whether the conjunction to be used between the last two units has been used
            if (printMinutes != 0) {
                sb.insert(0, formatMinutes(printMinutes, context));
            }
            if (printHours != 0) {
                lastConjunctionUsed = insertConjunction(lastConjunctionUsed, sb, context);
                sb.insert(0, formatHours(printHours, context));
            }
            if (printDays != 0) {
                lastConjunctionUsed = insertConjunction(lastConjunctionUsed, sb, context);
                sb.insert(0, formatDays(printDays, context));
            }
            return sb.toString();
        }

        private static boolean insertConjunction(boolean lastConjunctionUsed, StringBuilder sb, Context context) {
            if (sb.length() != 0) {
                if (lastConjunctionUsed) {
                    sb.insert(0, context.getString(R.string.duration_conjunction));
                } else {
                    sb.insert(0, context.getString(R.string.duration_conjunction_last));
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Duration that = (Duration) o;

            if (days != that.days) return false;
            if (hours != that.hours) return false;
            if (minutes != that.minutes) return false;
            return positive == that.positive;
        }

        // Generated by IntelliJ
        @Override
        public int hashCode() {
            int result = (int) (days ^ (days >>> 32));
            result = 31 * result + (int) (hours ^ (hours >>> 32));
            result = 31 * result + minutes;
            result = 31 * result + (positive ? 1 : 0);
            return result;
        }

        @NonNull
        @Override
        public String toString() {
            return "DurationHoursMinutes{" +
                    "days=" + days +
                    ", hours=" + hours +
                    ", minutes=" + minutes +
                    ", positive=" + positive +
                    '}';
        }
    }

    /**
     * Format the given amount of minutes (e.g. "0 minutes", "1 minute").
     * Uses singular form for input "1" and plural form otherwise.
     *
     * @param minutes
     * @param context
     * @return
     */
    public static String formatMinutes(long minutes, Context context) {
        if (minutes == 1) {
            return context.getString(R.string.duration_minute, minutes);
        } else {
            return context.getString(R.string.duration_minutes, minutes);
        }
    }

    /**
     * Format the given amount of hours (e.g. "0 hours", "1 hour").
     * Uses singular form for input "1" and plural form otherwise.
     *
     * @param hours
     * @param context
     * @return
     */
    public static String formatHours(long hours, Context context) {
        if (hours == 1) {
            return context.getString(R.string.duration_hour, hours);
        } else {
            return context.getString(R.string.duration_hours, hours);
        }
    }

    /**
     * Format the given amount of days (e.g. "0 days", "1 day").
     * Uses singular form for input "1" and plural form otherwise.
     *
     * @param days
     * @param context
     * @return
     */
    public static String formatDays(long days, Context context) {
        if (days == 1) {
            return context.getString(R.string.duration_day, days);
        } else {
            return context.getString(R.string.duration_days, days);
        }
    }

    private static DateFormat getDateTimeFormat() {
        if (dfDateTime == null) {
            dfDateTime = DateFormat.getDateTimeInstance();
        }
        return dfDateTime;
    }


    @SuppressLint("SimpleDateFormat")
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

    public static String formatDateWithDayOfWeek(Context context, Date date) {
        return DateUtils.formatDateTime(context, date.getTime(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL);
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
        return getDfCompareDay().format(d1).equals(dfCompareDay.format(d2));
    }

    /**
     * Check whether the given date is at the current day.
     *
     * @param d
     * @return
     */
    public static boolean isToday(Date d) {
        return isSameDay(d, new Date());
    }

    /**
     * Get a copy of the given date where hour, minute, second and millisecond are set to 0.
     *
     * @param date
     * @return
     */
    public static Calendar getDateAtMidnight(Calendar date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date.getTime());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    /**
     * Given two dates, calculates how often the day (number) is incremented from the first to the second.
     * Based on https://stackoverflow.com/a/6406294.
     */
    public static long dayChangesBetween(Calendar start, Calendar end) {
        if (isSameDay(start.getTime(), end.getTime())) {
            return 0;
        }

        Calendar d = getDateAtMidnight(start);
        Calendar e = getDateAtMidnight(end);
        long dayChanges = 0;
        while (d.before(e)) {
            d.add(Calendar.DAY_OF_MONTH, 1);
            dayChanges++;
        }
        return dayChanges;
    }

    /**
     * Return the duration of hours and minutes between two timestamps.
     * If end < start, then the result is a negative duration. There is no rounding,
     * exceeding seconds are cut off.
     *
     * @param start start time in milliseconds
     * @param end   end time in milliseconds
     * @return
     */
    public static Duration hoursMinutesBetween(long start, long end) {
        if (start <= end) {
            return hoursMinutesBetween_(start, end, true);
        } else {
            return hoursMinutesBetween_(end, start, false);
        }
    }

    private static Duration hoursMinutesBetween_(long start, long end, boolean positive) {
        long seconds = (end - start) / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds - hours * 3600) / 60;
        return new Duration(hours, (int) minutes, positive);
    }

    /**
     * Return the duration of days, hours and minutes between two dates. There is no rounding, exceeding seconds are cut off.
     * Days are in terms of calendar days, except for the last day change and the remaining hours.
     * This only has relevance regarding daylight saving time and means that the last 24 hours are interpreted as one day and that when clocks are set back by one hour it is possible to get a duration like "1 day, 24 hours, 50 minutes".
     *
     * @param start
     * @param end   Must be after {@code start} and the day changes between the two dates must not exceed {@link Integer#MAX_VALUE}.
     * @return
     * @throws IllegalArgumentException if any precondition is violated
     */
    public static Duration daysHoursMinutesBetween(Calendar start, Calendar end) throws IllegalArgumentException {
        /*
         * Check preconditions
         */
        if (!start.before(end)) {
            throw new IllegalArgumentException("Negative duration");
        }

        long dayChangesLong = dayChangesBetween(start, end);
        if (dayChangesLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Duration too large");
        }
        int dayChanges = (int) dayChangesLong;

        /*
         * Calculate number of days, hours and minutes.
         */
        int days = 0;
        // 'end' after subtracting days so that dayChanges <= 1; i.e., duration between start and newEnd is less than 1 day and 24 hours (except for days with more than 24 hours)
        Calendar newEnd = Calendar.getInstance();
        newEnd.setTime(end.getTime());
        if (dayChanges > 1) {
            days = dayChanges - 1;
            newEnd.add(Calendar.DAY_OF_MONTH, -days);
        }

        Duration duration = hoursMinutesBetween(start.getTime().getTime(), newEnd.getTime().getTime());
        int hours = (int) duration.getHours(); // always less than the equivalent of two days
        int minutes = duration.getMinutes();
        // Here we call potentially remaining 24 hours one day.
        if (hours >= 24) {
            days++;
            hours -= 24;
        }
        return new Duration(days, hours, minutes, duration.isPositive());
    }
}