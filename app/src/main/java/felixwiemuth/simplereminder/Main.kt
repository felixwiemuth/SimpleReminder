/*
 * Copyright (C) 2018-2024 Felix Wiemuth and contributors (see CONTRIBUTORS.md)
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

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import felixwiemuth.simplereminder.ReminderManager.createNotificationChannel
import felixwiemuth.simplereminder.ui.util.UIUtils
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.ktx.initAcra

class Main : Application() {
    // Note: This is run before any app component starts, i.e., also when starting the app via "Add reminder" or the service.
    override fun onCreate() {
        super.onCreate()

        PreferenceManager.setDefaultValues(this, R.xml.preferences, true)
        Prefs.getStoredRemindersListFormatVersion(this) // Initialize if not set
        createNotificationChannel(this)

        // Reschedule reminders and show due reminders on app startup. This ensures that reminders are scheduled and re-shown
        // automatically after reboot (if this is enabled in settings) and when starting the app again after a force-close which cancels
        // AlarmManager alarms and notifications.
        // This might also be called in situations where it is not necessary, for example after the system or user killed the app process
        // without cancelling notifications and alarms. However, there is no handy way of detecting whether this is the case.
        ReminderManager.scheduleAndReshowAllReminders(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        initAcra {
            buildConfigClass = BuildConfig::class.java
            mailSender {
                mailTo = "felixwiemuth@hotmail.de"
                reportAsFile = false // The report being the content of the mail makes it more obvious to the user what data is sent.
            }
            dialog {
                title = getString(R.string.acra_title)
                text = getString(R.string.acra_prompt)
                commentPrompt = getString(R.string.acra_comment_prompt)
            }
        }
    }

    companion object {
        /**
         * The current (newest) version of storing reminders in [Prefs].
         */
        @JvmField
        var REMINDERS_LIST_FORMAT_VERSION = 1

        @JvmStatic
        fun showWelcomeMessage(context: Context) {
            UIUtils.showMessageDialog(R.string.dialog_welcome_title, R.string.welcome_message, context)
        }

        @JvmStatic
        fun showWelcomeMessageUpdate(context: Context) {
            UIUtils.showMessageDialog(R.string.dialog_welcome_title, R.string.welcome_message_update, context)
        }
    }
}