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

package felixwiemuth.simplereminder.ui.actions;

import android.content.Context;

import java.util.List;

import felixwiemuth.simplereminder.Main;
import felixwiemuth.simplereminder.ui.util.HtmlDialogFragment;

/**
 * Displays the update welcome message.
 */
public class DisplayWelcomeMessageUpdate implements HtmlDialogFragment.Action {
    @Override
    public String getName() {
        return "display_welcome_message_update";
    }

    @Override
    public void run(List<String> args, Context context) {
        Main.showWelcomeMessageUpdate(context);
    }
}
