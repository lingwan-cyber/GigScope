package com.gigscope.auditor.ui

import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.gigscope.auditor.data.local.GigScopeConfig
import com.gigscope.auditor.domain.model.*
import com.gigscope.auditor.domain.reconciliation.TipReconciliationEngine
import com.gigscope.auditor.service.ExecutionTarget
import com.gigscope.auditor.service.GigScopeAccessibilityService
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val reconciliationEngine = TipReconciliationEngine()

    private val sparkTrips = mutableStateListOf<SparkCompletedTrip>()
    private val sparkEarnings = mutableStateListOf<SparkEarningsBreakdown>()
    private val onePayDeposits = mutableStateListOf<OnePayDeposit>()
    private val photoOffers = mutableStateListOf<PhotoOfferRecord>()
    private val discrepancies = mutableStateListOf<CustomerTipDiscrepancy>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("gigscope_preferences", MODE_PRIVATE)
        val initialJson = prefs.getString("saved_config_json", null)
        val initialConfig = if (!initialJson.isNullOrBlank()) {
            try { GigScopeConfig.fromJson(initialJson) } catch (e: Exception) { GigScopeConfig() }
        } else {
            GigScopeConfig()
        }
        GigScopeAccessibilityService.activeRecipeMap.putAll(initialConfig.recipes)

        setContent {
            var startDate by remember { mutableStateOf(initialConfig.startDate) }
            var endDate by remember { mutableStateOf(initialConfig.endDate) }

            var sparkStatus by remember { mutableStateOf("Ready (0 trips)") }
            var onePayStatus by remember { mutableStateOf("Ready (0 deposits)") }
            var photosStatus by remember { mutableStateOf("Ready (0 offers)") }

            var sparkApkPath by remember { mutableStateOf(initialConfig.sparkApkPath) }
            var onePayApkPath by remember { mutableStateOf(initialConfig.onePayApkPath) }
            var photosApkPath by remember { mutableStateOf(initialConfig.photosApkPath) }

            val appRecipes = remember {
                mutableStateMapOf<String, AppRecipe>().apply {
                    putAll(initialConfig.recipes)
                }
            }

            fun saveAutoPrefs() {
                val currentConfig = GigScopeConfig(
                    startDate = startDate,
                    endDate = endDate,
                    sparkApkPath = sparkApkPath,
                    onePayApkPath = onePayApkPath,
                    photosApkPath = photosApkPath,
                    recipes = appRecipes.toMap()
                )
                prefs.edit().putString("saved_config_json", currentConfig.toJson()).apply()
            }

            val isAccessibilityEnabled = GigScopeAccessibilityService.instance != null

            // Save Config Launcher (JSON)
            val saveConfigLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) {
                    try {
                        val config = GigScopeConfig(
                            startDate = startDate,
                            endDate = endDate,
                            sparkApkPath = sparkApkPath,
                            onePayApkPath = onePayApkPath,
                            photosApkPath = photosApkPath,
                            recipes = appRecipes.toMap()
                        )
                        contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(config.toJson().toByteArray(Charsets.UTF_8))
                        }
                        Toast.makeText(this@MainActivity, "Configuration saved successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Error saving config: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            // Load Config Launcher (JSON)
            val loadConfigLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    try {
                        var fileName: String? = null
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                fileName = cursor.getString(nameIndex)
                            }
                        }
                        val content = contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).readText()
                        }
                        if (content != null) {
                            val loaded = GigScopeConfig.fromJson(content)
                            startDate = loaded.startDate
                            endDate = loaded.endDate
                            sparkApkPath = loaded.sparkApkPath
                            onePayApkPath = loaded.onePayApkPath
                            photosApkPath = loaded.photosApkPath
                            appRecipes.clear()
                            appRecipes.putAll(loaded.recipes)
                            GigScopeAccessibilityService.activeRecipeMap.clear()
                            GigScopeAccessibilityService.activeRecipeMap.putAll(loaded.recipes)
                            saveAutoPrefs()
                            val label = fileName ?: "Configuration"
                            Toast.makeText(this@MainActivity, "$label loaded successfully", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Error loading config: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            // Connect callbacks from AccessibilityService
            LaunchedEffect(Unit) {
                GigScopeAccessibilityService.onSparkDataCollected = { trips, earnings ->
                    runOnUiThread {
                        sparkTrips.clear()
                        sparkTrips.addAll(trips)
                        sparkEarnings.clear()
                        sparkEarnings.addAll(earnings)
                        sparkStatus = "Collected ${trips.size} trips, ${earnings.size} earnings"
                        Toast.makeText(this@MainActivity, "Spark Data Collected!", Toast.LENGTH_SHORT).show()
                    }
                }
                GigScopeAccessibilityService.onOnePayDataCollected = { deposits ->
                    runOnUiThread {
                        onePayDeposits.clear()
                        onePayDeposits.addAll(deposits)
                        onePayStatus = "Collected ${deposits.size} deposits"
                        Toast.makeText(this@MainActivity, "OnePay Data Collected!", Toast.LENGTH_SHORT).show()
                    }
                }
                GigScopeAccessibilityService.onPhotosDataCollected = { offers ->
                    runOnUiThread {
                        photoOffers.clear()
                        photoOffers.addAll(offers)
                        photosStatus = "Collected ${offers.size} screenshot cards"
                        Toast.makeText(this@MainActivity, "Photos Data Collected!", Toast.LENGTH_SHORT).show()
                    }
                }
                GigScopeAccessibilityService.onRecipeRecorded = { recipe ->
                    runOnUiThread {
                        appRecipes[recipe.packageName] = recipe
                        saveAutoPrefs()
                        Toast.makeText(this@MainActivity, "Learned ${recipe.phases.size} phases for ${recipe.appName}!", Toast.LENGTH_LONG).show()
                    }
                }
            }

            DashboardScreen(
                startDate = startDate,
                endDate = endDate,
                onStartDateChange = {
                    startDate = it
                    saveAutoPrefs()
                },
                onEndDateChange = {
                    endDate = it
                    saveAutoPrefs()
                },
                sparkStatus = sparkStatus,
                onePayStatus = onePayStatus,
                photosStatus = photosStatus,
                sparkApkPath = sparkApkPath,
                onePayApkPath = onePayApkPath,
                photosApkPath = photosApkPath,
                onSparkApkPathChange = {
                    sparkApkPath = it
                    saveAutoPrefs()
                    if (it.isNotEmpty()) {
                        Toast.makeText(this@MainActivity, "Applied Spark APK: $it", Toast.LENGTH_SHORT).show()
                    }
                },
                onOnePayApkPathChange = {
                    onePayApkPath = it
                    saveAutoPrefs()
                    if (it.isNotEmpty()) {
                        Toast.makeText(this@MainActivity, "Applied OnePay APK: $it", Toast.LENGTH_SHORT).show()
                    }
                },
                onPhotosApkPathChange = {
                    photosApkPath = it
                    saveAutoPrefs()
                    if (it.isNotEmpty()) {
                        Toast.makeText(this@MainActivity, "Applied Photos APK: $it", Toast.LENGTH_SHORT).show()
                    }
                },
                onLoadConfig = { loadConfigLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onSaveConfig = {
                    saveAutoPrefs()
                    saveConfigLauncher.launch("gigscope_config.json")
                },
                onTestSpark = { launchAppForTest("com.walmart.sparkdriver", sparkApkPath, ExecutionTarget.TEST_SPARK) },
                onTestOnePay = { launchAppForTest("com.onefinance.one", onePayApkPath, ExecutionTarget.TEST_ONEPAY) },
                onTestPhotos = { launchAppForTest("com.google.android.apps.photos", photosApkPath, ExecutionTarget.TEST_PHOTOS) },
                onTeachSpark = { launchAppForRecord("com.walmart.sparkdriver", sparkApkPath, "Spark Driver", listOf("trips", "earnings")) },
                onTeachOnePay = { launchAppForRecord("com.onefinance.one", onePayApkPath, "OnePay", listOf("activity")) },
                onTeachPhotos = { launchAppForRecord("com.google.android.apps.photos", photosApkPath, "Google Photos", listOf("screenshots")) },
                hasSparkRecipe = appRecipes.containsKey("com.walmart.sparkdriver"),
                hasOnePayRecipe = appRecipes.containsKey("com.onefinance.one"),
                hasPhotosRecipe = appRecipes.containsKey("com.google.android.apps.photos"),
                onResetSparkRecipe = {
                    appRecipes.remove("com.walmart.sparkdriver")
                    GigScopeAccessibilityService.activeRecipeMap.remove("com.walmart.sparkdriver")
                    saveAutoPrefs()
                    Toast.makeText(this@MainActivity, "Reset Spark to default semantic traversal", Toast.LENGTH_SHORT).show()
                },
                onResetOnePayRecipe = {
                    appRecipes.remove("com.onefinance.one")
                    GigScopeAccessibilityService.activeRecipeMap.remove("com.onefinance.one")
                    saveAutoPrefs()
                    Toast.makeText(this@MainActivity, "Reset OnePay to default semantic traversal", Toast.LENGTH_SHORT).show()
                },
                onResetPhotosRecipe = {
                    appRecipes.remove("com.google.android.apps.photos")
                    GigScopeAccessibilityService.activeRecipeMap.remove("com.google.android.apps.photos")
                    saveAutoPrefs()
                    Toast.makeText(this@MainActivity, "Reset Photos to default semantic traversal", Toast.LENGTH_SHORT).show()
                },
                onRunFullAudit = {
                    val results = reconciliationEngine.reconcile(
                        startDate = startDate,
                        endDate = endDate,
                        completedTrips = sparkTrips,
                        earningsBreakdowns = sparkEarnings,
                        onePayDeposits = onePayDeposits,
                        screenshotOffers = photoOffers
                    )
                    discrepancies.clear()
                    discrepancies.addAll(results)
                    Toast.makeText(
                        this@MainActivity,
                        "Audit Complete: ${results.size} reduced tips found",
                        Toast.LENGTH_LONG
                    ).show()
                },
                discrepancies = discrepancies,
                isAccessibilityEnabled = isAccessibilityEnabled,
                onOpenAccessibilitySettings = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
            )
        }
    }

    private fun launchAppForRecord(defaultPackage: String, customApkPath: String, appName: String, phases: List<String>) {
        if (GigScopeAccessibilityService.instance == null) {
            Toast.makeText(this, "Please enable GigScope Accessibility Service first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow 'Display over other apps' to use the recording HUD", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        val targetPackage = resolveTargetPackage(defaultPackage, customApkPath)

        // Launch Floating HUD in Record Mode
        com.gigscope.auditor.service.FloatingHudService.start(this, com.gigscope.auditor.service.FloatingHudService.MODE_RECORD)
        GigScopeAccessibilityService.startRecording(targetPackage, appName, phases)

        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "Target app ($targetPackage) not installed on device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchAppForTest(defaultPackage: String, customApkPath: String, target: ExecutionTarget) {
        if (GigScopeAccessibilityService.instance == null) {
            Toast.makeText(this, "Please enable GigScope Accessibility Service first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        GigScopeAccessibilityService.activeTarget = target

        val targetPackage = resolveTargetPackage(defaultPackage, customApkPath)

        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "Target app ($targetPackage) not installed on device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun resolveTargetPackage(defaultPackage: String, customApkPath: String): String {
        var targetPackage = defaultPackage
        if (customApkPath.isNotBlank()) {
            val parenRegex = Regex("""\(([\w.]+)\)""")
            val match = parenRegex.find(customApkPath)
            if (match != null) {
                targetPackage = match.groupValues[1]
            } else {
                try {
                    val archiveInfo = packageManager.getPackageArchiveInfo(customApkPath, 0)
                    if (archiveInfo != null) {
                        targetPackage = archiveInfo.packageName
                    } else if (customApkPath.contains(".") && !customApkPath.contains("/")) {
                        targetPackage = customApkPath.trim()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return targetPackage
    }
}
