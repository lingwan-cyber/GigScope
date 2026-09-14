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
    TEST_ONEPAY,
    TEST_PHOTOS,
    FULL_AUDIT
}

class GigScopeAccessibilityService : AccessibilityService() {

    companion object {
        var instance: GigScopeAccessibilityService? = null
            private set

        var activeTarget: ExecutionTarget = ExecutionTarget.IDLE
        var onStatusUpdate: ((String) -> Unit)? = null
        var onSparkDataCollected: ((List<SparkCompletedTrip>, List<SparkEarningsBreakdown>) -> Unit)? = null
        var onOnePayDataCollected: ((List<OnePayDeposit>) -> Unit)? = null
        var onPhotosDataCollected: ((List<PhotoOfferRecord>) -> Unit)? = null

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
        actionHelper = AccessibilityActionHelper { rootInActiveWindow }
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
            cancelRecording()
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

        // 2. Otherwise handle automated test execution
        when (activeTarget) {
            ExecutionTarget.TEST_SPARK -> {
                if (packageName == "com.walmart.sparkdriver") {
                    handleSparkTraversal()
                }
            }
            ExecutionTarget.TEST_ONEPAY -> {
                if (packageName == "com.onefinance.one") {
                    handleOnePayTraversal()
                }
            }
            ExecutionTarget.TEST_PHOTOS -> {
                if (packageName == "com.google.android.apps.photos") {
                    handlePhotosTraversal()
                }
            }
            else -> { /* Idle or waiting for target app focus */ }
        }
    }

    private fun handleRecordingEvent(event: AccessibilityEvent, session: RecordingSession) {
        val node = event.source

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
                    isScrollable = false
                )
                session.addStepToCurrentPhase(step)
                updateFloatingHudForSession(session)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val className = node?.className?.toString() ?: event.className?.toString()
                val viewId = node?.viewIdResourceName
                val step = RecordedStep(
                    stepIndex = session.currentPhaseStepCount(),
                    actionType = ActionType.SCROLL_CONTAINER,
                    viewId = viewId,
                    className = className,
                    isScrollable = true
                )
                session.addStepToCurrentPhase(step)
                updateFloatingHudForSession(session)
            }
        }
    }

    private fun handleSparkTraversal() {
        activeTarget = ExecutionTarget.IDLE // Prevent duplicate triggering
        serviceScope.launch {
            onStatusUpdate?.invoke("Traversing Spark: Trips & Earnings...")
            val root = rootInActiveWindow ?: return@launch
            val sparkRecipe = activeRecipeMap["com.walmart.sparkdriver"]
            val trips = sparkAutomator.navigateAndCollectTrips(
                root = root,
                startDate = LocalDate.now().minusDays(14),
                endDate = LocalDate.now(),
                customRecipe = sparkRecipe?.phases?.get("trips")
            )
            val tripIds = trips.map { it.tripId }.toSet()
            val earnings = sparkAutomator.navigateAndCollectEarnings(
                root = root,
                targetTripIds = tripIds,
                customRecipe = sparkRecipe?.phases?.get("earnings")
            )
            onSparkDataCollected?.invoke(trips, earnings)
            onStatusUpdate?.invoke("Spark Test Complete: ${trips.size} trips collected")
        }
    }

    private fun handleOnePayTraversal() {
        activeTarget = ExecutionTarget.IDLE
        serviceScope.launch {
            onStatusUpdate?.invoke("Traversing OnePay: Checking Activity...")
            val root = rootInActiveWindow ?: return@launch
            val onePayRecipe = activeRecipeMap["com.onefinance.one"]
            val deposits = onePayAutomator.collectDeposits(
                root = root,
                startDate = LocalDate.now().minusDays(14),
                endDate = LocalDate.now(),
                customRecipe = onePayRecipe?.phases?.get("activity")
            )
            onOnePayDataCollected?.invoke(deposits)
            onStatusUpdate?.invoke("OnePay Test Complete: ${deposits.size} deposits collected")
        }
    }

    private fun handlePhotosTraversal() {
        activeTarget = ExecutionTarget.IDLE
        serviceScope.launch {
            onStatusUpdate?.invoke("Traversing Google Photos: Screenshots...")
            val root = rootInActiveWindow ?: return@launch
            val offers = photosAutomator.collectScreenshotOffers(root, LocalDate.now().minusDays(14), LocalDate.now())
            onPhotosDataCollected?.invoke(offers)
            onStatusUpdate?.invoke("Photos Test Complete: ${offers.size} offers scanned")
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
