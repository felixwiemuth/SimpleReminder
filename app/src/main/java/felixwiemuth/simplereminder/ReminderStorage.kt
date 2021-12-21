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

package felixwiemuth.simplereminder

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import felixwiemuth.simplereminder.data.Reminder
import felixwiemuth.simplereminder.ui.reminderslist.RemindersListFragment
import java.lang.RuntimeException
import java.util.HashSet
import java.util.concurrent.locks.ReentrantLock

/**
 * Handles the persistent reminder storage.
 * The operations in this object are thread-safe, i.e., reminder updates can be attempted
 * from multiple threads in parallel where a consistent storage is guaranteed.
 */
object ReminderStorage {
    class ReminderNotFoundException(message: String?) : RuntimeException(message)

    /**
     * Lock guarding the state preferences. This reference being null is equivalent to the lock not being acquired.
     * Note that this is necessary as Android can deinitialize static variables, though this should not happen
     * while a process of the app is active, and thus not while the lock is acquired.
     */
    private var prefStateLock: ReentrantLock? = null
    private fun lock() {
        if (prefStateLock == null) {
            prefStateLock = ReentrantLock()
        }
        prefStateLock!!.lock()
    }

    private fun unlock() {
        if (prefStateLock != null) {
            prefStateLock!!.unlock()
        }
    }

    /**
     * Edit the state preferences exclusively and commit after the operation has successfully completed. This ensures that different threads editing these preferences do not overwrite their changes. Also sends a [RemindersListFragment.BROADCAST_REMINDERS_UPDATED] broadcast to inform about a change. Only change reminders via this method.
     *
     * @param operation The operation to perform; the result of this operation is returned by this method
     */
    @SuppressLint("ApplySharedPref")
    private fun <T> performExclusivelyOnStatePrefsAndCommit(
        context: Context,
        operation: (SharedPreferences, SharedPreferences.Editor) -> T
    ): T {
        lock()
        return try {
            val prefs = Prefs.getStatePrefs(context)
            val editor = prefs.edit()
            val result = operation(prefs, editor)
            editor.commit()
            notifyRemindersChangedBroadcast(context)
            result
        } finally {
            unlock()
        }
    }

    /**
     * Returns an immutable list of the saved reminders.
     *
     * @param prefs
     * @return
     */
    private fun getRemindersFromPrefs(prefs: SharedPreferences): List<Reminder> {
        return Reminder.fromJson(prefs.getString(Prefs.PREF_STATE_CURRENT_REMINDERS, "[]"))
    }

    fun getReminders(context: Context): List<Reminder> {
        return getRemindersFromPrefs(Prefs.getStatePrefs(context))
    }

    /**
     * Get the reminder with the specified ID.
     *
     * @param context
     * @param id
     * @return
     * @throws ReminderNotFoundException if no reminder with the given ID exists
     */
    @JvmStatic
    @Throws(ReminderNotFoundException::class)
    fun getReminder(context: Context, id: Int): Reminder =
        getReminders(context).find { r -> r.id == id }
            ?: throw ReminderNotFoundException("Reminder with id $id does not exist.")

    private fun updateRemindersList(context: Context, operation: (MutableList<Reminder>) -> Unit) {
        performExclusivelyOnStatePrefsAndCommit(
            context
        ) { _, editor ->
            val reminders = getReminders(context).toMutableList()
            operation(reminders)
            updateRemindersListInEditor(editor, reminders)
        }
    }

    private fun updateRemindersListInEditor(
        editor: SharedPreferences.Editor,
        reminders: List<Reminder?>
    ) {
        editor.putString(Prefs.PREF_STATE_CURRENT_REMINDERS, Reminder.toJson(reminders))
    }

    /**
     * Add the given reminder (with the given ID).
     *
     * @param context
     * @param reminder
     * @return the argument reminder
     */
    fun addReminder(context: Context, reminder: Reminder): Reminder {
        return performExclusivelyOnStatePrefsAndCommit(
            context
        ) { prefs, editor ->
            addReminderToReminders(prefs, editor, reminder)
            reminder
        }
    }

    /**
     * Add the reminder described by the given builder. A new ID is assigned by this method.
     *
     * @param context
     * @param reminderBuilder
     * @return the resulting reminder
     */
    fun addReminder(context: Context, reminderBuilder: Reminder.Builder): Reminder {
        return performExclusivelyOnStatePrefsAndCommit(
            context
        ) { prefs, editor ->
            // Get next reminder ID
            val nextId = prefs.getInt(Prefs.PREF_STATE_NEXTID, 0)
            reminderBuilder.id = nextId
            val reminder = reminderBuilder.build()
            editor.putInt(Prefs.PREF_STATE_NEXTID, nextId + 2) // Reminder IDs may only be even
            addReminderToReminders(prefs, editor, reminder)
            reminder
        }
    }

