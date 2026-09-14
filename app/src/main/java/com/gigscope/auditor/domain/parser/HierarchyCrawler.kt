package com.gigscope.auditor.domain.parser

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

object HierarchyCrawler {

    /**
     * Breadth-First Search (BFS) for locating text matching a specific pattern.
     */
    fun findNodesByRegex(root: AccessibilityNodeInfo, regex: Regex): List<AccessibilityNodeInfo> {
        val matchedNodes = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: continue

            val nodeText = current.text?.toString() ?: current.contentDescription?.toString()
            if (nodeText != null && regex.containsMatchIn(nodeText)) {
                matchedNodes.add(current)
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return matchedNodes
    }

    /**
     * Finds sibling or child node value located relative to a static label anchor
     * e.g., Anchor: "Tip" -> Value: "$7.00"
     */
    fun findValueAdjacentToLabel(
        root: AccessibilityNodeInfo,
        labelPattern: Regex,
        valuePattern: Regex
    ): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: continue
            val text = current.text?.toString() ?: current.contentDescription?.toString()

            if (text != null && labelPattern.containsMatchIn(text)) {
                val parent = current.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val sibling = parent.getChild(i) ?: continue
                        val sibText = sibling.text?.toString() ?: sibling.contentDescription?.toString()
                        if (sibText != null && sibling != current && valuePattern.containsMatchIn(sibText)) {
                            return valuePattern.find(sibText)?.value
                        }
                    }
                }
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }
}
