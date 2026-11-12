package io.github.jgoedde.pielauncher.platform

import android.content.Intent

/** The system wallpaper picker ("Wallpaper & style"), opened the way Launcher3 opens it. */
fun wallpaperPickerIntent(): Intent = Intent(Intent.ACTION_SET_WALLPAPER).putExtra(WALLPAPER_LAUNCH_SOURCE, LAUNCH_SOURCE_LAUNCHER)

/** What Launcher3 sends, so the picker opens on its customisation page. */
private const val WALLPAPER_LAUNCH_SOURCE = "com.android.wallpaper.LAUNCH_SOURCE"
private const val LAUNCH_SOURCE_LAUNCHER = "app_launched_launcher"
