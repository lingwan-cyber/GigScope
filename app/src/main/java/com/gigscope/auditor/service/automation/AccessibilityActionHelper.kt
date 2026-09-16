package com.gigscope.auditor.service.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.gigscope.auditor.service.FloatingHudService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque

class AccessibilityActionHelper(
    private val serviceProvider: () -> AccessibilityService?
) {

    fun getActiveWindowRoot(): AccessibilityNodeInfo? = serviceProvider()?.rootInActiveWindow

    fun getStabilizationDelay(): Long {
        return when (FloatingHudService.currentSpeed) {
            "Fast" -> 350L
            "Normal" -> 750L
            "Slow" -> 1600L
            "Very Slow" -> 2600L
            else -> 750L
        }
    }

    fun getSwipeDuration(): Long {
        return when (FloatingHudService.currentSpeed) {
            "Fast" -> 250L
            "Normal" -> 450L
            "Slow" -> 850L
            "Very Slow" -> 1300L
            else -> 450L
        }
    }

    fun getScreenHeight(): Int {
        return serviceProvider()?.resources?.displayMetrics?.heightPixels ?: 2400
    }

    fun getScreenWidth(): Int {
        return serviceProvider()?.resources?.displayMetrics?.widthPixels ?: 1080
    }

    var lastCurrentAction: String = "Idle"
    var lastNextAction: String? = null

    fun updateActionState(current: String, next: String? = null) {
        lastCurrentAction = current
        lastNextAction = next
        FloatingHudService.updateActionDetails(current, next)
    }

    suspend fun waitForUiStabilization(timeoutMs: Long = getStabilizationDelay(), nextActionLabel: String? = null) {
        if (nextActionLabel != null) {
            updateActionState(lastCurrentAction, nextActionLabel)
        }
        if (FloatingHudService.currentSpeed == "Fast" || timeoutMs <= 300L) {
            FloatingHudService.updateCountdown(null)
            delay(timeoutMs)
            return
        }

        val startTime = System.currentTimeMillis()
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            val remainingSec = (timeoutMs - elapsed) / 1000.0f
            FloatingHudService.updateCountdown(String.format(java.util.Locale.US, "⏳ Next in %.1fs...", remainingSec))
            val stepDelay = minOf(100L, timeoutMs - elapsed)
            delay(stepDelay)
            elapsed = System.currentTimeMillis() - startTime
        }
        FloatingHudService.updateCountdown(null)
    }

    suspend fun dispatchClick(x: Float, y: Float, durationMs: Long = 100L): Boolean {
        val service = serviceProvider() ?: return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val deferred = CompletableDeferred<Boolean>()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                deferred.complete(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                deferred.complete(false)
            }
        }
        val dispatched = service.dispatchGesture(gesture, callback, null)
        if (!dispatched) return false
        return withTimeoutOrNull(durationMs + 1000L) { deferred.await() } ?: false
    }

    suspend fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = getSwipeDuration()): Boolean {
        val service = serviceProvider() ?: return false
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val deferred = CompletableDeferred<Boolean>()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                deferred.complete(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                deferred.complete(false)
            }
        }
        val dispatched = service.dispatchGesture(gesture, callback, null)
        if (!dispatched) return false
        return withTimeoutOrNull(durationMs + 1500L) { deferred.await() } ?: false
    }

    suspend fun performScrollForward(root: AccessibilityNodeInfo? = null): Boolean {
        val displayMetrics = serviceProvider()?.resources?.displayMetrics
        val width = displayMetrics?.widthPixels ?: 1080
        val height = displayMetrics?.heightPixels ?: 2400

        val startX = width * 0.5f
        val startY = height * 0.72f
        val endX = width * 0.5f
        val endY = height * 0.28f

        val swipeDuration = getSwipeDuration()
        FloatingHudService.showSwipe(startX, startY, endX, endY, "📜 Scrolling forward...", swipeDuration + 400L)

        // 1. Hardware gesture swipe (works on Compose, React Native, custom ScrollViews)
        val swiped = dispatchSwipe(startX, startY, endX, endY, swipeDuration)
        if (swiped) {
            waitForUiStabilization(getStabilizationDelay())
            return true
        }

        // 2. Fallback to AccessibilityNode scroll action
        if (root != null) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val node = queue.poll() ?: continue
                if (node.isScrollable) {
                    val success = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    if (success) {
                        waitForUiStabilization(getStabilizationDelay())
                        return true
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        }
        return false
    }

    suspend fun findAndClickByText(root: AccessibilityNodeInfo, possibleTexts: List<String>): Boolean {
        for (target in possibleTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(target)
            for (node in nodes) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    val cx = rect.centerX().toFloat()
                    val cy = rect.centerY().toFloat()
                    FloatingHudService.showTouch(cx, cy, "👆 Tap '$target'")
                    val gestureClicked = dispatchClick(cx, cy)
                    if (gestureClicked) {
                        waitForUiStabilization(getStabilizationDelay())
                        return true
                    }
                }
                if (performClickHierarchy(node)) {
                    waitForUiStabilization(getStabilizationDelay())
                    return true
                }
            }
        }
        return false
    }

    private fun performClickHierarchy(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    suspend fun awaitStepConfirmation(
        description: String,
        currentStep: Int,
        totalSteps: Int,
        highlightX: Float? = null,
        highlightY: Float? = null
    ): Boolean {
        if (!FloatingHudService.isStepByStepActive) {
            if (highlightX != null && highlightY != null) {
                FloatingHudService.showTouch(highlightX, highlightY, description, getStabilizationDelay())
            }
            waitForUiStabilization(getStabilizationDelay())
            return true
        }

        if (highlightX != null && highlightY != null) {
            FloatingHudService.showTouch(highlightX, highlightY, "Target: $description", 3000L)
        }

        FloatingHudService.requestStepConfirmation(description, currentStep, totalSteps)

        val stepDeferred = CompletableDeferred<Boolean>()
        FloatingHudService.onStepConfirmed = {
            stepDeferred.complete(true)
        }
        FloatingHudService.onAutoRunRequested = {
            FloatingHudService.isStepByStepActive = false
            stepDeferred.complete(true)
        }
        FloatingHudService.onCancelRequested = {
            stepDeferred.complete(false)
        }
        FloatingHudService.onAbortRequested = {
            stepDeferred.complete(false)
        }

        val confirmed = stepDeferred.await()
        FloatingHudService.clearStepConfirmation()
        return confirmed
    }

    suspend fun executeRecordedNavigation(
        root: AccessibilityNodeInfo,
        steps: List<com.gigscope.auditor.domain.model.RecordedStep>
    ): Boolean {
        var currentWindowRoot: AccessibilityNodeInfo = root
        val total = steps.size

        for ((index, step) in steps.withIndex()) {
            val stepNum = index + 1
            val nextStep = steps.getOrNull(index + 1)
            val currentDesc = when (step.actionType) {
                com.gigscope.auditor.domain.model.ActionType.CLICK ->
                    "Tap: " + (step.targetText ?: step.contentDescription ?: "Step #$stepNum")
                com.gigscope.auditor.domain.model.ActionType.SCROLL_CONTAINER ->
                    "Scroll container"
                com.gigscope.auditor.domain.model.ActionType.WAIT ->
                    "Wait for screen update"
            }
            val nextDesc = nextStep?.let { ns ->
                when (ns.actionType) {
                    com.gigscope.auditor.domain.model.ActionType.CLICK ->
                        "Tap: " + (ns.targetText ?: ns.contentDescription ?: "Step #${index + 2}")
                    com.gigscope.auditor.domain.model.ActionType.SCROLL_CONTAINER ->
                        "Scroll container"
                    com.gigscope.auditor.domain.model.ActionType.WAIT ->
                        "Wait for screen update"
                }
            } ?: "Data extraction / Finish"

            updateActionState("Step $stepNum/$total: $currentDesc", nextDesc)

            when (step.actionType) {
                com.gigscope.auditor.domain.model.ActionType.CLICK -> {
                    val desc = step.targetText ?: step.contentDescription ?: "Tap (#${stepNum})"
                    val targetX = if (step.screenX >= 0) step.screenX.toFloat() else null
                    val targetY = if (step.screenY >= 0) step.screenY.toFloat() else null

                    val confirmed = awaitStepConfirmation(desc, stepNum, total, targetX, targetY)
                    if (!confirmed) return false

                    val clicked = clickRecordedStep(currentWindowRoot, step)
                    waitForUiStabilization(getStabilizationDelay(), nextDesc)
                    currentWindowRoot = getActiveWindowRoot() ?: currentWindowRoot
                }
                com.gigscope.auditor.domain.model.ActionType.WAIT -> {
                    waitForUiStabilization(getStabilizationDelay(), nextDesc)
                }
                com.gigscope.auditor.domain.model.ActionType.SCROLL_CONTAINER -> {
                    val confirmed = awaitStepConfirmation("Scroll Container", stepNum, total)
                    if (!confirmed) return false
                    performScrollForward(currentWindowRoot)
                    waitForUiStabilization(getStabilizationDelay(), nextDesc)
                    currentWindowRoot = getActiveWindowRoot() ?: currentWindowRoot
                }
            }
        }
        return true
    }

    private suspend fun clickRecordedStep(root: AccessibilityNodeInfo, step: com.gigscope.auditor.domain.model.RecordedStep): Boolean {
        // 1. Try matching by view ID if available
        if (!step.viewId.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(step.viewId)
            for (node in nodes) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    val cx = rect.centerX().toFloat()
                    val cy = rect.centerY().toFloat()
                    FloatingHudService.showTouch(cx, cy, "👆 Tap: ${step.targetText ?: step.viewId}")
                    val gestureClicked = dispatchClick(cx, cy)
                    if (gestureClicked) return true
                }
                if (performClickHierarchy(node)) return true
            }
        }

        // 2. Try matching by target text
        if (!step.targetText.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(step.targetText)
            for (node in nodes) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    val cx = rect.centerX().toFloat()
                    val cy = rect.centerY().toFloat()
                    FloatingHudService.showTouch(cx, cy, "👆 Tap: '${step.targetText}'")
                    val gestureClicked = dispatchClick(cx, cy)
                    if (gestureClicked) return true
                }
                if (performClickHierarchy(node)) return true
            }
        }

        // 3. Try matching by content description
        if (!step.contentDescription.isNullOrBlank()) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val curr = queue.poll() ?: continue
                if (curr.contentDescription?.toString().equals(step.contentDescription, ignoreCase = true)) {
                    val rect = Rect()
                    curr.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        val cx = rect.centerX().toFloat()
                        val cy = rect.centerY().toFloat()
                        FloatingHudService.showTouch(cx, cy, "👆 Tap: '${step.contentDescription}'")
                        val gestureClicked = dispatchClick(cx, cy)
                        if (gestureClicked) return true
                    }
                    if (performClickHierarchy(curr)) return true
                }
                for (i in 0 until curr.childCount) {
                    curr.getChild(i)?.let { queue.add(it) }
                }
            }
        }

        // 4. Coordinates Fallback (vital for React Native, Flutter, and Jetpack Compose canvas views!)
        if (step.screenX >= 0 && step.screenY >= 0) {
            val cx = step.screenX.toFloat()
            val cy = step.screenY.toFloat()
            FloatingHudService.showTouch(cx, cy, "👆 Tap at (${step.screenX}, ${step.screenY})")
            return dispatchClick(cx, cy)
        }

        return false
    }

    fun findParentContainer(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        for (i in 0 until depth) {
            current = current?.parent ?: return null
        }
        return current
    }

    fun findNodesByPattern(root: AccessibilityNodeInfo, regex: Regex): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: continue
            val text = current.text?.toString() ?: current.contentDescription?.toString()
            if (text != null && regex.containsMatchIn(text)) {
                result.add(current)
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return result
    }

    fun findNodesByClass(root: AccessibilityNodeInfo, className: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: continue
            if (current.className?.toString() == className) {
                result.add(current)
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return result
    }
}
