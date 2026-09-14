package com.gigscope.auditor.service.automation

import android.view.accessibility.AccessibilityNodeInfo
import com.gigscope.auditor.domain.model.OnePayDeposit
import com.gigscope.auditor.domain.parser.HierarchyCrawler
import java.time.LocalDate

class OnePayAutomator(private val actionHelper: AccessibilityActionHelper) {

    private val sparkPayerRegex = Regex("""(?i)(Spark|Walmart|DDI|Delivery)""")
    private val depositAmountRegex = Regex("""\+\s*\$([0-9]+\.[0-9]{2})""")
    private val refIdRegex = Regex("""(?i)(?:Ref|Transaction)\s*(?:ID|#)?\s*[:#]?\s*([A-Za-z0-9\-]{8,24})""")
    private val timeRegex = Regex("""\b(\d{1,2}:\d{2}\s*(?:AM|PM|am|pm))\b""")
    private val tipIndicatorRegex = Regex("""(?i)(Tip|Customer\s+Tip|Gratuity)""")

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
            actionHelper.updateActionState("Opening Checking tab", "Open transaction activity")
            actionHelper.findAndClickByText(root, listOf("Checking", "One Checking", "Spend"))
            actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Open transaction activity")

            val currentWindow = actionHelper.getActiveWindowRoot() ?: return deposits
            actionHelper.updateActionState("Opening Activity list", "Scan deposits")
            actionHelper.findAndClickByText(currentWindow, listOf("Show all", "View all", "See all activity", "Activity"))
            actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Scan deposits")
        }

        // Scroll down pagination loop
        var reachedOlderDate = false
        var scrollCount = 0
        val maxScrolls = 30

        while (!reachedOlderDate && scrollCount < maxScrolls) {
            actionHelper.updateActionState(
                "Scanning OnePay (found ${deposits.size}, scroll $scrollCount/$maxScrolls)",
                if (scrollCount + 1 < maxScrolls) "Scroll down list" else "Finish scan"
            )
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
                actionHelper.updateActionState(
                    "Scrolling OnePay list (${scrollCount + 1}/$maxScrolls)",
                    "Scan next transactions"
                )
                val scrollSuccess = actionHelper.performScrollForward(window)
                if (!scrollSuccess) break
                scrollCount++
                actionHelper.waitForUiStabilization(actionHelper.getStabilizationDelay(), "Scan next transactions")
            }
        }

        return deposits
    }

    suspend fun collectTripEarnings(
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate,
        customRecipe: com.gigscope.auditor.domain.model.AppPhaseRecipe? = null
    ): List<OnePayDeposit> {
        val all = collectDeposits(root, startDate, endDate, customRecipe)
        return all.filter { it.transactionType == com.gigscope.auditor.domain.model.OnePayTransactionType.TRIP_EARNING }
    }

    suspend fun collectTipDeposits(
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate,
        customRecipe: com.gigscope.auditor.domain.model.AppPhaseRecipe? = null
    ): List<OnePayDeposit> {
        val all = collectDeposits(root, startDate, endDate, customRecipe)
        return all.filter { it.transactionType == com.gigscope.auditor.domain.model.OnePayTransactionType.TIP_DEPOSIT }
    }

    private fun parseOnePayTransaction(container: AccessibilityNodeInfo): OnePayDeposit? {
        val textDump = StringBuilder()
        HierarchyCrawler.findNodesByRegex(container, Regex(".*")).forEach {
            it.text?.let { t -> textDump.append(t).append(" | ") }
        }
        val content = textDump.toString()

        val amountNodes = HierarchyCrawler.findNodesByRegex(container, depositAmountRegex)
        val depositAmount = amountNodes.firstOrNull()?.let {
            val text = it.text?.toString() ?: it.contentDescription?.toString() ?: ""
            depositAmountRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        } ?: depositAmountRegex.find(content)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null

        val refIdNode = HierarchyCrawler.findNodesByRegex(container, refIdRegex).firstOrNull()
        val referenceId = refIdNode?.let {
            val text = it.text?.toString() ?: it.contentDescription?.toString() ?: ""
            refIdRegex.find(text)?.groupValues?.get(1)
        } ?: refIdRegex.find(content)?.groupValues?.get(1)
        ?: "OP-${System.currentTimeMillis()}-${(1000..9999).random()}"

        val timestamp = timeRegex.find(content)?.value

        val isTip = tipIndicatorRegex.containsMatchIn(content)
        val txType = if (isTip) {
            com.gigscope.auditor.domain.model.OnePayTransactionType.TIP_DEPOSIT
        } else {
            com.gigscope.auditor.domain.model.OnePayTransactionType.TRIP_EARNING
        }

        return OnePayDeposit(
            referenceId = referenceId,
            date = LocalDate.now(),
            amount = depositAmount,
            sender = "Spark Driver / Walmart",
            transactionType = txType,
            timestamp = timestamp,
            rawDescription = content
        )
    }
}
