package io.github.jgoedde.pielauncher.platform

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo

/** One launcher activity of the main profile. A package with several launcher activities yields several. */
data class LauncherApp(
    val info: LauncherActivityInfo,
) {
    val componentName: ComponentName get() = info.componentName
    val packageName: String get() = info.componentName.packageName
    val label: String = info.label.toString()

    /** Preinstalled with the system image (also after updates); such apps cannot be uninstalled. */
    val isSystemApp: Boolean get() = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
}
