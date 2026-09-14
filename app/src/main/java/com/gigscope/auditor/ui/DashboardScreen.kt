package com.gigscope.auditor.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigscope.auditor.BuildConfig
import com.gigscope.auditor.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class AppDrawerItem(
    val appName: String,
    val packageName: String,
    val apkPath: String,
    val icon: ImageBitmap?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    startDate: LocalDate,
    endDate: LocalDate,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    automationSpeed: String = "Normal",
    onAutomationSpeedChange: (String) -> Unit = {},
    stepByStepMode: Boolean = false,
    onStepByStepModeChange: (Boolean) -> Unit = {},
    sparkStatus: String,
    onePayStatus: String,
    photosStatus: String,
    sparkApkPath: String,
    onePayApkPath: String,
    photosApkPath: String,
    onSparkApkPathChange: (String) -> Unit,
    onOnePayApkPathChange: (String) -> Unit,
    onPhotosApkPathChange: (String) -> Unit,
    onLoadConfig: () -> Unit,
    onSaveConfig: () -> Unit,
    onTestSpark: () -> Unit,
    onTestOnePay: () -> Unit,
    onTestPhotos: () -> Unit,
    onTeachSpark: () -> Unit = {},
    onTeachOnePay: () -> Unit = {},
    onTeachPhotos: () -> Unit = {},
    hasSparkRecipe: Boolean = false,
    hasOnePayRecipe: Boolean = false,
    hasPhotosRecipe: Boolean = false,
    onResetSparkRecipe: () -> Unit = {},
    onResetOnePayRecipe: () -> Unit = {},
    onResetPhotosRecipe: () -> Unit = {},
    onRunFullAudit: () -> Unit,
    discrepancies: List<CustomerTipDiscrepancy>,
    isAccessibilityEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    sparkTrips: List<SparkCompletedTrip> = emptyList(),
    sparkEarnings: List<SparkEarningsBreakdown> = emptyList(),
    onePayDeposits: List<OnePayDeposit> = emptyList(),
    photoOffers: List<PhotoOfferRecord> = emptyList(),
    onFindSparkTrips: () -> Unit = {},
    onFindSparkEarnings: () -> Unit = {},
    onSaveSparkResults: () -> Unit = {},
    onFindOnePayTripEarnings: () -> Unit = {},
    onFindOnePayTipDeposits: () -> Unit = {},
    onSaveOnePayResults: () -> Unit = {},
    onFindAndExtractScreenshots: () -> Unit = {},
    onSavePhotosResults: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var showConfigMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    var showSparkTripsDialog by remember { mutableStateOf(false) }
    var showSparkEarningsDialog by remember { mutableStateOf(false) }
    var showOnePayTripEarningsDialog by remember { mutableStateOf(false) }
    var showOnePayTipDepositsDialog by remember { mutableStateOf(false) }
    var showPhotosDialog by remember { mutableStateOf(false) }

    if (showSparkTripsDialog) {
        SparkTripsDialog(trips = sparkTrips, onDismiss = { showSparkTripsDialog = false })
    }
    if (showSparkEarningsDialog) {
        SparkEarningsDialog(earnings = sparkEarnings, onDismiss = { showSparkEarningsDialog = false })
    }
    if (showOnePayTripEarningsDialog) {
        OnePayDepositsDialog(
            title = "OnePay Trip Earnings",
            subtitle = "Direct trip earnings & delivery payments with exact timestamps",
            deposits = onePayDeposits.filter { it.transactionType == OnePayTransactionType.TRIP_EARNING },
            onDismiss = { showOnePayTripEarningsDialog = false }
        )
    }
    if (showOnePayTipDepositsDialog) {
        OnePayDepositsDialog(
            title = "OnePay Tip Deposits",
            subtitle = "Customer tip deposits (credited ~24h after delivery)",
            deposits = onePayDeposits.filter { it.transactionType == OnePayTransactionType.TIP_DEPOSIT },
            onDismiss = { showOnePayTipDepositsDialog = false }
        )
    }
    if (showPhotosDialog) {
        PhotosOffersDialog(offers = photoOffers, onDismiss = { showPhotosDialog = false })
    }

    if (showAboutDialog) {
        AboutDialog(onDismissRequest = { showAboutDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "GigScope Auditor",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { showConfigMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showConfigMenu,
                            onDismissRequest = { showConfigMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Load Config") },
                                onClick = {
                                    showConfigMenu = false
                                    onLoadConfig()
                                },
                                leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Save Config") },
                                onClick = {
                                    showConfigMenu = false
                                    onSaveConfig()
                                },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    showConfigMenu = false
                                    showAboutDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                if (!isAccessibilityEnabled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "Alert", tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Accessibility Service Not Enabled",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Enable GigScope in Settings to inspect UI view trees in Spark, OnePay, and Photos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onOpenAccessibilitySettings) {
                                Text("Enable in Settings")
                            }
                        }
                    }
                }
            }

            // Date Range Selector Card
            item {
                DateRangeCard(
                    startDate = startDate,
                    endDate = endDate,
                    onStartDateChange = onStartDateChange,
                    onEndDateChange = onEndDateChange,
                    onSelectPreset = { days ->
                        onStartDateChange(LocalDate.now().minusDays(days.toLong()))
                        onEndDateChange(LocalDate.now())
                    }
                )
            }

            // Automation Speed & Execution Control Card
            item {
                AutomationSettingsCard(
                    automationSpeed = automationSpeed,
                    onAutomationSpeedChange = onAutomationSpeedChange,
                    stepByStepMode = stepByStepMode,
                    onStepByStepModeChange = onStepByStepModeChange
                )
            }

            // Vertical 3-App Listing
            item {
                Text(
                    "Target Applications & Isolated Test Harnesses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // App 1: Spark Driver
            item {
                AppCard(
                    title = "1. Spark Driver",
                    defaultPackage = "com.walmart.sparkdriver",
                    targetDescription = "Target: 'Trips' (history, stops & customer info) & 'Earnings' (breakdowns & tips)",
                    status = sparkStatus,
                    apkPath = sparkApkPath,
                    hasCustomRecipe = hasSparkRecipe,
                    onApplyApkPath = onSparkApkPathChange,
                    buttonText = "Test Spark Traversal",
                    onTestClick = onTestSpark,
                    onTeachClick = onTeachSpark,
                    onResetRecipeClick = onResetSparkRecipe,
                    extraContent = {
                        // Discovery Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onFindSparkTrips,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Find Trips", fontSize = 12.sp)
                            }
                            Button(
                                onClick = onFindSparkEarnings,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Find Earnings", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // View Extracted Data Dialog Launchers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showSparkTripsDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("View Trips (${sparkTrips.size})", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { showSparkEarningsDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("View Earnings (${sparkEarnings.size})", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // Save Spark Results Button
                        Button(
                            onClick = onSaveSparkResults,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Spark Results (${sparkTrips.size} trips, ${sparkEarnings.size} earnings)")
                        }
                    }
                )
            }

            // App 2: OnePay
            item {
                val tripEarningsCount = onePayDeposits.count { it.transactionType == OnePayTransactionType.TRIP_EARNING }
                val tipDepositsCount = onePayDeposits.count { it.transactionType == OnePayTransactionType.TIP_DEPOSIT }

                AppCard(
                    title = "2. OnePay (One Finance)",
                    defaultPackage = "com.onefinance.one",
                    targetDescription = "Target: 'Checking' Activity -> Trip Earnings & Tip Deposits with timestamps",
                    status = onePayStatus,
                    apkPath = onePayApkPath,
                    hasCustomRecipe = hasOnePayRecipe,
                    onApplyApkPath = onOnePayApkPathChange,
                    buttonText = "Test OnePay Traversal",
                    onTestClick = onTestOnePay,
                    onTeachClick = onTeachOnePay,
                    onResetRecipeClick = onResetOnePayRecipe,
                    extraContent = {
                        // Discovery Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onFindOnePayTripEarnings,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Find Trip Earnings", fontSize = 11.sp)
                            }
                            Button(
                                onClick = onFindOnePayTipDeposits,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Find Tip Deposits", fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // View Extracted Data Dialog Launchers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showOnePayTripEarningsDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Trip Earnings ($tripEarningsCount)", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { showOnePayTipDepositsDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Tip Deposits ($tipDepositsCount)", fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // Save OnePay Results Button
                        Button(
                            onClick = onSaveOnePayResults,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save OnePay Results (${onePayDeposits.size} records)")
                        }
                    }
                )
            }

            // App 3: Google Photos
            item {
                AppCard(
                    title = "3. Google Photos",
                    defaultPackage = "com.google.android.apps.photos",
                    targetDescription = "Target: 'Screenshots' album -> scan offer cards & extract full OCR text",
                    status = photosStatus,
                    apkPath = photosApkPath,
                    hasCustomRecipe = hasPhotosRecipe,
                    onApplyApkPath = onPhotosApkPathChange,
                    buttonText = "Test Photos Traversal",
                    onTestClick = onTestPhotos,
                    onTeachClick = onTeachPhotos,
                    onResetRecipeClick = onResetPhotosRecipe,
                    extraContent = {
                        // Discovery Button: Find & Extract
                        Button(
                            onClick = onFindAndExtractScreenshots,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Find & Extract Screenshots to Text")
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // View Extracted Screenshots Dialog Launcher
                        OutlinedButton(
                            onClick = { showPhotosDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View Extracted Screenshots (${photoOffers.size})")
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // Save Photos Results Button
                        Button(
                            onClick = onSavePhotosResults,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Photos Results (${photoOffers.size} offers)")
                        }
                    }
                )
            }

            // Master Audit Button
            item {
                Button(
                    onClick = onRunFullAudit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        "Run Full Reconciliation Audit",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Results Section
            item {
                Text(
                    "Audit Results: Reduced Tips & Tip Baiting",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (discrepancies.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No tip reductions detected within selected date range.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                items(discrepancies) { item ->
                    DiscrepancyResultCard(item)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun AboutDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "About GigScope Auditor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Build Type", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Column {
                    Text("Build Date & Time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = BuildConfig.BUILD_TIME,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Column {
                    Text("Summary of Features", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            Text("• Automated View Hierarchy Traversal", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("Uses Android Accessibility Services to extract trip amounts, dates, and order numbers across Spark Driver, OnePay, and Google Photos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text("• App Drawer Selection System", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("Lets users select installed target apps directly from the system app drawer with real launcher icons and instant search.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text("• Tip-Baiting & Loss Detection", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("Cross-references initial offer screenshots against finalized Spark earnings and 24h OnePay deposits to identify customer tip reductions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text("• Unaccepted Offer Discarding", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("Automatically discards screenshots of offers the driver rejected or missed by inner-joining with confirmed completed Spark trips.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text("• Visual Touch & Scroll Indicators", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("Displays interactive touch ripples, target rings, and glowing swipe arrows showing real-time automation movements across apps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text("• Configurable Speed & Step-by-Step Mode", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("Allows adjusting gesture execution speed (Fast, Normal, Slow, Very Slow) and stepping through operations one-by-one with user confirmation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text("• JSON Configuration Persistence", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("Save and load date ranges, custom APK configurations, and app paths via standard JSON files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text("• Air-Gapped Privacy & Security", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("Zero internet access permission declared. All earnings and personal data persist locally within SQLCipher-encrypted SQLite storage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("OK")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeCard(
    startDate: LocalDate,
    endDate: LocalDate,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    onSelectPreset: (Int) -> Unit
) {
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onStartDateChange(selected)
                        if (selected.isAfter(endDate)) {
                            onEndDateChange(selected)
                        }
                    }
                    showStartDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEndDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onEndDateChange(selected)
                        if (selected.isBefore(startDate)) {
                            onStartDateChange(selected)
                        }
                    }
                    showEndDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Audit Date Range", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Two interactive date buttons (Custom Start Date & Custom End Date)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Start Date Box
                OutlinedCard(
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Start Date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                startDate.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Pick start date",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // End Date Box
                OutlinedCard(
                    onClick = { showEndDatePicker = true },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "End Date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                endDate.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Pick end date",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Quick Presets",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSelectPreset(7) }, modifier = Modifier.weight(1f)) {
                    Text("Last 7d", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { onSelectPreset(14) }, modifier = Modifier.weight(1f)) {
                    Text("Last 14d", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { onSelectPreset(30) }, modifier = Modifier.weight(1f)) {
                    Text("Last 30d", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSettingsCard(
    automationSpeed: String,
    onAutomationSpeedChange: (String) -> Unit,
    stepByStepMode: Boolean,
    onStepByStepModeChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Automation Speed & Execution Control", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Configure how fast touches and scrolls are performed, or step through operations with user confirmation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Speed selection row
            Text("Movement & Traversal Speed:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))

            val speeds = listOf("Fast", "Normal", "Slow", "Very Slow")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                speeds.forEach { speed ->
                    val isSelected = speed.equals(automationSpeed, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onAutomationSpeedChange(speed) },
                        label = { Text(speed, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Step-by-Step Mode Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "Step-by-Step Confirmation",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Pause before every tap or scroll so you can inspect the target and confirm via the on-screen prompt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = stepByStepMode,
                    onCheckedChange = onStepByStepModeChange
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Visual Indicators Status Info
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Visual indicators active: Animated touch ripples, rings, and glowing swipe lines show live touch movements across apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun AppCard(
    title: String,
    defaultPackage: String,
    targetDescription: String,
    status: String,
    apkPath: String,
    hasCustomRecipe: Boolean = false,
    onApplyApkPath: (String) -> Unit,
    buttonText: String,
    onTestClick: () -> Unit,
    onTeachClick: () -> Unit = {},
    onResetRecipeClick: (() -> Unit)? = null,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var inputApkText by remember(apkPath) { mutableStateOf(apkPath) }
    var showAppDrawerDialog by remember { mutableStateOf(false) }

    // Storage file picker fallback
    val storageFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val resolvedPath = resolveUriToPathOrName(context, uri)
                inputApkText = resolvedPath
                onApplyApkPath(resolvedPath)
            }
        }
    )

    if (showAppDrawerDialog) {
        AppDrawerPickerDialog(
            onDismissRequest = { showAppDrawerDialog = false },
            onAppSelected = { appName, packageName, apkFilePath ->
                showAppDrawerDialog = false
                val displayText = if (apkFilePath.isNotBlank()) "$apkFilePath ($packageName)" else packageName
                inputApkText = displayText
                onApplyApkPath(displayText)
            },
            onBrowseStorageApk = {
                showAppDrawerDialog = false
                storageFilePickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Default: $defaultPackage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(4.dp))
            Text(targetDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))

            // Status and Custom Recipe badge row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status: $status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                if (hasCustomRecipe) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Learned Recipe Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.SemiBold
                        )
                        if (onResetRecipeClick != null) {
                            TextButton(
                                onClick = onResetRecipeClick,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Reset", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Button to specify/select from App Drawer
            Button(
                onClick = { showAppDrawerDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select from App Drawer / APK")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Text Field with Clear button & Apply button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputApkText,
                    onValueChange = { inputApkText = it },
                    label = { Text("APK Name & Path") },
                    placeholder = { Text("e.g. /data/app/.../base.apk or package") },
                    singleLine = true,
                    trailingIcon = {
                        if (inputApkText.isNotEmpty()) {
                            IconButton(onClick = {
                                inputApkText = ""
                                onApplyApkPath("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        onApplyApkPath(inputApkText)
                    }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onApplyApkPath(inputApkText)
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Apply")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // App-specific Granular Action Rows & Save Buttons
            extraContent()

            Spacer(modifier = Modifier.height(10.dp))

            // Dual Action Row: [ 🎥 Teach / Record ] and [ ▶️ Test Traversal ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTeachClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Teach / Record", fontSize = 12.sp)
                }
                Button(
                    onClick = onTestClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(buttonText, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AppDrawerPickerDialog(
    onDismissRequest: () -> Unit,
    onAppSelected: (appName: String, packageName: String, apkPath: String) -> Unit,
    onBrowseStorageApk: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var allApps by remember { mutableStateOf<List<AppDrawerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = loadAppDrawerApps(context)
            withContext(Dispatchers.Main) {
                allApps = apps
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, allApps) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("App Drawer Apps", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                // Option to browse APK file from storage
                OutlinedButton(
                    onClick = onBrowseStorageApk,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Or Browse .apk File from Storage...")
                }

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No matching apps found", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(filteredApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAppSelected(app.appName, app.packageName, app.apkPath)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Android,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        app.appName,
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    )
}

fun loadAppDrawerApps(context: Context): List<AppDrawerItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val activities = pm.queryIntentActivities(intent, 0)
    return activities.mapNotNull { resolveInfo ->
        try {
            val label = resolveInfo.loadLabel(pm).toString()
            val pkg = resolveInfo.activityInfo.packageName
            val apk = resolveInfo.activityInfo.applicationInfo.sourceDir ?: ""
            val drawable = resolveInfo.loadIcon(pm)
            val iconBitmap = drawableToImageBitmap(drawable)
            AppDrawerItem(
                appName = label,
                packageName = pkg,
                apkPath = apk,
                icon = iconBitmap
            )
        } catch (e: Exception) {
            null
        }
    }.sortedBy { it.appName.lowercase() }
}

fun drawableToImageBitmap(drawable: Drawable): ImageBitmap? {
    return try {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.asImageBitmap()
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

fun resolveUriToPathOrName(context: Context, uri: Uri): String {
    var displayName: String? = null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                displayName = cursor.getString(nameIndex)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    val path = uri.path
    return when {
        displayName != null && path != null && !path.endsWith(displayName) -> "$displayName ($path)"
        displayName != null -> displayName
        path != null -> path
        else -> uri.toString()
    }
}

@Composable
fun DiscrepancyResultCard(discrepancy: CustomerTipDiscrepancy) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Trip #${discrepancy.tripId}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    discrepancy.date.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Customer: ${discrepancy.customerIdentifier}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Offered: \$${String.format("%.2f", discrepancy.offeredTip)}")
                Text("Final: \$${String.format("%.2f", discrepancy.finalReceivedTip)}")
                Text(
                    "Loss: -\$${String.format("%.2f", discrepancy.lossAmount)}",
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Type: ${discrepancy.discrepancyType.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun SparkTripsDialog(
    trips: List<SparkCompletedTrip>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ListAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Spark Completed Trips (${trips.size})", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (trips.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No trips collected yet.\nTap 'Find Trips' on the Spark card to scan.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(trips) { trip ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Trip #${trip.tripId}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "${trip.tripDate} ${trip.completedTime ?: ""}".trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(trip.tripType ?: "Delivery", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("${trip.stopCount} ${if (trip.stopCount > 1) "Stops" else "Stop"}", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                                if (!trip.customerDropDetails.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Stops / Addresses: ${trip.customerDropDetails}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Offered Tip: \$${String.format("%.2f", trip.initialOfferedTip)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (trip.rawTotalEstimate != null) {
                                        Text(
                                            "Estimated Total: \$${String.format("%.2f", trip.rawTotalEstimate)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SparkEarningsDialog(
    earnings: List<SparkEarningsBreakdown>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Spark Earnings Records (${earnings.size})", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (earnings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No earnings records collected yet.\nTap 'Find Earnings' on the Spark card to scan.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(earnings) { earn ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Trip #${earn.tripId}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "${earn.date} ${earn.timestamp ?: ""}".trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Base: \$${String.format("%.2f", earn.basePay)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Tip: \$${String.format("%.2f", earn.confirmedTip)}", style = MaterialTheme.typography.bodySmall)
                                    if (earn.extraEarnings > 0.0) {
                                        Text("Extra: \$${String.format("%.2f", earn.extraEarnings)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        "Total: \$${String.format("%.2f", earn.totalEarnings)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun OnePayDepositsDialog(
    title: String,
    subtitle: String,
    deposits: List<OnePayDeposit>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("$title (${deposits.size})", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        text = {
            if (deposits.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No deposits collected in this category.\nTap search buttons on the OnePay card to scan.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(deposits) { dep ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        dep.referenceId,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${dep.date} ${dep.timestamp ?: ""}".trim(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        dep.sender,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "+\$${String.format("%.2f", dep.amount)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun PhotosOffersDialog(
    offers: List<PhotoOfferRecord>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Extracted Screenshots (${offers.size})", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (offers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No screenshot offers extracted yet.\nTap 'Find & Extract Screenshots to Text' to scan Google Photos.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(offers) { offer ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Trip #${offer.tripId}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "${offer.captureDate} ${offer.timestamp ?: ""}".trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Base Pay: \$${String.format("%.2f", offer.basePay)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Offered Tip: \$${String.format("%.2f", offer.offeredTip)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    if (offer.estimatedTotal != null) {
                                        Text("Total: \$${String.format("%.2f", offer.estimatedTotal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (!offer.extractedText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Extracted Text (OCR):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = offer.extractedText,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
