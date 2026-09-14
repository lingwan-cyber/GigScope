package com.gigscope.auditor.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.gigscope.auditor.domain.model.*
import com.gigscope.auditor.service.automation.*
import kotlinx.coroutines.*
import java.time.LocalDate

enum class ExecutionTarget {
    IDLE,
    TEST_SPARK,
    SPARK_FIND_TRIPS,
    SPARK_FIND_EARNINGS,
    TEST_ONEPAY,
    ONEPAY_FIND_TRIP_EARNINGS,
    ONEPAY_FIND_TIP_DEPOSITS,
    TEST_PHOTOS,
    PHOTOS_EXTRACT_SCREENSHOTS,
    FULL_AUDIT
}

class GigScopeAccessibilityService : AccessibilityService() {

    companion object {
        var instance: GigScopeAccessibilityService? = null
            private set

        var activeTarget: ExecutionTarget = ExecutionTarget.IDLE
        var queryStartDate: LocalDate = LocalDate.now().minusDays(14)
        var queryEndDate: LocalDate = LocalDate.now()

        var onStatusUpdate: ((String) -> Unit)? = null
        var onSparkDataCollected: ((List<SparkCompletedTrip>, List<SparkEarningsBreakdown>) -> Unit)? = null
        var onSparkTripsCollected: ((List<SparkCompletedTrip>) -> Unit)? = null
        var onSparkEarningsCollected: ((List<SparkEarningsBreakdown>) -> Unit)? = null
        var onOnePayDataCollected: ((List<OnePayDeposit>) -> Unit)? = null
        var onOnePayTripEarningsCollected: ((List<OnePayDeposit>) -> Unit)? = null
        var onOnePayTipDepositsCollected: ((List<OnePayDeposit>) -> Unit)? = null
        var onPhotosDataCollected: ((List<PhotoOfferRecord>) -> Unit)? = null
        var onPhotosScreenshotsExtracted: ((List<PhotoOfferRecord>) -> Unit)? = null

        var activeRecordingSession: RecordingSession? = null
        var activeRecipeMap: MutableMap<String, AppRecipe> = mutableMapOf()
        var onRecipeRecorded: ((AppRecipe) -> Unit)? = null

        fun startRecording(targetPackage: String, appName: String, phases: List<String>) {
            val session = RecordingSession(
                targetPackage = targetPackage,
                appName = appName,
                totalPhases = phases
            )
            activeRecordingSession = session
            updateFloatingHudForSession(session)
        }

        fun advanceRecordingPhase() {
            val session = activeRecordingSession ?: return
            if (session.currentPhaseIndex < session.totalPhases.size - 1) {
                session.currentPhaseIndex++
                updateFloatingHudForSession(session)
            }
        }

        fun finishRecording() {
            val session = activeRecordingSession ?: return
            val recipe = session.toAppRecipe()
            activeRecipeMap[recipe.packageName] = recipe
            onRecipeRecorded?.invoke(recipe)
            activeRecordingSession = null
            onStatusUpdate?.invoke("Saved custom recipe for ${recipe.appName} (${recipe.phases.size} phases)")
        }

        fun cancelRecording() {
            activeRecordingSession = null
            onStatusUpdate?.invoke("Recording cancelled")
        }

        private fun updateFloatingHudForSession(session: RecordingSession) {
            val phase = session.currentPhaseName
            val isSparkPhase1 = session.targetPackage.contains("spark") && session.currentPhaseIndex == 0
            val count = session.currentPhaseStepCount()
            FloatingHudService.updateHud(
                title = "🔴 REC: ${session.appName} - $phase",
                subtitle = "Recorded: $count steps. Tap tabs & scroll once.",
                isSparkPhase1 = isSparkPhase1
            )
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var actionHelper: AccessibilityActionHelper
    private lateinit var sparkAutomator: SparkDriverAutomator
    private lateinit var onePayAutomator: OnePayAutomator
    private lateinit var photosAutomator: GooglePhotosAutomator

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        actionHelper = AccessibilityActionHelper { this }
        sparkAutomator = SparkDriverAutomator(actionHelper)
        onePayAutomator = OnePayAutomator(actionHelper)
        photosAutomator = GooglePhotosAutomator(actionHelper)

        FloatingHudService.onNextPhaseRequested = {
            advanceRecordingPhase()
        }
        FloatingHudService.onFinishRequested = {
            finishRecording()
            FloatingHudService.stop(this)
        }
        FloatingHudService.onCancelRequested = {
            if (activeRecordingSession != null) {
                cancelRecording()
            }
            activeTarget = ExecutionTarget.IDLE
            FloatingHudService.stop(this)
        }

        onStatusUpdate?.invoke("Accessibility Service Connected & Ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // 1. Check if we are actively recording macro demonstration
        val session = activeRecordingSession
        if (session != null && packageName == session.targetPackage) {
            handleRecordingEvent(event, session)
            return
        }

        // 2. Otherwise handle automated execution
        when (activeTarget) {
            ExecutionTarget.TEST_SPARK -> {
                if (packageName == "com.walmart.sparkdriver") {
                    handleSparkTraversal()
                }
            }
            ExecutionTarget.SPARK_FIND_TRIPS -> {
                if (packageName == "com.walmart.sparkdriver") {
                    handleSparkTripsOnly()
                }
            }
            ExecutionTarget.SPARK_FIND_EARNINGS -> {
                if (packageName == "com.walmart.sparkdriver") {
                    handleSparkEarningsOnly()
                }
            }
            ExecutionTarget.TEST_ONEPAY -> {
                if (packageName == "com.onefinance.one") {
                    handleOnePayTraversal()
                }
            }
            ExecutionTarget.ONEPAY_FIND_TRIP_EARNINGS -> {
                if (packageName == "com.onefinance.one") {
                    handleOnePayTripEarningsOnly()
                }
            }
            ExecutionTarget.ONEPAY_FIND_TIP_DEPOSITS -> {
                if (packageName == "com.onefinance.one") {
                    handleOnePayTipDepositsOnly()
                }
            }
            ExecutionTarget.TEST_PHOTOS, ExecutionTarget.PHOTOS_EXTRACT_SCREENSHOTS -> {
                if (packageName == "com.google.android.apps.photos") {
                    handlePhotosTraversal()
                }
            }
            else -> { /* Idle or waiting for target app focus */ }
        }
    }

    private fun handleRecordingEvent(event: AccessibilityEvent, session: RecordingSession) {
        val node = event.source
        val rect = android.graphics.Rect()
        node?.getBoundsInScreen(rect)

        val screenX = if (!rect.isEmpty) rect.centerX() else -1
        val screenY = if (!rect.isEmpty) rect.centerY() else -1

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val text = node?.text?.toString()?.takeIf { it.isNotBlank() }
                    ?: event.text.joinToString(" ").takeIf { it.isNotBlank() }
                val contentDesc = node?.contentDescription?.toString()
                val viewId = node?.viewIdResourceName
                val className = node?.className?.toString() ?: event.className?.toString()

                val step = RecordedStep(
                    stepIndex = session.currentPhaseStepCount(),
                    actionType = ActionType.CLICK,
                    targetText = text,
                    contentDescription = contentDesc,
                    viewId = viewId,
                    className = className,
                    isScrollable = false,
                    screenX = screenX,
                    screenY = screenY,
                    boundsLeft = if (!rect.isEmpty) rect.left else 0,
                    boundsTop = if (!rect.isEmpty) rect.top else 0,
                    boundsRight = if (!rect.isEmpty) rect.right else 0,
                    boundsBottom = if (!rect.isEmpty) rect.bottom else 0
                )
                session.addStepToCurrentPhase(step)
                updateFloatingHudForSession(session)
                if (screenX >= 0 && screenY >= 0) {
                    FloatingHudService.showTouch(screenX.toFloat(), screenY.toFloat(), text ?: contentDesc ?: "Tap")
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val className = node?.className?.toString() ?: event.className?.toString()
                val viewId = node?.viewIdResourceName
                val step = RecordedStep(
                    stepIndex = session.currentPhaseStepCount(),
                    actionType = ActionType.SCROLL_CONTAINER,
                    viewId = viewId,
                    className = className,
                    isScrollable = true,
                    screenX = screenX,
                    screenY = screenY,
                    boundsLeft = if (!rect.isEmpty) rect.left else 0,
                    boundsTop = if (!rect.isEmpty) rect.top else 0,
                    boundsRight = if (!rect.isEmpty) rect.right else 0,
                    boundsBottom = if (!rect.isEmpty) rect.bottom else 0
                )
                session.addStepToCurrentPhase(step)
                updateFloatingHudForSession(session)
                if (!rect.isEmpty) {
                    val cx = rect.centerX().toFloat()
                    val startY = rect.bottom.toFloat() * 0.8f
                    val endY = rect.top.toFloat() * 1.2f
                    FloatingHudService.showSwipe(cx, startY, cx, endY, "Scroll")
                }
            }
        }
    }

    private fun handleSparkTripsOnly() {
        activeTarget = ExecutionTarget.IDLE
        serviceScope.launch {
            val msg = "Scanning Spark Trips ($queryStartDate to $queryEndDate)..."
            onStatusUpdate?.invoke(msg)
            FloatingHudService.showStatus(msg)
            FloatingHudService.updateHud("⚡ Spark: Trips Scan", "Filtering $queryStartDate to $queryEndDate")
            val root = rootInActiveWindow ?: return@launch
            val sparkRecipe = activeRecipeMap["com.walmart.sparkdriver"]
            val trips = sparkAutomator.navigateAndCollectTrips(
                root = root,
                startDate = queryStartDate,
                endDate = queryEndDate,
                customRecipe = sparkRecipe?.phases?.get("trips")
            )
            onSparkTripsCollected?.invoke(trips)
            val doneMsg = "Found ${trips.size} Spark trips within date range"
            onStatusUpdate?.invoke(doneMsg)
            FloatingHudService.showStatus(doneMsg, 2500L)
            FloatingHudService.updateHud("✅ Spark Trips Complete", "Extracted ${trips.size} trips")
        }
    }

    private fun handleSparkEarningsOnly() {
        activeTarget = ExecutionTarget.IDLE
        serviceScope.launch {
            val msg = "Scanning Spark Earnings ($queryStartDate to $queryEndDate)..."
            onStatusUpdate?.invoke(msg)
            FloatingHudService.showStatus(msg)
            FloatingHudService.updateHud("⚡ Spark: Earnings Scan", "Filtering $queryStartDate to $queryEndDate")
            val root = rootInActiveWindow ?: return@launch
            val sparkRecipe = activeRecipeMap["com.walmart.sparkdriver"]
            val earnings = sparkAutomator.navigateAndCollectEarnings(
                root = root,
                startDate = queryStartDate,
                endDate = queryEndDate,
                targetTripIds = emptySet(),
                customRecipe = sparkRecipe?.phases?.get("earnings")
            )
            onSparkEarningsCollected?.invoke(earnings)
            val doneMsg = "Found ${earnings.size} Spark earnings breakdowns"
            onStatusUpdate?.invoke(doneMsg)
            FloatingHudService.showStatus(doneMsg, 2500L)
            FloatingHudService.updateHud("✅ Spark Earnings Complete", "Extracted ${earnings.size} earnings")
        }
    }

    private fun handleSparkTraversal() {
        activeTarget = ExecutionTarget.IDLE
        serviceScope.launch {
            val msg = "Traversing Spark: Trips & Earnings ($queryStartDate to $queryEndDate)..."
            onStatusUpdate?.invoke(msg)
            FloatingHudService.showStatus(msg)
            FloatingHudService.updateHud("⚡ Spark Full Scan", "Trips & Earnings...")
            val root = rootInActiveWindow ?: return@launch
            val sparkRecipe = activeRecipeMap["com.walmart.sparkdriver"]
            val trips = sparkAutomator.navigateAndCollectTrips(
                root = root,
                startDate = queryStartDate,
                endDate = queryEndDate,
                customRecipe = sparkRecipe?.phases?.get("trips")
            )
            val tripIds = trips.map { it.tripId }.toSet()
            val earnings = sparkAutomator.navigateAndCollectEarnings(
                root = root,
                startDate = queryStartDate,
                endDate = queryEndDate,
                targetTripIds = tripIds,
                customRecipe = sparkRecipe?.phases?.get("earnings")
            )
            onSparkDataCollected?.invoke(trips, earnings)
            onSparkTripsCollected?.invoke(trips)
            onSparkEarningsCollected?.invoke(earnings)
            val doneMsg = "Spark Scan Complete: ${trips.size} trips, ${earnings.size} earnings"
            onStatusUpdate?.invoke(doneMsg)
            FloatingHudService.showStatus(doneMsg, 2500L)
            FloatingHudService.updateHud("✅ Spark Complete", "${trips.size} trips, ${earnings.size} earnings")
        }
    }

    private fun handleOnePayTripEarningsOnly() {
        activeTarget = ExecutionTarget.IDLE
        serviceScope.launch {
            val msg = "Scanning OnePay Trip Earnings ($queryStartDate to $queryEndDate)..."
            onStatusUpdate?.invoke(msg)
            FloatingHudService.showStatus(msg)
            FloatingHudService.updateHud("⚡ OnePay: Trip Earnings", "Filtering $queryStartDate to $queryEndDate")
            val root = rootInActiveWindow ?: return@launch
            val onePayRecipe = activeRecipeMap["com.onefinance.one"]
            val tripEarnings = onePayAutomator.collectTripEarnings(
                root = root,
                startDate = queryStartDate,
                endDate = queryEndDate,
                customRecipe = onePayRecipe?.phases?.get("activity")
            )
            onOnePayTripEarningsCollected?.invoke(tripEarnings)
            val doneMsg = "Found ${tripEarnings.size} OnePay trip earnings deposits"
            onStatusUpdate?.invoke(doneMsg)
            FloatingHudService.showStatus(doneMsg, 2500L)
            FloatingHudService.updateHud("✅ OnePay Earnings Complete", "Extracted ${tripEarnings.size} deposits")
        }
    }

    private fun handleOnePayTipDepositsOnly() {
        activeTarget = ExecutionTarget.IDLE
        serviceScope.launch {
            val msg = "Scanning OnePay Tip Deposits ($queryStartDate to $queryEndDate)..."
            onStatusUpdate?.invoke(msg)
            FloatingHudService.showStatus(msg)
            FloatingHudService.updateHud("⚡ OnePay: Tip Deposits", "Filtering $queryStartDate to $queryEndDate")
            val root = rootInActiveWindow ?: return@launch
            val onePayRecipe = activeRecipeMap["com.onefinance.one"]
            val tipDeposits = onePayAutomator.collectTipDeposits(
                root = root,
                startDate = queryStartDate,
                endDate = queryEndDate,
                customRecipe = onePayRecipe?.phases?.get("activity")
            )
            onOnePayTipDepositsCollected?.invoke(tipDeposits)
            val doneMsg = "Found ${tipDeposits.size} OnePay tip deposits"
            onStatusUpdate?.invoke(doneMsg)
            FloatingHudService.showStatus(doneMsg, 2500L)
            FloatingHudService.updateHud("✅ OnePay Tips Complete", "Extracted ${tipDeposits.size} deposits")
        }
    }

    private fun handleOnePayTraversal() {
        activeTarget = ExecutionTarget.IDLE
        serviceScope.launch {
            val msg = "Traversing OnePay Activity ($queryStartDate to $queryEndDate)..."
            onStatusUpdate?.invoke(msg)
            FloatingHudService.showStatus(msg)
            FloatingHudService.updateHud("⚡ OnePay Full Scan", "Filtering $queryStartDate to $queryEndDate")
            val root = rootInActiveWindow ?: return@launch
            val onePayRecipe = activeRecipeMap["com.onefinance.one"]
            val deposits = onePayAutomator.collectDeposits(
                root = root,
                startDate = queryStartDate,
                endDate = queryEndDate,
                customRecipe = onePayRecipe?.phases?.get("activity")
            )
            onOnePayDataCollected?.invoke(deposits)
            val tripEarnings = deposits.filter { it.transactionType == OnePayTransactionType.TRIP_EARNING }
            val tipDeposits = deposits.filter { it.transactionType == OnePayTransactionType.TIP_DEPOSIT }
            onOnePayTripEarningsCollected?.invoke(tripEarnings)
            onOnePayTipDepositsCollected?.invoke(tipDeposits)
            val doneMsg = "OnePay Complete: ${deposits.size} total deposits (${tripEarnings.size} trip earnings, ${tipDeposits.size} tips)"
            onStatusUpdate?.invoke(doneMsg)
            FloatingHudService.showStatus(doneMsg, 2500L)
            FloatingHudService.updateHud("✅ OnePay Complete", "${deposits.size} deposits total")
        }
    }

    private fun handlePhotosTraversal() {
        activeTarget = ExecutionTarget.IDLE
        serviceScope.launch {
            val msg = "Scanning Google Photos: Screenshots ($queryStartDate to $queryEndDate)..."
            onStatusUpdate?.invoke(msg)
            FloatingHudService.showStatus(msg)
            FloatingHudService.updateHud("⚡ Google Photos Scan", "Extracting trip screenshots...")
            val root = rootInActiveWindow ?: return@launch
            val photosRecipe = activeRecipeMap["com.google.android.apps.photos"]
            val offers = photosAutomator.collectScreenshotOffers(
                root = root,
                startDate = queryStartDate,
                endDate = queryEndDate,
                customRecipe = photosRecipe?.phases?.get("screenshots")
            )
            onPhotosDataCollected?.invoke(offers)
            onPhotosScreenshotsExtracted?.invoke(offers)
            val doneMsg = "Photos Complete: ${offers.size} screenshot offers extracted"
            onStatusUpdate?.invoke(doneMsg)
            FloatingHudService.showStatus(doneMsg, 2500L)
            FloatingHudService.updateHud("✅ Photos Complete", "${offers.size} offers extracted")
        }
    }

    override fun onInterrupt() {
        instance = null
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }
}
