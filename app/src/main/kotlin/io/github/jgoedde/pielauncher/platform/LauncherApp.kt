package io.github.jgoedde.pielauncher.platform

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo

/** One launcher activity of the main profile. A package with several launcher activities yields several. */
data class LauncherApp(
    val info: LauncherActivityInfo,
) {
    val componentName: ComponentName get() = info.componentName
    val label: String = info.label.toString()
}
