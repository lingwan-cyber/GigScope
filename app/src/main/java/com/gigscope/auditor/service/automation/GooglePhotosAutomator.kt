package com.gigscope.auditor.service.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.gigscope.auditor.domain.model.PhotoOfferRecord
import com.gigscope.auditor.domain.parser.HierarchyCrawler
import com.gigscope.auditor.service.FloatingHudService
import java.time.LocalDate

class GooglePhotosAutomator(private val actionHelper: AccessibilityActionHelper) {

    private val tripIdRegex = Regex("""(?:Trip|Order)[\s#:]*([A-Za-z0-9\-]{4,12})""", RegexOption.IGNORE_CASE)
    private val tipAmountRegex = Regex("""(?i)(?:Estimated\s+Tip|Customer\s+Tip|Tip)[:\s]*\$([0-9]+\.[0-9]{2})""")
    private val basePayRegex = Regex("""(?i)(?:Base\s+Pay|Delivery\s+Pay|Spark\s+Pay)[:\s]*\$([0-9]+\.[0-9]{2})""")
    private val totalPayRegex = Regex("""(?i)(?:Estimated\s+Total|Total)[:\s]*\$([0-9]+\.[0-9]{2})""")
    private val timeRegex = Regex("""\b(\d{1,2}:\d{2}\s*(?:AM|PM|am|pm))\b""")

