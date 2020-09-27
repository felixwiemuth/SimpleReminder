/*
 * Copyright (C) 2019 Felix Wiemuth
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

package felixwiemuth.simplereminder;

import android.app.Application;
import android.content.Context;
import androidx.preference.PreferenceManager;
import org.acra.ACRA;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraDialog;
import org.acra.annotation.AcraMailSender;

/**
 * @author Felix Wiemuth
 */
@AcraCore(buildConfigClass = BuildConfig.class)
@AcraMailSender(mailTo = "felixwiemuth@hotmail.de", reportAsFile = false)
@AcraDialog(
        resTitle = R.string.acra_title,
        resText = R.string.acra_prompt,
        resCommentPrompt = R.string.acra_comment_prompt)
public class Main extends Application {

    /**
     * The current (newest) version of storing reminders in {@link Prefs}.
     */
    public static int REMINDERS_LIST_FORMAT_VERSION = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        if (ACRA.isACRASenderServiceProcess()) { // If ACRA started the application for a crash report, do nothing
            return;
        }
        PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
        Prefs.getStoredRemindersListFormatVersion(this); // Initialize if not set

        Prefs.checkRescheduleOnBoot(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        ACRA.init(this);
    }
}
