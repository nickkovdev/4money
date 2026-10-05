/* Copyright 2025 Oleg Koretsky

   This file is part of the 4Money,
   a budget tracking Android app.

   4Money is free software: you can redistribute it
   and/or modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation, either version 3 of the License,
   or (at your option) any later version.

   4Money is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
   See the GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with 4Money. If not, see <http://www.gnu.org/licenses/>.
*/

package ua.com.radiokot.money.inbox.sources.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * What the device knows about installed apps. Needs the launcher `<queries>` entry.
 */
interface AppInfoSource {

    /**
     * @return the app label, null if the app is not installed or not visible
     */
    fun getLabel(packageName: String): String?

    fun getIcon(packageName: String): Drawable?

    /**
     * @return apps with a launcher entry, sorted by label, this app excluded
     */
    fun getLaunchableApps(): List<AppInfo>
}

data class AppInfo(
    val packageName: String,
    val label: String,
)

class AndroidAppInfoSource(
    private val context: Context,
) : AppInfoSource {

    private val packageManager: PackageManager
        get() = context.packageManager

    override fun getLabel(packageName: String): String? =
        try {
            packageManager
                .getApplicationInfo(packageName, 0)
                .loadLabel(packageManager)
                .toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    override fun getIcon(packageName: String): Drawable? =
        try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    override fun getLaunchableApps(): List<AppInfo> =
        packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0,
            )
            .map { resolveInfo ->
                AppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                )
            }
            .filter { it.packageName != context.packageName }
            .distinctBy(AppInfo::packageName)
            .sortedBy { it.label.lowercase() }
}
