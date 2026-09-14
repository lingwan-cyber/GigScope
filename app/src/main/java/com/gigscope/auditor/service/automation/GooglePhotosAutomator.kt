package com.gigscope.auditor.service.automation

import android.view.accessibility.AccessibilityNodeInfo
import com.gigscope.auditor.domain.model.PhotoOfferRecord
import com.gigscope.auditor.domain.parser.HierarchyCrawler
import java.time.LocalDate

class GooglePhotosAutomator(private val actionHelper: AccessibilityActionHelper) {

    private val tripIdRegex = Regex("""(?:Trip|Order)[\s#:]*([A-Za-z0-9\-]{4,12})""", RegexOption.IGNORE_CASE)
    private val tipAmountRegex = Regex("""(?i)(?:Estimated\s+Tip|Customer\s+Tip|Tip)[:\s]*\$([0-9]+\.[0-9]{2})""")
    private val basePayRegex = Regex("""(?i)(?:Base\s+Pay|Delivery\s+Pay|Spark\s+Pay)[:\s]*\$([0-9]+\.[0-9]{2})""")
    private val totalPayRegex = Regex("""(?i)(?:Estimated\s+Total|Total)[:\s]*\$([0-9]+\.[0-9]{2})""")
    private val timeRegex = Regex("""\b(\d{1,2}:\d{2}\s*(?:AM|PM|am|pm))\b""")

    suspend fun collectScreenshotOffers(
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate,
        customRecipe: com.gigscope.auditor.domain.model.AppPhaseRecipe? = null
    ): List<PhotoOfferRecord> {
        val rawOffers = mutableListOf<PhotoOfferRecord>()

        // 1. Navigate to Screenshots album
        if (customRecipe != null && customRecipe.steps.isNotEmpty()) {
            actionHelper.executeRecordedNavigation(root, customRecipe.steps)
        } else {
            actionHelper.findAndClickByText(root, listOf("Library", "Albums", "Collections"))
            actionHelper.waitForUiStabilization(500)

            val activeWindow = actionHelper.getActiveWindowRoot() ?: return rawOffers
            actionHelper.findAndClickByText(activeWindow, listOf("Screenshots", "Screens"))
            actionHelper.waitForUiStabilization(500)
        }

        // 2. Autonomous Scroll & extract screenshot offer details
        var scrollCount = 0
        val maxScrolls = 20

        while (scrollCount < maxScrolls) {
            val current = actionHelper.getActiveWindowRoot() ?: break
            val textDump = StringBuilder()
            HierarchyCrawler.findNodesByRegex(current, Regex(".*")).forEach {
                it.text?.let { t -> textDump.append(t).append(" | ") }
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

            val scrolled = actionHelper.performScrollForward(current)
            if (!scrolled) break
            scrollCount++
            actionHelper.waitForUiStabilization(400)
        }

        return rawOffers
    }
}
