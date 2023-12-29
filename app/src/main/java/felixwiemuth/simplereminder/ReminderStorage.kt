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

package felixwiemuth.simplereminder

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import felixwiemuth.simplereminder.data.Reminder
import felixwiemuth.simplereminder.ui.reminderslist.RemindersListFragment
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
     * This is for complete top-level operations, which can be composed of partial operations working on the state prefs and a corresponding editor.
     *
     * @param operation The operation to perform on state prefs and a corresponding editor; the result of this operation is returned by this method
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
        return Reminder.fromJson(prefs.getString(Prefs.PREF_STATE_CURRENT_REMINDERS, "[]")!!)
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
            writeRemindersListInEditor(editor, reminders)
        }
    }

    private fun writeRemindersListInEditor(
        editor: SharedPreferences.Editor,
        reminders: List<Reminder>
    ) {
        editor.putString(Prefs.PREF_STATE_CURRENT_REMINDERS, Reminder.toJson(reminders))
    }

    private fun updateRemindersListInEditor(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor,
        operation: (MutableList<Reminder>) -> Unit
    ) {
        val reminders = getRemindersFromPrefs(prefs).toMutableList()
        operation(reminders)
        writeRemindersListInEditor(editor, reminders)
    }

    private fun requireReminderIDNotExists(reminders: Iterable<Reminder>, id: Int) =
        require(reminders.find { it.id == id } == null) { "Reminder with id $id already exists." }

    private fun addReminderInEditor(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor,
        reminder: Reminder
    ) {
        updateRemindersListInEditor(prefs, editor) {
            requireReminderIDNotExists(it, reminder.id)
            it.add(reminder)
        }
    }

    /**
     * Add the given reminder (with the given ID).
     *
     * @param context
     * @param reminder
     * @return the argument reminder
     * @throws IllegalArgumentException if a reminder with the same ID already exists
     */
    fun addReminder(context: Context, reminder: Reminder): Reminder {
        updateRemindersList(context) {
            requireReminderIDNotExists(it, reminder.id)
            it.add(reminder)
        }
        return reminder
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
            addReminderInEditor(prefs, editor, reminder)
            reminder
        }
    }

    /**
     * Remove a reminder with the same ID of the given one (if such exists) and add the given one.
     *
     * @param context
     * @param reminder
     */
    @JvmStatic
    fun updateReminder(context: Context, reminder: Reminder) {
        updateRemindersList(
            context
        ) {
            removeReminderWithSameId(it.iterator(), reminder)
            it.add(reminder)
        }
    }

    /**
     * Remove all reminders which have the ID of one of the given reminders and add all the given reminders.
     *
     * @param context
     * @param reminders
     */
    fun updateReminders(context: Context, reminders: Iterable<Reminder>) {
        updateRemindersList(
            context
        ) { currentReminders: MutableList<Reminder> ->
            removeRemindersWithSameId(currentReminders.iterator(), reminders)
            currentReminders.addAll(reminders)
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
    ): List<Reminder> {
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
        updateRemindersList(context) { removeRemindersById(it.iterator(), ids) }
    }

    /**
     * Removes the first occurrence of a reminder with the ID of the given reminder on the iterator.
     *
     * @param it
     * @param reminder
     */
    private fun removeReminderWithSameId(it: MutableIterator<Reminder>, reminder: Reminder) {
        for ((id) in it) {
            if (id == reminder.id) {
                it.remove()
                break
            }
        }
    }

    /**
     * From the first given reminders, remove all with an ID matching one of the second given reminders.
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
        removeRemindersById(it, idsToRemove)
    }

    /**
     * Removes all occurrence of reminders with the given IDs.
     *
     * @param it
     * @param ids
     */
    private fun removeRemindersById(it: MutableIterator<Reminder>, ids: Set<Int>) {
        it.forEach { (id) -> if (ids.contains(id)) it.remove() }
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