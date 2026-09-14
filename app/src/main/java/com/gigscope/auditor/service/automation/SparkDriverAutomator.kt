package com.gigscope.auditor.service.automation

import android.view.accessibility.AccessibilityNodeInfo
import com.gigscope.auditor.domain.model.SparkCompletedTrip
import com.gigscope.auditor.domain.model.SparkEarningsBreakdown
import com.gigscope.auditor.domain.parser.HierarchyCrawler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class SparkDriverAutomator(private val actionHelper: AccessibilityActionHelper) {

    private val tripIdRegex = Regex("""(?:Trip|Order|OSN)[\s#:]*([A-Za-z0-9\-]{4,12})""", RegexOption.IGNORE_CASE)
    private val currencyRegex = Regex("""\$([0-9]+\.[0-9]{2})""")
    private val tipAnchorRegex = Regex("""(?i)(Customer\s+Tip|Estimated\s+Tip|Tip)""")
    private val basePayAnchorRegex = Regex("""(?i)(Base\s+Pay|Delivery\s+Pay|Spark\s+Pay)""")

    suspend fun navigateAndCollectTrips(
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate,
        customRecipe: com.gigscope.auditor.domain.model.AppPhaseRecipe? = null
    ): List<SparkCompletedTrip> {
        val collectedTrips = mutableListOf<SparkCompletedTrip>()

        // 1. Locate and click "Trips" Navigation Tab (via recorded steps or default semantic search)
        if (customRecipe != null && customRecipe.steps.isNotEmpty()) {
            actionHelper.executeRecordedNavigation(root, customRecipe.steps)
        } else {
            actionHelper.findAndClickByText(root, listOf("Trips", "Trip History", "Completed Trips"))
            actionHelper.waitForUiStabilization(500)
        }

        // 2. Autonomous Scroll & extract loop (continues scrolling until startDate is reached)
        var reachedPastStartDate = false
        var scrollAttempts = 0
        val maxScrolls = 25

        while (!reachedPastStartDate && scrollAttempts < maxScrolls) {
            val currentWindow = actionHelper.getActiveWindowRoot() ?: break
            val tripIdNodes = actionHelper.findNodesByPattern(currentWindow, tripIdRegex)

            for (node in tripIdNodes) {
                val card = actionHelper.findParentContainer(node, depth = 3) ?: node
                val trip = parseCompletedTripCard(card) ?: continue

                if (trip.tripDate.isBefore(startDate)) {
                    reachedPastStartDate = true
                    break
                }
                if (!trip.tripDate.isAfter(endDate) && collectedTrips.none { it.tripId == trip.tripId }) {
                    collectedTrips.add(trip)
                }
            }

            if (!reachedPastStartDate) {
                val scrolled = actionHelper.performScrollForward(currentWindow)
                if (!scrolled) break
                scrollAttempts++
                actionHelper.waitForUiStabilization(400)
            }
        }
        return collectedTrips
    }

    suspend fun navigateAndCollectEarnings(
        root: AccessibilityNodeInfo,
        targetTripIds: Set<String>,
        customRecipe: com.gigscope.auditor.domain.model.AppPhaseRecipe? = null
    ): List<SparkEarningsBreakdown> {
        // Navigate to "Earnings" (via recorded steps or default semantic search)
        if (customRecipe != null && customRecipe.steps.isNotEmpty()) {
            actionHelper.executeRecordedNavigation(root, customRecipe.steps)
        } else {
            actionHelper.findAndClickByText(root, listOf("Earnings", "Earnings History"))
            actionHelper.waitForUiStabilization(500)
        }

        val earningsList = mutableListOf<SparkEarningsBreakdown>()
        val currentWindow = actionHelper.getActiveWindowRoot() ?: return earningsList

        // Locate breakdowns for target trips
        val tripNodes = actionHelper.findNodesByPattern(currentWindow, tripIdRegex)
        for (node in tripNodes) {
            val text = node.text?.toString() ?: continue
            val tripId = tripIdRegex.find(text)?.groupValues?.get(1) ?: continue

            if (targetTripIds.contains(tripId)) {
                val card = actionHelper.findParentContainer(node, depth = 3) ?: node
                val tipStr = HierarchyCrawler.findValueAdjacentToLabel(card, tipAnchorRegex, currencyRegex)
                val baseStr = HierarchyCrawler.findValueAdjacentToLabel(card, basePayAnchorRegex, currencyRegex)

                val tip = tipStr?.replace("$", "")?.toDoubleOrNull() ?: 0.0
                val base = baseStr?.replace("$", "")?.toDoubleOrNull() ?: 0.0

                earningsList.add(
                    SparkEarningsBreakdown(
                        tripId = tripId,
                        date = LocalDate.now(),
                        basePay = base,
                        confirmedTip = tip,
                        totalEarnings = base + tip
                    )
                )
            }
        }
        return earningsList
    }

    private fun parseCompletedTripCard(card: AccessibilityNodeInfo): SparkCompletedTrip? {
        val textDump = StringBuilder()
        HierarchyCrawler.findNodesByRegex(card, Regex(".*")).forEach {
            it.text?.let { t -> textDump.append(t).append(" | ") }
        }
        val content = textDump.toString()
        val tripId = tripIdRegex.find(content)?.groupValues?.get(1) ?: return null

        val tipStr = HierarchyCrawler.findValueAdjacentToLabel(card, tipAnchorRegex, currencyRegex)
        val tip = tipStr?.replace("$", "")?.toDoubleOrNull() ?: 0.0

        // Extract customer stop details or address if available
        val customerDetails = if (content.contains("Stop", ignoreCase = true)) {
            content.split("|").firstOrNull { it.contains("Stop", ignoreCase = true) }?.trim()
        } else null

        return SparkCompletedTrip(
            tripId = tripId,
            tripDate = LocalDate.now(),
            initialOfferedTip = tip,
            customerDropDetails = customerDetails
        )
    }
}