    /**
     * Replaces the reminder which has the ID of the given reminder with the given reminder.
     * If no reminder with that ID exists, the reminder will be created.
     *
     * @param context
     * @param reminder
     */
    @JvmStatic
    fun updateReminder(context: Context, reminder: Reminder) {
        updateRemindersList(
            context
        ) { currentReminders: MutableList<Reminder> ->
            removeReminderWithSameId(currentReminders.iterator(), reminder)
            currentReminders.add(reminder)
        }
    }

    /**
     * For each given reminder, replaces the existing reminder with the ID of the given reminder with the given one.
     *
     * @param context
     * @param reminders
     */
    fun updateReminders(context: Context, reminders: Iterable<Reminder>) {
        updateRemindersList(
            context
        ) { currentReminders: MutableList<Reminder> ->
            removeRemindersWithSameId(currentReminders.iterator(), reminders)
            for (reminder in reminders) {
                currentReminders.add(reminder)
            }
        }
    }

    /**
     * Update the reminders with the given IDs with the given transformation.
     *
     * @param context
     * @param transformation
     * @param ids
     * @return the transformed reminders
     */
    fun updateReminders(
        context: Context,
        transformation: (Reminder) -> Unit,
        ids: Set<Int>
    ) : List<Reminder> {
        val updated = mutableListOf<Reminder>()
        updateRemindersList(context) { currentReminders: List<Reminder> ->
            for (reminder in currentReminders) {
                if (ids.contains(reminder.id)) {
                    transformation(reminder)
                    updated.add(reminder)
                }
            }
        }
        return updated
    }

    /**
     * Remove the reminders with the given IDs from the current reminders.
     *
     * @param context
     * @param ids
     */
    fun removeReminders(context: Context, ids: Set<Int>) {
        updateRemindersList(
            context
        ) { currentReminders: MutableList<Reminder> ->
            removeRemindersById(currentReminders.iterator(), ids)
        }
    }

    /**
     * Add the given reminder to the list of reminders. No reminder with the same ID must exist yet.
     *
     * @param prefs
     * @param editor
     * @param reminder
     */
    private fun addReminderToReminders(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor,
        reminder: Reminder
    ) {
        val reminders = getRemindersFromPrefs(prefs)
        for (r in reminders) {
            if (r.id == reminder.id) {
                throw RuntimeException("Cannot add reminder: reminder with id " + reminder.id + " already exists.")
            }
        }
        val remindersUpdated = reminders + reminder
        updateRemindersListInEditor(editor, remindersUpdated)
    }

    /**
     * Removes the first occurrence of a reminder with the ID of the given reminder on the iterator.
     *
     * @param it
     * @param reminder
     */
    private fun removeReminderWithSameId(it: MutableIterator<Reminder>, reminder: Reminder) {
        while (it.hasNext()) {
            val (id) = it.next()
            if (id == reminder.id) {
                it.remove()
                break
            }
        }
    }

    /**
     * Removes all occurrence of reminders with the IDs of the given reminders on the iterator.
     *
     * @param it
     * @param reminders
     */
    private fun removeRemindersWithSameId(
        it: MutableIterator<Reminder>,
        reminders: Iterable<Reminder>
    ) {
        val idsToRemove: MutableSet<Int> = HashSet()
        for ((id) in reminders) {
            idsToRemove.add(id)
        }
        while (it.hasNext()) {
            val (id) = it.next()
            if (idsToRemove.contains(id)) {
                it.remove()
            }
        }
    }

    /**
     * Removes all occurrence of reminders with the given IDs.
     *
     * @param it
     * @param ids
     */
    private fun removeRemindersById(it: MutableIterator<Reminder>, ids: Set<Int>) {
        while (it.hasNext()) {
            val (id) = it.next()
            if (ids.contains(id)) {
                it.remove()
            }
        }
    }

    /**
     * Set the [Prefs.setRemindersUpdated] flag and send a local broadcast indicating that the list of reminders changed.
     *
     * @param context
     */
    private fun notifyRemindersChangedBroadcast(context: Context) {
        Prefs.setRemindersUpdated(true, context)
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(RemindersListFragment.getRemindersUpdatedBroadcastIntent())
    }
}