    /**
     * Verifies if Google Photos is actively viewing the Screenshots album.
     */
    fun isInsideScreenshotsAlbum(root: AccessibilityNodeInfo): Boolean {
        // 1. Toolbar title is "Screenshots" near top of screen (y < 350)
        val screenshotNodes = root.findAccessibilityNodeInfosByText("Screenshots")
        for (node in screenshotNodes) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.top in 50..350) {
                return true
            }
        }

        // 2. Toolbar contains "Navigate up" / Back button AND Screenshots text exists
        val upButtons = root.findAccessibilityNodeInfosByText("Navigate up")
        if (upButtons.isNotEmpty() && screenshotNodes.isNotEmpty()) {
            return true
        }

        return false
    }

    /**
     * Robust navigation to the Screenshots album:
     * 1. If already in Screenshots, returns immediately.
     * 2. Replays learned recipe if provided.
     * 3. If recipe only reached Collections (or without recipe), locates & clicks "Screenshots" folder.
     * 4. Auto-scrolls Collections list if Screenshots is below the fold.
     * 5. Falls back to Search tab if needed.
     */
    suspend fun navigateToScreenshotsSection(
        root: AccessibilityNodeInfo,
        customRecipe: com.gigscope.auditor.domain.model.AppPhaseRecipe? = null
    ): Boolean {
        var currentRoot = actionHelper.getActiveWindowRoot() ?: root

        // Already in Screenshots album?
        if (isInsideScreenshotsAlbum(currentRoot)) {
            actionHelper.updateActionState("Already in Screenshots album", "Scan screenshots")
            return true
        }

        // 1. Try learned recipe first if available
        if (customRecipe != null && customRecipe.steps.isNotEmpty()) {
            actionHelper.updateActionState("Executing learned navigation recipe", "Navigate to Screenshots")
            actionHelper.executeRecordedNavigation(currentRoot, customRecipe.steps)
            actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Verify Screenshots album")
            currentRoot = actionHelper.getActiveWindowRoot() ?: currentRoot

            if (isInsideScreenshotsAlbum(currentRoot)) {
                return true
            }
            actionHelper.updateActionState("Recipe on Collections page", "Locating Screenshots folder...")
        }

        // 2. Ensure Collections / Library tab is selected
        val isCollectionsSelected = isTabSelected(currentRoot, listOf("Collections", "Library", "tab_collections"))
        if (!isCollectionsSelected) {
            actionHelper.updateActionState("Opening Collections tab", "Select Screenshots folder")
            val clickedTab = actionHelper.findAndClickByText(currentRoot, listOf("Collections", "Library"))
            if (!clickedTab) {
                clickTabById(currentRoot, "com.google.android.apps.photos:id/tab_collections")
            }
            actionHelper.waitForUiStabilization(800L, "Locating Screenshots folder")
            currentRoot = actionHelper.getActiveWindowRoot() ?: currentRoot
        }

        if (isInsideScreenshotsAlbum(currentRoot)) return true

        val screenHeight = actionHelper.getScreenHeight()

        var scrollAttempts = 0
        val maxScrollAttempts = 5

        while (scrollAttempts <= maxScrollAttempts) {
            currentRoot = actionHelper.getActiveWindowRoot() ?: currentRoot

            // Check for Screenshots node
            val screenshotNodes = currentRoot.findAccessibilityNodeInfosByText("Screenshots")
            for (node in screenshotNodes) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                // We want the album row/tile on Collections (not the bottom tab or off-screen)
                if (rect.width() > 0 && rect.height() > 0 && rect.top in 120..(screenHeight - 150)) {
                    actionHelper.updateActionState("Found Screenshots folder", "Tap to open")
                    val cx = rect.centerX().toFloat()
                    val cy = rect.centerY().toFloat()
                    FloatingHudService.showTouch(cx, cy, "👆 Tap 'Screenshots'")

                    // First try clicking parent container if clickable
                    val parent = actionHelper.findParentContainer(node, depth = 2)
                    if (parent != null && parent.isClickable) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    actionHelper.dispatchClick(cx, cy)
                    actionHelper.waitForUiStabilization(1000L, "Verify Screenshots album")

                    currentRoot = actionHelper.getActiveWindowRoot() ?: currentRoot
                    if (isInsideScreenshotsAlbum(currentRoot)) {
                        return true
                    }
                }
            }

            if (scrollAttempts < maxScrollAttempts) {
                actionHelper.updateActionState(
                    "Searching Collections for Screenshots (${scrollAttempts + 1}/$maxScrollAttempts)",
                    "Scroll down"
                )
                val scrolled = actionHelper.performScrollForward(currentRoot)
                if (!scrolled) break
                scrollAttempts++
                actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Check for Screenshots folder")
            } else {
                break
            }
        }

        // 4. Fallback: Search tab navigation
        currentRoot = actionHelper.getActiveWindowRoot() ?: currentRoot
        if (isInsideScreenshotsAlbum(currentRoot)) return true

        actionHelper.updateActionState("Trying Search tab fallback", "Open Search")
        val searchClicked = actionHelper.findAndClickByText(currentRoot, listOf("Search")) ||
                clickTabById(currentRoot, "com.google.android.apps.photos:id/search_destination")
        if (searchClicked) {
            actionHelper.waitForUiStabilization(800L, "Look for Screenshots in Search")
            currentRoot = actionHelper.getActiveWindowRoot() ?: currentRoot

            val searchScreenshots = currentRoot.findAccessibilityNodeInfosByText("Screenshots")
            for (node in searchScreenshots) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0 && rect.top in 120..(screenHeight - 150)) {
                    val cx = rect.centerX().toFloat()
                    val cy = rect.centerY().toFloat()
                    FloatingHudService.showTouch(cx, cy, "👆 Tap 'Screenshots'")
                    actionHelper.dispatchClick(cx, cy)
                    actionHelper.waitForUiStabilization(1000L, "Verify Screenshots album")
                    currentRoot = actionHelper.getActiveWindowRoot() ?: currentRoot
                    if (isInsideScreenshotsAlbum(currentRoot)) return true
                }
            }
        }

        return isInsideScreenshotsAlbum(currentRoot)
    }

    private fun isTabSelected(root: AccessibilityNodeInfo, identifiers: List<String>): Boolean {
        for (id in identifiers) {
            val nodes = root.findAccessibilityNodeInfosByText(id)
            for (node in nodes) {
                if (node.isSelected) return true
                var parent = node.parent
                for (i in 0..2) {
                    if (parent != null && parent.isSelected) return true
                    parent = parent?.parent
                }
            }
        }
        return false
    }

    private suspend fun clickTabById(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        for (node in nodes) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                val cx = rect.centerX().toFloat()
                val cy = rect.centerY().toFloat()
                FloatingHudService.showTouch(cx, cy, "👆 Tap")
                return actionHelper.dispatchClick(cx, cy)
            }
        }
        return false
    }

    suspend fun collectScreenshotOffers(
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate,
        customRecipe: com.gigscope.auditor.domain.model.AppPhaseRecipe? = null
    ): List<PhotoOfferRecord> {
        val rawOffers = mutableListOf<PhotoOfferRecord>()

        // 1. Navigate to Screenshots album
        val reachedScreenshots = navigateToScreenshotsSection(root, customRecipe)
        if (!reachedScreenshots) {
            FloatingHudService.showStatus("Could not locate Screenshots album", 3000L)
            FloatingHudService.updateHud("⚠️ Photos: Not in Screenshots", "Please open Screenshots album manually")
            return rawOffers
        }

        FloatingHudService.updateHud("📸 Screenshots Album Opened", "Scanning for trip offer cards...")
        actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Scan screenshot offers")

        // 2. Autonomous Scroll & extract screenshot offer details
        var scrollCount = 0
        val maxScrolls = 25

        while (scrollCount < maxScrolls) {
            actionHelper.updateActionState(
                "Scanning Photos (extracted ${rawOffers.size} offers, scroll $scrollCount/$maxScrolls)",
                if (scrollCount + 1 < maxScrolls) "Scroll down album" else "Finish scan"
            )
            val current = actionHelper.getActiveWindowRoot() ?: break
            val textDump = StringBuilder()
            HierarchyCrawler.findNodesByRegex(current, Regex(".*")).forEach {
                val text = it.text?.toString() ?: it.contentDescription?.toString()
                if (!text.isNullOrBlank()) {
                    textDump.append(text).append(" | ")
                }
            }

            val allContent = textDump.toString()
            val tripMatches = tripIdRegex.findAll(allContent).toList()

            for (match in tripMatches) {
                val tripId = match.groupValues[1]
                if (rawOffers.none { it.tripId == tripId }) {
                    val tip = tipAmountRegex.find(allContent)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                    val base = basePayRegex.find(allContent)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                    val total = totalPayRegex.find(allContent)?.groupValues?.get(1)?.toDoubleOrNull() ?: (base + tip)
                    val time = timeRegex.find(allContent)?.value

                    rawOffers.add(
                        PhotoOfferRecord(
                            tripId = tripId,
                            offeredTip = tip,
                            basePay = base,
                            captureDate = LocalDate.now(),
                            timestamp = time,
                            imageUri = null,
                            isAccepted = false,
                            extractedText = allContent.take(1500),
                            estimatedTotal = total
                        )
                    )
                }
            }

            actionHelper.updateActionState(
                "Scrolling Photos album (${scrollCount + 1}/$maxScrolls)",
                "Scan next photos"
            )
            val scrolled = actionHelper.performScrollForward(current)
            if (!scrolled) break
            scrollCount++
            actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Scan next photos")
        }

        return rawOffers
    }
}
