package com.gigscope.auditor.service.automation

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import java.util.ArrayDeque

class AccessibilityActionHelper(private val getActiveRoot: () -> AccessibilityNodeInfo?) {

    fun getActiveWindowRoot(): AccessibilityNodeInfo? = getActiveRoot()

    suspend fun waitForUiStabilization(timeoutMs: Long = 350) {
        delay(timeoutMs)
    }

    fun findAndClickByText(root: AccessibilityNodeInfo, possibleTexts: List<String>): Boolean {
        for (target in possibleTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(target)
            for (node in nodes) {
                if (performClickHierarchy(node)) {
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

    fun performScrollForward(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: continue
            if (node.isScrollable) {
                val success = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                if (success) return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
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

    suspend fun executeRecordedNavigation(root: AccessibilityNodeInfo, steps: List<com.gigscope.auditor.domain.model.RecordedStep>): Boolean {
        var currentWindowRoot: AccessibilityNodeInfo = root
        for (step in steps) {
            when (step.actionType) {
                com.gigscope.auditor.domain.model.ActionType.CLICK -> {
                    val clicked = clickRecordedStep(currentWindowRoot, step)
                    if (clicked) {
                        waitForUiStabilization(500)
                        currentWindowRoot = getActiveWindowRoot() ?: currentWindowRoot
                    }
                }
                com.gigscope.auditor.domain.model.ActionType.WAIT -> {
                    waitForUiStabilization(500)
                }
                com.gigscope.auditor.domain.model.ActionType.SCROLL_CONTAINER -> {
                    // Reached the pagination container; navigation is complete!
                    break
                }
            }
        }
        return true
    }

    private fun clickRecordedStep(root: AccessibilityNodeInfo, step: com.gigscope.auditor.domain.model.RecordedStep): Boolean {
        // 1. Try matching by view ID if available
        if (!step.viewId.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(step.viewId)
            for (node in nodes) {
                if (performClickHierarchy(node)) return true
            }
        }

        // 2. Try matching by target text
        if (!step.targetText.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(step.targetText)
            for (node in nodes) {
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
                    if (performClickHierarchy(curr)) return true
                }
                for (i in 0 until curr.childCount) {
                    curr.getChild(i)?.let { queue.add(it) }
                }
            }
        }

        return false
    }
}
