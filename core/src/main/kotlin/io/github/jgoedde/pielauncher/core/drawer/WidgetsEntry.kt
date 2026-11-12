package io.github.jgoedde.pielauncher.core.drawer

/**
 * Whether the App Drawer long-press menu of the app [packageName] offers "Widgets": only when the
 * Widget Picker would list at least one of its widgets, so the entry never opens an empty picker.
 * [pickerProviderPackages] are the packages of every provider the Widget Picker lists.
 */
fun offersWidgets(
    packageName: String,
    pickerProviderPackages: Iterable<String>,
): Boolean = pickerProviderPackages.any { it == packageName }
