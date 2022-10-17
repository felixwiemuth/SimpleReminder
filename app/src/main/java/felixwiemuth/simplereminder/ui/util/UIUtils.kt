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
package felixwiemuth.simplereminder.ui.util

import android.content.Context
import android.content.DialogInterface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import felixwiemuth.simplereminder.R

/**
 * @author Felix Wiemuth
 */
object UIUtils {
    fun showMessageDialog(@StringRes resTitle: Int, @StringRes resMessage: Int, context: Context, onDismiss : DialogInterface.OnDismissListener? = null) {
        AlertDialog.Builder(context)
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _ -> dialog.dismiss() }
            .setTitle(resTitle)
            .setMessage(resMessage)
            .setOnDismissListener(onDismiss)
            .show()
    }

    fun makeAlertText(source: CharSequence?, context: Context): SpannableString {
        val color = context.resources.getColor(R.color.text_alert) // Deprecated, but same as getColor(R.color.text_alert, null) on newer APIs
        val spannableString = SpannableString(source)
        spannableString.setSpan(ForegroundColorSpan(color), 0, spannableString.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        return spannableString
    }

    fun makeAlertText(@StringRes resId: Int, context: Context): SpannableString = makeAlertText(context.getString(resId), context)
}