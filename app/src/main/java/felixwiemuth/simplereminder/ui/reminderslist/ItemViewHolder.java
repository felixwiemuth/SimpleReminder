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

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewStub;
import android.widget.TextView;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import felixwiemuth.simplereminder.R;

/**
 * The base item view holder.
 */
public class ItemViewHolder extends RecyclerView.ViewHolder {
    final CardView itemView;
    final View datefieldView;
    private final ColorStateList cardBackgroundColor;
    final TextView timeView;
    final TextView descriptionView;

    ItemViewHolder(@LayoutRes int datefieldRes, @NonNull CardView itemView) {
        super(itemView);
        this.itemView = itemView;
        this.descriptionView = itemView.findViewById(R.id.description);
        cardBackgroundColor = itemView.getCardBackgroundColor();
        ViewStub datefieldViewStub = itemView.findViewById(R.id.datefield_stub);
        datefieldViewStub.setLayoutResource(datefieldRes);
        this.datefieldView = datefieldViewStub.inflate();
        this.timeView = itemView.findViewById(R.id.time);
    }

    boolean isSelected() {
        return itemView.isSelected();
    }

    void setSelected(Context context) {
        itemView.setSelected(true);
        itemView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.bg_selected));
    }

    void setUnselected() {
        if (isSelected()) {
            itemView.setSelected(false);
            itemView.setCardBackgroundColor(cardBackgroundColor);
        }
    }
}
