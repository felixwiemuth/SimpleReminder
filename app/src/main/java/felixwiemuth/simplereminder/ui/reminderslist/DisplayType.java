/*
 * Copyright (C) 2018-2023 Felix Wiemuth and contributors (see CONTRIBUTORS.md)
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

package felixwiemuth.simplereminder.ui.reminderslist;

/**
 * Ways of displaying the date of a reminder item.
 */
public enum DisplayType {
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
