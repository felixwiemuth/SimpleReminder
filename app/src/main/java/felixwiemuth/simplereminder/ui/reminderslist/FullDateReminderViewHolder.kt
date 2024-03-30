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
package felixwiemuth.simplereminder.ui.reminderslist

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import felixwiemuth.simplereminder.R
import felixwiemuth.simplereminder.util.DateTimeUtil
import java.util.*

/**
 * View holder for items where time and date is to be shown.
 */
class FullDateReminderViewHolder(itemView: View) :
    ReminderViewHolder(R.layout.reminder_card_datefield_full_date, (itemView as CardView)) {
    private val dateView: TextView = itemView.findViewById(R.id.date)
    override fun initializeDateView(date: Date, context: Context) {
        dateView.text = DateTimeUtil.formatDate(context, date)
    }
}