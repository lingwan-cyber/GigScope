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
    private val tipAnchorRegex = Regex("""(?i)(Customer\s+Tip|Estimated\s+Tip|Confirmed\s+Tip|Tip)""")
    private val basePayAnchorRegex = Regex("""(?i)(Base\s+Pay|Delivery\s+Pay|Spark\s+Pay)""")
    private val extraPayAnchorRegex = Regex("""(?i)(Extra\s+Earnings|Incentive|Bonus|Wait\s+Time|Surge)""")
    private val timeRegex = Regex("""\b(\d{1,2}:\d{2}\s*(?:AM|PM|am|pm))\b""")
    private val stopCountRegex = Regex("""(\d+)\s*(?:stops?|drop-?offs?|drops?)""", RegexOption.IGNORE_CASE)

    suspend fun navigateAndCollectTrips(
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate,
        customRecipe: com.gigscope.auditor.domain.model.AppPhaseRecipe? = null
    ): List<SparkCompletedTrip> {
        val collectedTrips = mutableListOf<SparkCompletedTrip>()

        // 1. Locate and click "Trips" Navigation Tab
        if (customRecipe != null && customRecipe.steps.isNotEmpty()) {
            actionHelper.executeRecordedNavigation(root, customRecipe.steps)
        } else {
            actionHelper.updateActionState("Opening Trips tab", "Scan trip cards")
            actionHelper.findAndClickByText(root, listOf("Trips", "Trip History", "Completed Trips"))
            actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Scan trip cards")
        }

        // 2. Autonomous Scroll & extract loop (continues scrolling until startDate is reached)
        var reachedPastStartDate = false
        var scrollAttempts = 0
        val maxScrolls = 25

        while (!reachedPastStartDate && scrollAttempts < maxScrolls) {
            actionHelper.updateActionState(
                "Scanning Trips (found ${collectedTrips.size}, scroll $scrollAttempts/$maxScrolls)",
                if (scrollAttempts + 1 < maxScrolls) "Scroll down list" else "Finish scan"
            )
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
                actionHelper.updateActionState(
                    "Scrolling Trips list (${scrollAttempts + 1}/$maxScrolls)",
                    "Scan next trip cards"
                )
                val scrolled = actionHelper.performScrollForward(currentWindow)
                if (!scrolled) break
                scrollAttempts++
                actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Scan next trip cards")
            }
        }
        return collectedTrips
    }

    suspend fun navigateAndCollectEarnings(
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate,
        targetTripIds: Set<String> = emptySet(),
        customRecipe: com.gigscope.auditor.domain.model.AppPhaseRecipe? = null
    ): List<SparkEarningsBreakdown> {
        // Navigate to "Earnings"
        if (customRecipe != null && customRecipe.steps.isNotEmpty()) {
            actionHelper.executeRecordedNavigation(root, customRecipe.steps)
        } else {
            actionHelper.updateActionState("Opening Earnings tab", "Scan earnings cards")
            actionHelper.findAndClickByText(root, listOf("Earnings", "Earnings History"))
            actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Scan earnings cards")
        }

        val earningsList = mutableListOf<SparkEarningsBreakdown>()
        var reachedPastStartDate = false
        var scrollAttempts = 0
        val maxScrolls = 25

        while (!reachedPastStartDate && scrollAttempts < maxScrolls) {
            actionHelper.updateActionState(
                "Scanning Earnings (found ${earningsList.size}, scroll $scrollAttempts/$maxScrolls)",
                if (scrollAttempts + 1 < maxScrolls) "Scroll down list" else "Finish scan"
            )
            val currentWindow = actionHelper.getActiveWindowRoot() ?: break
            val tripNodes = actionHelper.findNodesByPattern(currentWindow, tripIdRegex)

            for (node in tripNodes) {
                val card = actionHelper.findParentContainer(node, depth = 3) ?: node
                val earning = parseEarningsCard(card) ?: continue

                if (earning.date.isBefore(startDate)) {
                    reachedPastStartDate = true
                    break
                }

                val matchesTarget = targetTripIds.isEmpty() || targetTripIds.contains(earning.tripId)
                if (matchesTarget && !earning.date.isAfter(endDate) && earningsList.none { it.tripId == earning.tripId }) {
                    earningsList.add(earning)
                }
            }

            if (!reachedPastStartDate) {
                actionHelper.updateActionState(
                    "Scrolling Earnings list (${scrollAttempts + 1}/$maxScrolls)",
                    "Scan next earnings cards"
                )
                val scrolled = actionHelper.performScrollForward(currentWindow)
                if (!scrolled) break
                scrollAttempts++
                actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Scan next earnings cards")
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

        val timeMatch = timeRegex.find(content)?.value

        val stopCountMatch = stopCountRegex.find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 1

        val tripType = when {
            content.contains("Shop & Deliver", ignoreCase = true) || content.contains("Shopping", ignoreCase = true) -> "Shop & Deliver"
            content.contains("Curbside", ignoreCase = true) -> "Curbside Pickup"
            content.contains("Express", ignoreCase = true) -> "Express Delivery"
            content.contains("Return", ignoreCase = true) -> "Customer Return"
            else -> "Delivery"
        }

        // Extract customer drop details / addresses / customer names
        val customerDetails = content.split("|")
            .map { it.trim() }
            .filter { seg ->
                seg.contains("Stop", ignoreCase = true) ||
                        seg.contains("Drop", ignoreCase = true) ||
                        seg.contains("St", ignoreCase = true) ||
                        seg.contains("Ave", ignoreCase = true) ||
                        seg.contains("Rd", ignoreCase = true) ||
                        seg.contains("Dr", ignoreCase = true) ||
                        seg.contains("Blvd", ignoreCase = true)
            }
            .take(3)
            .joinToString(" • ")
            .ifBlank { null }

        val allCurrencies = currencyRegex.findAll(content).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        val maxCurrency = allCurrencies.maxOrNull()

        return SparkCompletedTrip(
            tripId = tripId,
            tripDate = LocalDate.now(),
            completedTime = timeMatch,
            tripType = tripType,
            stopCount = stopCountMatch,
            initialOfferedTip = tip,
            customerDropDetails = customerDetails,
            rawTotalEstimate = maxCurrency,
            rawText = content
        )
    }

    private fun parseEarningsCard(card: AccessibilityNodeInfo): SparkEarningsBreakdown? {
        val textDump = StringBuilder()
        HierarchyCrawler.findNodesByRegex(card, Regex(".*")).forEach {
            it.text?.let { t -> textDump.append(t).append(" | ") }
        }
        val content = textDump.toString()
        val tripId = tripIdRegex.find(content)?.groupValues?.get(1) ?: return null

        val tipStr = HierarchyCrawler.findValueAdjacentToLabel(card, tipAnchorRegex, currencyRegex)
        val baseStr = HierarchyCrawler.findValueAdjacentToLabel(card, basePayAnchorRegex, currencyRegex)
        val extraStr = HierarchyCrawler.findValueAdjacentToLabel(card, extraPayAnchorRegex, currencyRegex)

        val tip = tipStr?.replace("$", "")?.toDoubleOrNull() ?: 0.0
        val base = baseStr?.replace("$", "")?.toDoubleOrNull() ?: 0.0
        val extra = extraStr?.replace("$", "")?.toDoubleOrNull() ?: 0.0
        val total = base + tip + extra

        val timeMatch = timeRegex.find(content)?.value

        return SparkEarningsBreakdown(
            tripId = tripId,
            date = LocalDate.now(),
            basePay = base,
            confirmedTip = tip,
            extraEarnings = extra,
            totalEarnings = total,
            timestamp = timeMatch,
            rawEarningDetails = content
        )
    }
}
