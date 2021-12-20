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
package felixwiemuth.simplereminder.data

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
     * ID of the reminder, also used for notifications. Must be in the interval [0,[OFFSET_REQUEST_CODE_ADD_REMINDER_DIALOG_ACTIVITY_PENDING_INTENT]) and even (used by [felixwiemuth.simplereminder.ReminderManager] for correct scheduling).
     */
    val id: Int,

    /**
     * Reminder's due date.
     */
    @Serializable(with = DateSerializer::class)
    val date: Date,

    /**
     * The interval in minutes this reminder should be repeated until dismissed.
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
        require(id >= 0) { "Id must be >= 0." }
    }

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
        const val OFFSET_REQUEST_CODE_ADD_REMINDER_DIALOG_ACTIVITY_PENDING_INTENT = 1000000

        @JvmStatic
        fun builder(date: Date, text: String): Builder = Builder(date = date, text = text)

        @JvmStatic
        fun toJson(reminders: List<Reminder?>?): String =
            Json.encodeToString(reminders)

        @JvmStatic
        fun fromJson(json: String): List<Reminder> =
            Json.decodeFromString(ListSerializer(serializer()), json)

        /**
         * Request code for a pending intent to be used to start [felixwiemuth.simplereminder.ui.EditReminderDialogActivity].
         */
        @JvmStatic
        fun getRequestCodeEditReminderDialogActivityPendingIntent(reminderID: Int): Int {
            return OFFSET_REQUEST_CODE_ADD_REMINDER_DIALOG_ACTIVITY_PENDING_INTENT + reminderID
        }
    }

    /**
     * Used to construct a [Reminder] step by step. Necessary as the ID for a new reminder is first created when adding the reminder to the storage.
     */
    data class Builder
    @JvmOverloads
    constructor(
        @kotlin.jvm.JvmField
        var id: Int? = null,
        @kotlin.jvm.JvmField
        val date: Date,
        @kotlin.jvm.JvmField
        var naggingRepeatInterval: Int = 0,
        @kotlin.jvm.JvmField
        val text: String = "",
        @kotlin.jvm.JvmField
        var status: Status = Status.SCHEDULED
    ) {
        fun build() = Reminder(requireNotNull(id), date, naggingRepeatInterval, text, status)
    }
}