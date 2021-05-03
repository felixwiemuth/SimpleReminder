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

package felixwiemuth.simplereminder.ui.reminderslist;

import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import felixwiemuth.simplereminder.R;

/**
 * View holder for items where time and date is to be shown.
 */
public class FullDateItemViewHolder extends ItemViewHolder {
    final TextView dateView;

    public FullDateItemViewHolder(@NonNull View itemView) {
        super(R.layout.reminder_card_datefield_full_date, (CardView) itemView);
        dateView = itemView.findViewById(R.id.date);
    }
}
