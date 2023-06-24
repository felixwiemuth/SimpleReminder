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

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    /**
     * This receiver does not do anything, but the corresponding broadcasts are received to execute code in [Main.onCreate],
     * which is executed on every startup of the application.
     */
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
    }

    companion object {
        @JvmStatic
        fun isPermissionGranted(applicationContext: Context) =
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECEIVE_BOOT_COMPLETED
            ) == PackageManager.PERMISSION_GRANTED

        @JvmStatic
        fun setBootReceiverEnabled(context: Context, enabled: Boolean) {
            val receiver = ComponentName(context, BootReceiver::class.java)
            val newState: Int = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(receiver, newState, PackageManager.DONT_KILL_APP)
        }
    }
}