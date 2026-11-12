package io.github.jgoedde.pielauncher.settings

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.home.PieLauncherTheme
import java.io.IOException

/**
 * Launcher Settings (PRD 10): its own `LAUNCHER` activity, so it is a Drawer Entry and can be a
 * Pie Item. One screen plus the licenses sub-page, switched by state (no navigation library).
 */
class SettingsActivity : ComponentActivity() {
    private val graph by lazy { (application as PieLauncherApplication).graph }

    private val viewModel: SettingsViewModel by viewModels {
        viewModelFactory {
            initializer {
                SettingsViewModel(
                    config = graph.pieConfigStore.config,
                    loaded = graph.pieConfigStore.loaded,
                    record = graph.importRecordStore.record,
                    apps = graph.appsRepository.apps,
                    resolver = graph.pieItemResolver,
                    importer = graph.pieConfigImporter,
                    exporter = graph.configExporter,
                )
            }
        }
    }

    private val roleManager by lazy { getSystemService(RoleManager::class.java) }

    private var isDefaultHome by mutableStateOf(true)

    private val pickConfig =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) }

    private val createConfig =
        registerForActivityResult(ActivityResultContracts.CreateDocument(JSON_TYPE)) { uri -> uri?.let(viewModel::exportConfig) }

    private val createInventory =
        registerForActivityResult(ActivityResultContracts.CreateDocument(JSON_TYPE)) { uri -> uri?.let(viewModel::exportInventory) }

    private val requestHome =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshDefaultHome() }

    private val notice: String by lazy {
        try {
            assets.open(NOTICE_ASSET).use { it.readBytes().decodeToString() }
        } catch (e: IOException) {
            Log.e(TAG, "NOTICE is not bundled", e)
            ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val versionInfo = packageManager.getPackageInfo(packageName, 0)
        setContent {
            PieLauncherTheme {
                var showLicenses by rememberSaveable { mutableStateOf(false) }
                var showStatus by rememberSaveable { mutableStateOf(false) }
                val status by viewModel.status.collectAsStateWithLifecycle()
                val unavailable by viewModel.unavailable.collectAsStateWithLifecycle()
                val importDialog by viewModel.importDialog.collectAsStateWithLifecycle()
                val resetDialog by viewModel.resetDialog.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.exportOutcomes.collect { outcome ->
                        val text =
                            when (outcome) {
                                ExportOutcome.ConfigExported -> R.string.export_config_done
                                ExportOutcome.InventoryExported -> R.string.export_inventory_done
                                ExportOutcome.Failed -> R.string.export_failed
                            }
                        Toast.makeText(this@SettingsActivity, text, Toast.LENGTH_SHORT).show()
                    }
                }

                if (showLicenses) {
                    BackHandler { showLicenses = false }
                    LicensesScreen(notice, onBack = { showLicenses = false })
                } else {
                    SettingsScreen(
                        SettingsState(isDefaultHome, status, versionInfo.versionName.orEmpty(), versionInfo.longVersionCode),
                        object : SettingsActions {
                            override fun requestDefaultHome() =
                                requestHome.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))

                            override fun showStatus() {
                                showStatus = true
                            }

                            override fun importConfig() = pickConfig.launch(JSON_TYPES)

                            override fun exportConfig() = createConfig.launch(CONFIG_FILE_NAME)

                            override fun exportInventory() = createInventory.launch(INVENTORY_FILE_NAME)

                            override fun resetConfig() = viewModel.askReset()

                            override fun openWallpaperStyle() =
                                start(Intent(Intent.ACTION_SET_WALLPAPER).putExtra(WALLPAPER_LAUNCH_SOURCE, LAUNCH_SOURCE_LAUNCHER))

                            override fun openGitHub() = start(Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri()))

                            override fun openLicenses() {
                                showLicenses = true
                            }
                        },
                    )
                }
                if (showStatus) StatusDialog(status, unavailable, onDismiss = { showStatus = false })
                ImportDialogHost(importDialog, onApply = viewModel::apply, onDismiss = viewModel::dismissImport)
                ResetDialogHost(
                    resetDialog,
                    onConfirm = viewModel::confirmReset,
                    onExportFirst = { createConfig.launch(CONFIG_FILE_NAME) },
                    onDismiss = viewModel::dismissReset,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDefaultHome()
    }

    private fun refreshDefaultHome() {
        isDefaultHome = roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Nothing handles $intent", e)
        }
    }

    private companion object {
        const val TAG = "SettingsActivity"
        const val NOTICE_ASSET = "NOTICE"
        const val GITHUB_URL = "https://github.com/jgoedde/pie-launcher-remake"

        const val JSON_TYPE = "application/json"
        const val CONFIG_FILE_NAME = "pie-config.json"
        const val INVENTORY_FILE_NAME = "pie-inventory.json"

        /** Some file managers label JSON as plain text or octet-stream. */
        val JSON_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

        /** What Launcher3 sends, so the picker opens on its customisation page. */
        const val WALLPAPER_LAUNCH_SOURCE = "com.android.wallpaper.LAUNCH_SOURCE"
        const val LAUNCH_SOURCE_LAUNCHER = "app_launched_launcher"
    }
}
