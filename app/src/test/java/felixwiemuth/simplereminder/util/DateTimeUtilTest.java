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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.Calendar;

@RunWith(Enclosed.class)
public class DateTimeUtilTest {

    public static class TestHoursMinutesBetween {

        @Test
        public void testHoursMinutesBetween1() {
            testPositiveNegative(0, 0, new DateTimeUtil.Duration(0, 0, true));
        }

        @Test
        public void testHoursMinutesBetween2() {
            testPositiveNegative(1234567890, 1234567890, new DateTimeUtil.Duration(0, 0, true));
        }

        @Test
        public void testHoursMinutesBetween3() {
            testPositiveNegative(0, 1000, new DateTimeUtil.Duration(0, 0, true));
        }

        @Test
        public void testHoursMinutesBetween4() {
            testPositiveNegative(123400000, 123459999, new DateTimeUtil.Duration(0, 0, true));
        }

        @Test
        public void testHoursMinutesBetween5() {
            testPositiveNegative(123400000, 123460000, new DateTimeUtil.Duration(0, 1, true));
        }

        @Test
        public void testHoursMinutesBetween6() {
            testPositiveNegative(0, 1000 * 60 * 60 - 1, new DateTimeUtil.Duration(0, 59, true));
        }

        @Test
        public void testHoursMinutesBetween7() {
            testPositiveNegative(0, 1000 * 60 * 60, new DateTimeUtil.Duration(1, 0, true));
        }

        @Test
        public void testHoursMinutesBetween8() {
            testPositiveNegative(0, 1000 * 61 * 60, new DateTimeUtil.Duration(1, 1, true));
        }

        @Test
        public void testHoursMinutesBetween9() {
            testPositiveNegative(0, 1000L * 2000 * 60 * 60 + 1000 * 60 * 5, new DateTimeUtil.Duration(2000, 5, true));
        }

        @Test
        public void testHoursBetween10() {
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.setTime(start.getTime());
            end.add(Calendar.MINUTE, 1);
            testWithCalendar(start, end, 0, 1);
        }

        @Test
        public void testHoursBetween11() {
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.setTime(start.getTime());
            end.add(Calendar.HOUR_OF_DAY, 1);
            testWithCalendar(start, end, 1, 0);
        }

        @Test
        public void testHoursBetween12() {
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.setTime(start.getTime());
            end.add(Calendar.SECOND, 59);
            testWithCalendar(start, end, 0, 0);
        }

        @Test
        public void testHoursBetween13() {
            Calendar start = Calendar.getInstance();
            testWithCalendar(start, start, 0, 0);
        }

        @Test
        public void testHoursBetween14() {
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.setTime(start.getTime());
            end.add(Calendar.SECOND, 60 * 70);
            // This should hold even with leap seconds etc.
            testWithCalendar(start, end, 1, 10);
        }

        private static void testPositiveNegative(long start, long end, DateTimeUtil.Duration expected) {
            assertEquals(expected, DateTimeUtil.hoursMinutesBetween(start, end));
            if (start != end) {
                assertEquals(new DateTimeUtil.Duration(expected.getHours(), expected.getMinutes(), false), DateTimeUtil.hoursMinutesBetween(end, start));
            }
        }

        private static void testWithCalendar(Calendar start, Calendar end, long hours, int minutes) {
            testPositiveNegative(start.getTime().getTime(), end.getTime().getTime(), new DateTimeUtil.Duration(hours, minutes, true));
        }
    }
}