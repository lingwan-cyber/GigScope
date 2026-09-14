package com.gigscope.auditor.service.automation

import android.view.accessibility.AccessibilityNodeInfo
import com.gigscope.auditor.domain.model.PhotoOfferRecord
import com.gigscope.auditor.domain.parser.HierarchyCrawler
import java.time.LocalDate

class GooglePhotosAutomator(private val actionHelper: AccessibilityActionHelper) {

    private val tripIdRegex = Regex("""(?:Trip|Order)[\s#:]*([A-Za-z0-9\-]{4,12})""", RegexOption.IGNORE_CASE)
    private val tipAmountRegex = Regex("""(?i)(?:Estimated\s+Tip|Tip)[:\s]*\$([0-9]+\.[0-9]{2})""")
    private val basePayRegex = Regex("""(?i)(?:Base\s+Pay|Delivery\s+Pay)[:\s]*\$([0-9]+\.[0-9]{2})""")

    suspend fun collectScreenshotOffers(
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<PhotoOfferRecord> {
        val rawOffers = mutableListOf<PhotoOfferRecord>()

        // 1. Navigate to Library or Albums
        actionHelper.findAndClickByText(root, listOf("Library", "Albums", "Collections"))
        actionHelper.waitForUiStabilization(500)

        // 2. Click "Screenshots"
        val activeWindow = actionHelper.getActiveWindowRoot() ?: return rawOffers
        actionHelper.findAndClickByText(activeWindow, listOf("Screenshots", "Screens"))
        actionHelper.waitForUiStabilization(500)

        // 3. Scan visible screenshot thumbnail / detail views
        val current = actionHelper.getActiveWindowRoot() ?: return rawOffers
        val textDump = StringBuilder()
        HierarchyCrawler.findNodesByRegex(current, Regex(".*")).forEach {
            it.text?.let { t -> textDump.append(t).append(" ") }
        }

        val allContent = textDump.toString()
        val tripId = tripIdRegex.find(allContent)?.groupValues?.get(1)
        val tip = tipAmountRegex.find(allContent)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val base = basePayRegex.find(allContent)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        if (tripId != null) {
            rawOffers.add(
                PhotoOfferRecord(
                    tripId = tripId,
                    offeredTip = tip,
                    basePay = base,
                    captureDate = LocalDate.now(),
                    imageUri = null,
                    isAccepted = false // Initially unconfirmed until matched against Spark completed trips
                )
            )
        }

        return rawOffers
    }
}
