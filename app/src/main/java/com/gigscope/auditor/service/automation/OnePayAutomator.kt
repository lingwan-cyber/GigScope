package com.gigscope.auditor.service.automation

import android.view.accessibility.AccessibilityNodeInfo
import com.gigscope.auditor.domain.model.OnePayDeposit
import com.gigscope.auditor.domain.parser.HierarchyCrawler
import java.time.LocalDate

class OnePayAutomator(private val actionHelper: AccessibilityActionHelper) {

    private val sparkPayerRegex = Regex("""(?i)(Spark|Walmart|DDI)""")
    private val depositAmountRegex = Regex("""\+\s*\$([0-9]+\.[0-9]{2})""")
    private val refIdRegex = Regex("""(?i)(?:Ref|Transaction)\s*(?:ID|#)?\s*[:#]?\s*([A-Za-z0-9\-]{8,24})""")

    suspend fun collectDeposits(
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate,
        customRecipe: com.gigscope.auditor.domain.model.AppPhaseRecipe? = null
    ): List<OnePayDeposit> {
        val deposits = mutableListOf<OnePayDeposit>()

        // 1. Navigate to "Checking" -> "Show all" (via recorded steps or default semantic search)
        if (customRecipe != null && customRecipe.steps.isNotEmpty()) {
            actionHelper.executeRecordedNavigation(root, customRecipe.steps)
        } else {
            actionHelper.findAndClickByText(root, listOf("Checking", "One Checking", "Spend"))
            actionHelper.waitForUiStabilization(500)

            val currentWindow = actionHelper.getActiveWindowRoot() ?: return deposits
            actionHelper.findAndClickByText(currentWindow, listOf("Show all", "View all", "See all activity", "Activity"))
            actionHelper.waitForUiStabilization(500)
        }

        // 3. Scroll down pagination loop
        var reachedOlderDate = false
        var scrollCount = 0
        val maxScrolls = 30

        while (!reachedOlderDate && scrollCount < maxScrolls) {
            val window = actionHelper.getActiveWindowRoot() ?: break
            val txNodes = actionHelper.findNodesByPattern(window, sparkPayerRegex)

            for (node in txNodes) {
                val container = actionHelper.findParentContainer(node, depth = 3) ?: node
                val deposit = parseOnePayTransaction(container) ?: continue

                if (deposit.date.isBefore(startDate)) {
                    reachedOlderDate = true
                    break
                }

                if (!deposit.date.isAfter(endDate) && deposits.none { it.referenceId == deposit.referenceId }) {
                    deposits.add(deposit)
                }
            }

            if (!reachedOlderDate) {
                val scrollSuccess = actionHelper.performScrollForward(window)
                if (!scrollSuccess) break
                scrollCount++
                actionHelper.waitForUiStabilization(400)
            }
        }

        return deposits
    }

    private fun parseOnePayTransaction(container: AccessibilityNodeInfo): OnePayDeposit? {
        val amountNodes = HierarchyCrawler.findNodesByRegex(container, depositAmountRegex)
        val depositAmount = amountNodes.firstOrNull()?.let {
            val text = it.text?.toString() ?: it.contentDescription?.toString() ?: ""
            depositAmountRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        } ?: return null

        val refIdNode = HierarchyCrawler.findNodesByRegex(container, refIdRegex).firstOrNull()
        val referenceId = refIdNode?.let {
            val text = it.text?.toString() ?: it.contentDescription?.toString() ?: ""
            refIdRegex.find(text)?.groupValues?.get(1)
        } ?: "OP-${System.currentTimeMillis()}-${(1000..9999).random()}"

        return OnePayDeposit(
            referenceId = referenceId,
            date = LocalDate.now(),
            amount = depositAmount,
            sender = "Spark Driver / Walmart"
        )
    }
}
