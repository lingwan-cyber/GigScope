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
    /**
     * Verifies if Google Photos is actively viewing the Screenshots album.
     */
    fun isInsideScreenshotsAlbum(root: AccessibilityNodeInfo): Boolean {
        // 1. If bottom tab bar or collections grid is present, we are on the main tabs, NOT inside an album.
        val collectionsGrid = root.findAccessibilityNodeInfosByViewId("com.google.android.apps.photos:id/photos_collectionstab_grid_view")
        if (collectionsGrid.isNotEmpty()) return false

        val tabCollections = root.findAccessibilityNodeInfosByViewId("com.google.android.apps.photos:id/tab_collections")
        if (tabCollections.isNotEmpty()) return false

        val tabPhotos = root.findAccessibilityNodeInfosByViewId("com.google.android.apps.photos:id/tab_photos")
        if (tabPhotos.isNotEmpty()) return false

        // 2. Inside Screenshots album: Toolbar title is "Screenshots"
        val toolbars = root.findAccessibilityNodeInfosByViewId("com.google.android.apps.photos:id/toolbar")
        for (tb in toolbars) {
            val titles = tb.findAccessibilityNodeInfosByText("Screenshots")
            if (titles.isNotEmpty()) return true
        }

        val screenshotNodes = root.findAccessibilityNodeInfosByText("Screenshots")
        for (node in screenshotNodes) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            // The album title in the top toolbar is near the top (y between 40 and 250)
            if (rect.top in 40..250 && rect.height() in 20..150) {
                return true
            }
        }

        // 3. Date scrubber / recycler view is present AND top toolbar has Screenshots
        val hasDateScrubber = root.findAccessibilityNodeInfosByViewId("com.google.android.apps.photos:id/photos_photogrid_date_scrubber_view").isNotEmpty()
        val hasAutoBackup = root.findAccessibilityNodeInfosByViewId("com.google.android.apps.photos:id/autobackup_folder_switch_text").isNotEmpty()
        if ((hasDateScrubber || hasAutoBackup) && screenshotNodes.isNotEmpty()) {
            return true
        }

        return false
    }

    /**
     * Robust navigation to the Screenshots album:
     * 1. If already in Screenshots, returns immediately.
     * 2. If stuck in photo viewer or search page, returns to main Collections page.
     * 3. Replays learned recipe if provided.
     * 4. Locates & clicks "Screenshots" folder container on Collections tab.
     * 5. Auto-scrolls Collections list if Screenshots is below the fold.
     * 6. Falls back to Search tab if needed.
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

        // Return from sub-views (like photo viewer or search subpage) if bottom tabs and screenshots album are absent
        for (attempt in 0..2) {
            if (isInsideScreenshotsAlbum(currentRoot)) return true
            val hasTabs = currentRoot.findAccessibilityNodeInfosByViewId("com.google.android.apps.photos:id/tab_collections").isNotEmpty() ||
                    currentRoot.findAccessibilityNodeInfosByViewId("com.google.android.apps.photos:id/tab_photos").isNotEmpty()
            if (hasTabs) break

            val upNodes = HierarchyCrawler.findNodesByRegex(currentRoot, Regex("(?i)navigate up"))
            if (upNodes.isNotEmpty()) {
                val rect = Rect()
                upNodes.first().getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    actionHelper.dispatchClick(rect.centerX().toFloat(), rect.centerY().toFloat())
                    actionHelper.waitForUiStabilization(800L, "Return to main screen")
                    currentRoot = actionHelper.getActiveWindowRoot() ?: currentRoot
                }
            }
        }

        if (isInsideScreenshotsAlbum(currentRoot)) return true

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
            actionHelper.waitForUiStabilization(1000L, "Locating Screenshots folder")
            currentRoot = actionHelper.getActiveWindowRoot() ?: currentRoot
        }

        if (isInsideScreenshotsAlbum(currentRoot)) return true

        val screenHeight = actionHelper.getScreenHeight()

        var scrollAttempts = 0
        val maxScrollAttempts = 6

        while (scrollAttempts <= maxScrollAttempts) {
            currentRoot = actionHelper.getActiveWindowRoot() ?: currentRoot

            // Check for Screenshots node
            val screenshotNodes = currentRoot.findAccessibilityNodeInfosByText("Screenshots")
            for (node in screenshotNodes) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                // We want the album row/tile on Collections (not the bottom tab or off-screen)
                if (rect.width() > 0 && rect.height() > 0 && rect.top in 100..(screenHeight - 120)) {
                    actionHelper.updateActionState("Found Screenshots folder", "Tap to open")

                    // Find clickable parent container (the album row)
                    var clickableNode: AccessibilityNodeInfo? = node
                    var curr: AccessibilityNodeInfo? = node
                    for (depth in 0..3) {
                        curr = curr?.parent ?: break
                        if (curr.isClickable) {
                            clickableNode = curr
                            break
                        }
                    }

                    val tapRect = Rect()
                    (clickableNode ?: node).getBoundsInScreen(tapRect)
                    val tapX = (tapRect.left + 250).coerceAtMost(tapRect.centerX()).toFloat()
                    val tapY = tapRect.centerY().toFloat()

                    FloatingHudService.showTouch(tapX, tapY, "👆 Tap 'Screenshots'")

                    clickableNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    actionHelper.dispatchClick(tapX, tapY)
                    actionHelper.waitForUiStabilization(1200L, "Verify Screenshots album")

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
            actionHelper.waitForUiStabilization(1000L, "Look for Screenshots in Search")
            currentRoot = actionHelper.getActiveWindowRoot() ?: currentRoot

            val searchScreenshots = currentRoot.findAccessibilityNodeInfosByText("Screenshots")
            for (node in searchScreenshots) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0 && rect.top in 100..(screenHeight - 120)) {
                    val tapX = (rect.left + 250).coerceAtMost(rect.centerX()).toFloat()
                    val tapY = rect.centerY().toFloat()
                    FloatingHudService.showTouch(tapX, tapY, "👆 Tap 'Screenshots'")
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    actionHelper.dispatchClick(tapX, tapY)
                    actionHelper.waitForUiStabilization(1200L, "Verify Screenshots album")
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
        context: android.content.Context,
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate,
        customRecipe: com.gigscope.auditor.domain.model.AppPhaseRecipe? = null
    ): List<PhotoOfferRecord> {
        val rawOffers = mutableListOf<PhotoOfferRecord>()

        // 1. Navigate to Screenshots album in Google Photos
        val reachedScreenshots = navigateToScreenshotsSection(root, customRecipe)
        if (reachedScreenshots) {
            FloatingHudService.showStatus("Screenshots Album Opened", 2000L)
            FloatingHudService.updateHud("📸 Screenshots Album Opened", "Scanning screenshots ($startDate to $endDate)...")
            actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Scan screenshot offers")
        } else {
            FloatingHudService.showStatus("Could not locate Screenshots album", 2500L)
            FloatingHudService.updateHud("⚠️ Photos: Not in Screenshots", "Searching local screenshots directly...")
        }

        // 2. High-accuracy on-device ML Kit OCR across screenshots in the date range
        actionHelper.updateActionState("Extracting Trip & Customer Info (OCR)", "Analyzing screenshots ($startDate to $endDate)")
        FloatingHudService.updateHud("🔍 OCR Extraction", "Reading trip offer cards ($startDate to $endDate)...")

        val ocrRecords = com.gigscope.auditor.service.ocr.ScreenshotOcrExtractor.findAndExtractScreenshots(
            context = context,
            startDate = startDate,
            endDate = endDate
        ) { current, total, offer ->
            val cust = offer?.customerName ?: "Offer"
            val totalStr = offer?.estimatedTotal?.let { "$$it" } ?: ""
            actionHelper.updateActionState("OCR Screenshot $current/$total: $cust $totalStr", "Extracting customer info")
            FloatingHudService.updateHud("🔍 OCR ($current/$total)", "$cust $totalStr")
        }

        rawOffers.addAll(ocrRecords)

        // 3. If no local files were accessible or matched, fallback to on-screen accessibility scraping
        if (rawOffers.isEmpty()) {
            var scrollCount = 0
            val maxScrolls = 15
            while (scrollCount < maxScrolls) {
                actionHelper.updateActionState(
                    "Scanning Photos ($scrollCount/$maxScrolls)",
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
                                captureDate = startDate,
                                timestamp = time,
                                imageUri = null,
                                isAccepted = false,
                                extractedText = allContent.take(1500),
                                estimatedTotal = total
                            )
                        )
                    }
                }
                val scrolled = actionHelper.performScrollForward(current)
                if (!scrolled) break
                scrollCount++
                actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Scan next photos")
            }
        }

        return rawOffers
    }
}
