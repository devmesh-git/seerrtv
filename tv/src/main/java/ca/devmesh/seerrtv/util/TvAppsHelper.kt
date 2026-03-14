package ca.devmesh.seerrtv.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import ca.devmesh.seerrtv.BuildConfig
import androidx.core.content.edit

private const val PREFS_NAME_APP_ROW = "app_row_order"
private const val KEY_ORDER = "package_order"
private const val ORDER_DELIMITER = "|"

/**
 * Represents an installed app that can be launched on Android TV (has a leanback launcher activity).
 */
data class TvAppInfo(
    val packageName: String,
    val label: CharSequence,
    val icon: Drawable?,
    val launchIntent: Intent
)

/**
 * Returns a list of installed apps that declare a leanback launcher activity,
 * suitable for showing in a launcher's "Apps" row. Excludes the current app.
 * Results are sorted by label.
 */
@SuppressLint("QueryPermissionsNeeded")
fun getInstalledTvApps(context: Context): List<TvAppInfo> {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    }
    val resolveList = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
    val ourPackage = context.packageName
    val result = resolveList
        .mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val pkg = activityInfo.packageName
            if (pkg == ourPackage) return@mapNotNull null
            val label = resolveInfo.loadLabel(pm).toString()
            val icon = try {
                resolveInfo.loadIcon(pm)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w("TvAppsHelper", "No icon for $pkg", e)
                null
            }
            val launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    setClassName(pkg, activityInfo.name)
                    addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                }
            TvAppInfo(
                packageName = pkg,
                label = label,
                icon = icon,
                launchIntent = launchIntent
            )
        }
        .sortedBy { it.label.toString().lowercase() }
    if (BuildConfig.DEBUG) {
        Log.d("TvAppsHelper", "Found ${result.size} TV apps (excluding $ourPackage)")
    }
    return result
}

/**
 * Load saved app row order (list of package names in order). Returns null if none saved.
 * Order is preserved by storing a delimited string.
 */
fun loadAppRowOrder(context: Context): List<String>? {
    val prefs = context.getSharedPreferences(PREFS_NAME_APP_ROW, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_ORDER, null) ?: return null
    val list = raw.split(ORDER_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
    return list.ifEmpty { null }
}

/**
 * Save app row order (list of package names). Order is preserved using a delimited string.
 */
fun saveAppRowOrder(context: Context, packageNames: List<String>) {
    val value = packageNames.joinToString(ORDER_DELIMITER)
    context.getSharedPreferences(PREFS_NAME_APP_ROW, Context.MODE_PRIVATE)
        .edit {
            putString(KEY_ORDER, value)
        }
}

/**
 * Apply saved order to a full list: packages in [savedOrder] appear first in that order,
 * then any from [fullList] not in saved order (appended in their current order).
 * Uninstalled packages in [savedOrder] are skipped (no crash); they are dropped from
 * the effective order until the user saves again.
 */
fun applyAppRowOrder(fullList: List<TvAppInfo>, savedOrder: List<String>?): List<TvAppInfo> {
    if (savedOrder.isNullOrEmpty()) return fullList
    val byPackage = fullList.associateBy { it.packageName }
    val ordered = mutableListOf<TvAppInfo>()
    for (pkg in savedOrder) {
        byPackage[pkg]?.let { ordered.add(it) }
    }
    for (app in fullList) {
        if (app.packageName !in savedOrder) ordered.add(app)
    }
    return ordered
}

/**
 * Returns the installed TV app list with saved order applied. If the effective order
 * changed (e.g. some saved packages were uninstalled, or new apps were installed),
 * re-saves the order so persisted state stays in sync.
 */
fun getInstalledTvAppsWithSavedOrder(context: Context): List<TvAppInfo> {
    val full = getInstalledTvApps(context)
    val saved = loadAppRowOrder(context)
    val ordered = applyAppRowOrder(full, saved)
    val fullPkgs = full.map { it.packageName }.toSet()
    val savedSet = saved?.toSet().orEmpty()
    val hadUninstalled = savedSet.any { it !in fullPkgs }
    val hadNewApps = fullPkgs.any { it !in savedSet }
    if (hadUninstalled || hadNewApps) {
        saveAppRowOrder(context, ordered.map { it.packageName })
    }
    return ordered
}
