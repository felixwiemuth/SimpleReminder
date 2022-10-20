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
package felixwiemuth.simplereminder.data

import felixwiemuth.simplereminder.data.Reminder.Companion.MAX_REMINDER_ID
import felixwiemuth.simplereminder.util.DateSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

@Serializable
data class Reminder
constructor(
    /**
     * ID of the reminder, also used for notifications. Must be in the range 0..[MAX_REMINDER_ID] and even.
     */
    val id: Int,

    /**
     * Reminder's due date.
     */
    @Serializable(with = DateSerializer::class)
    val date: Date,

    /**
     * The interval in minutes with which this reminder should be repeated until dismissed.
     * This field is optional. A value <= 0 (or omitting in JSON) means that nagging is disabled, which is the default.
     * @since 0.9.9
     */
    val naggingRepeatInterval: Int = 0,

    val text: String = "",

    var status: Status = Status.SCHEDULED
) : Comparable<Reminder> {
    /**
     * Status of saved reminders.
     */
    enum class Status {
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

    init {
        require(id in 0..MAX_REMINDER_ID && id % 2 == 0) { "Id must be even, >= 0 and <= $MAX_REMINDER_ID." }
    }

    /**
     * Get a new [Calendar] instance set to the reminder's date.
     */
    val calendar: Calendar
        get() {
            val c = Calendar.getInstance()
            c.time = date
            return c
        }

    override fun compareTo(other: Reminder): Int {
        return date.compareTo(other.date)
    }

    val isNagging: Boolean
        get() = naggingRepeatInterval > 0
    val naggingRepeatIntervalInMillis: Long
        get() = (60 * 1000 * naggingRepeatInterval).toLong()

    companion object {
        const val MAX_REMINDER_ID = 1000000

        @JvmStatic
        fun builder(date: Date, text: String): Builder = Builder(date = date, text = text)

        @JvmStatic
        fun toJson(reminders: List<Reminder?>?): String =
            Json.encodeToString(reminders)

        @JvmStatic
        fun fromJson(json: String): List<Reminder> =
            Json.decodeFromString(ListSerializer(serializer()), json)
    }

    /**
     * Used to construct a [Reminder] step by step. This allows the ID for a new reminder to be first created when adding the reminder to the storage.
     */
    data class Builder
    @JvmOverloads
    constructor(
        @JvmField
        var id: Int? = null,
        @JvmField
        val date: Date,
        @JvmField
        var naggingRepeatInterval: Int = 0,
        @JvmField
        val text: String = "",
        @JvmField
        var status: Status = Status.SCHEDULED
    ) {
        fun build() = Reminder(requireNotNull(id), date, naggingRepeatInterval, text, status)
    }
}