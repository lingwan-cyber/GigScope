# Technical Specification: GigScope (Gig Worker Earnings & Tip Auditing Engine)

## 1. Executive Summary & Architecture Overview

### 1.1 Objective
**GigScope** is an Android-based personal auditing and reconciliation tool designed specifically for gig economy contractors (such as Walmart Spark Driver delivery drivers). The system captures, validates, and correlates compensation data across three primary on-device touchpoints:
1. **Spark Driver App**:
   - **"Trips" Section**: Captures historical completed delivery records, trip identifiers, stops/addresses, and order acceptance records.
   - **"Earnings" Section**: Captures detailed financial breakdowns (base pay, confirmed customer tips, tip adjustments, and weekly/daily payouts).
2. **OnePay (One Finance) App**:
   - **"Checking" -> "Activity"**: Accesses the transaction ledger via the **"Show all"** action and automated scroll-down pagination to capture immediate base pay payouts and delayed tip deposits.
3. **Google Photos App**:
   - **"Screenshots" Album**: Inspects screenshot proofs of initial offers. Because drivers take screenshots indiscriminately (capturing offers accepted as well as rejected/expired offers), the system cross-references screenshot records against completed Spark trips.

The platform provides a **user-configurable date range filter**, a **vertical 3-app dashboard with modular isolated test harnesses**, and an **automated comparison engine** that identifies exact customer instances where tips were reduced or baited.

---

### 1.2 System Architecture Diagram

```
+-----------------------------------------------------------------------------------------------+
|                                     Android OS Environment                                    |
|                                                                                               |
|  +------------------------+  +------------------------+  +---------------------------------+  |
|  |    Spark Driver App    |  |       OnePay App       |  |        Google Photos App        |  |
|  |  [Trips] & [Earnings]  |  | [Checking]->[Activity] |  |     [Screenshots Album]         |  |
|  +-----------+------------+  +-----------+------------+  +----------------+----------------+  |
|              |                           |                                |                   |
|              | (Auto-Nav & Read)         | (Click "Show all" & Scroll)    | (Scan & OCR)      |
|              +---------------------------+--------------------------------+                   |
|                                          |                                                    |
|                                          v                                                    |
|                     [ AccessibilityEvent & Node Hierarchy Stream ]                            |
|                                          |                                                    |
+------------------------------------------|----------------------------------------------------+
                                           |
+------------------------------------------v----------------------------------------------------+
| GigScope Application Context                                                                  |
|                                                                                               |
|  +-----------------------------------------------------------------------------------------+  |
|  |                                UI & Test Harness Layer                                  |  |
|  |  +-----------------------------------------------------------------------------------+  |  |
|  |  | [Date Range Selector]: [ Start Date: 2026-09-01 ] to [ End Date: 2026-09-12 ]     |  |  |
|  |  +-----------------------------------------------------------------------------------+  |  |
|  |  | App 1: Spark Driver  --> [ Test Traversal & Collect ] -> [ Status: 18 Trips Found ]  |  |  |
|  |  | App 2: OnePay        --> [ Test Traversal & Collect ] -> [ Status: 22 Deposits ]     |  |  |
|  |  | App 3: Google Photos --> [ Test Traversal & Collect ] -> [ Status: 35 Proofs Read ]  |  |  |
|  |  +-----------------------------------------------------------------------------------+  |  |
|  |  |                       [ RUN FULL RECONCILIATION AUDIT ]                           |  |  |
|  |  +-----------------------------------------------------------------------------------+  |  |
|  +-------------------------------------------+---------------------------------------------+  |
|                                              |                                                |
|                                              v                                                |
|  +-----------------------------------------------------------------------------------------+  |
|  |                          GigScopeAccessibilityService Controller                        |  |
|  |  - Automated Action Sequencer (Click, Scroll Forward, Back, Find-by-Text)                |  |
|  |  - Active App Target Context Switcher (Isolated Test Mode vs. Full Orchestration)        |  |
|  |  - Floating HUD Progress Overlay (User feedback during foreground traversal)             |  |
|  +-------------------------------------------+---------------------------------------------+  |
|                                              |                                                |
|                                              v                                                |
|  +-----------------------------------------------------------------------------------------+  |
|  |                         Application Scrapers & Parsers                                  |  |
|  |  +------------------------+ +------------------------+ +-------------------------------+  |  |
|  |  |   SparkTripsEarnings   | |     OnePayActivity     | |     GooglePhotosScreenshot    |  |  |
|  |  |        Parser          | |     Scroll Crawler     | |      Offer Filter & Parser    |  |  |
|  |  +-----------+------------+ +-----------+------------+ +---------------+---------------+  |  |
|  +--------------|--------------------------|------------------------------|----------------+  |
|                 v                          v                              v                   |
|  +-----------------------------------------------------------------------------------------+  |
|  |                    Encrypted Local Database (Room with SQLCipher)                       |  |
|  |  - spark_completed_trips       - onepay_deposits       - screenshot_offers              |  |
|  +-------------------------------------------+---------------------------------------------+  |
|                                              |                                                |
|                                              v                                                |
|  +-----------------------------------------------------------------------------------------+  |
|  |                       Reconciliation & Tip-Baiting Engine                               |  |
|  |  1. Filter Screenshots: Keep ONLY screenshots matching accepted trip IDs/dates          |  |
|  |  2. Correlate Spark Earnings breakdown (Base Pay vs Final Tip) with OnePay deposits     |  |
|  |  3. Detect: (Final Tip < Initial Offered Tip) -> Tag specific customer/stop & loss      |  |
|  +-----------------------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------------------+
```

---

## 2. User Interface & Test Harness Specification

### 2.1 UI Wireframe Layout (Vertical App Hierarchy)

The main dashboard features a Material 3 TopAppBar with a **Hamburger Menu** (`[ ☰ ]`) providing access to **Load Config**, **Save Config**, and **About** (modeled after the GM reference app), followed by a date range filter and vertically arranged target application cards. Each card provides:
1. An interactive **"Select from App Drawer / APK"** button with a launcher app picker modal, live icons, and `.apk` fallback.
2. An **APK path/name text field** with inline clear `(X)`, automatic focus dismissal on Enter / tap-outside, and an **"Apply"** button.
3. An isolated full-width **"Test Traversal"** button allowing the user to verify navigation and data collection on each app independently before running the end-to-end audit.

```
+-------------------------------------------------------------+
| [ ☰ ]  GIGSCOPE AUDITOR                                     |
|  |-> [📂 Load Config]                                        |
|  |-> [💾 Save Config]                                        |
|  +-> [ℹ️ About]                                              |
+-------------------------------------------------------------+
| [DATE RANGE FILTER]                                         |
|  +---------------------------+ +--------------------------+ |
|  | Start Date: 2026-09-01 📅 | | End Date:  2026-09-13 📅 | |
|  +---------------------------+ +--------------------------+ |
|  Quick Presets: [ Last 7d ]    [ Last 14d ]    [ Last 30d ] |
+-------------------------------------------------------------+
|                                                             |
| [1] SPARK DRIVER                                            |
|     Default: com.walmart.sparkdriver                        |
|     Target: "Trips" history & "Earnings" breakdown          |
|     Status: [ READY ]  | [✓ Learned Recipe Active]          |
|     +-----------------------------------------------------+ |
|     |  [ 📱 SELECT FROM APP DRAWER / APK ]                | |
|     +-----------------------------------------------------+ |
|     |  APK: [ /data/app/.../base.apk          (X) ] [Apply] | |
|     +-----------------------------------------------------+ |
|     |  [ 🎥 Teach / Record ]  [ ▶️ Test Spark Traversal ]   | |
|     +-----------------------------------------------------+ |
|                                                             |
| [2] ONEPAY (ONE FINANCE)                                    |
|     Default: com.onefinance.one                             |
|     Target: "Checking" -> "Activity" -> "Show all" (Scroll) |
|     Status: [ READY ]  | Standard Semantic Traversal        |
|     +-----------------------------------------------------+ |
|     |  [ 📱 SELECT FROM APP DRAWER / APK ]                | |
|     +-----------------------------------------------------+ |
|     |  APK: [ /data/app/.../base.apk          (X) ] [Apply] | |
|     +-----------------------------------------------------+ |
|     |  [ 🎥 Teach / Record ]  [ ▶️ Test OnePay Traversal ] | |
|     +-----------------------------------------------------+ |
|                                                             |
| [3] GOOGLE PHOTOS                                           |
|     Default: com.google.android.apps.photos                 |
|     Target: "Screenshots" Album (Offer Cards)               |
|     Status: [ READY ]  | Standard Semantic Traversal        |
|     +-----------------------------------------------------+ |
|     |  [ 📱 SELECT FROM APP DRAWER / APK ]                | |
|     +-----------------------------------------------------+ |
|     |  APK: [ /data/app/.../base.apk          (X) ] [Apply] | |
|     +-----------------------------------------------------+ |
|     |  [ 🎥 Teach / Record ]  [ ▶️ Test Photos Traversal ] | |
|     +-----------------------------------------------------+ |
|                                                             |
+-------------------------------------------------------------+
|  +-------------------------------------------------------+  |
|  |           [ RUN FULL RECONCILIATION AUDIT ]           |  |
|  +-------------------------------------------------------+  |
+-------------------------------------------------------------+
| [AUDIT RESULTS & REDUCED TIPS]                              |
|  ! Alert: 2 Tip Reductions Identified (Total Loss: -$14.50) |
|                                                             |
|  - Trip #482910 | Sep 10, 2026 | Stop 2: Customer "Emily R."|
|    Offered Tip: $10.00 -> Final Tip: $2.00 | Loss: -$8.00   |
|    Proof: Screenshot_20260910-110214.png [View Card]        |
|                                                             |
|  - Trip #481022 | Sep 08, 2026 | Stop 1: Customer "Mark T." |
|    Offered Tip: $6.50  -> Final Tip: $0.00 | Loss: -$6.50   |
|    Proof: Screenshot_20260908-164510.png [View Card]        |
+-------------------------------------------------------------+
```

---

### 2.2 Isolated Test Harness Architecture

To ensure reliability, each app scraper can run in **Test Mode**:
1. **User Action**: The user taps `TEST SPARK TRAVERSAL`, `TEST ONEPAY TRAVERSAL`, or `TEST PHOTOS TRAVERSAL`.
2. **App Launch & HUD Attachment**:
   - GigScope launches the target application via `Intent.FLAG_ACTIVITY_NEW_TASK`.
   - GigScope attaches an interactive, non-blocking **Floating Window / HUD** (`WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`) over the screen.
   - The HUD displays real-time execution steps: e.g., *"Step 1: Locating 'Checking' tab... Step 2: Clicking 'Show all'... Step 3: Scrolling page 3 (Collected 12 items)..."*.
   - A floating **"Stop Test"** button allows the user to immediately abort.
3. **Execution & Return**:
   - Once all transactions within the user's date range are collected (or the user cancels), GigScope automatically returns to the foreground.
   - A **Test Results Modal** presents the collected records in a preview table, reporting parsed fields, total counts, and error diagnostics.

```kotlin
package com.gigscope.auditor.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun AppTargetCard(
    appName: String,
    targetDescription: String,
    statusText: String,
    isTesting: Boolean,
    onTestClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = appName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = targetDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Status: $statusText",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onTestClick,
                enabled = !isTesting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing Traversal...")
                } else {
                    Text("Test $appName Traversal")
                }
            }
        }
    }
}
```

---

## 3. Automated Navigation & Scraping Implementation

### 3.1 Spark Driver Automation: "Trips" & "Earnings" Navigation

Spark Driver maintains two essential tabs:
- **"Trips" Tab**: Contains historical trip cards showing trip identifiers, completion timestamps, delivery addresses, and customer drop details.
- **"Earnings" Tab**: Contains financial breakdowns for each completed trip, detailing Base Pay and Confirmed Customer Tips.

#### Programmatic Traversal Workflow
```
[Launch Spark Driver]
       |
       v
[Find & Click "Trips" Tab/Menu]
       |
       +---> [Traverse Completed Trips List]
       |     - Scrape Trip ID, Date, Address/Customer Name
       |     - Filter by Date Range [StartDate, EndDate]
       |     - Stop scrolling when item date < StartDate
       |
       v
[Find & Click "Earnings" Tab/Menu]
       |
       +---> [Traverse Earnings List & Open Details]
             - Scrape Trip ID, Base Pay, Final Confirmed Tip
             - Check for "Tip Adjusted by Customer" indicators
```

```kotlin
package com.gigscope.auditor.service.automation

import android.view.accessibility.AccessibilityNodeInfo
import com.gigscope.auditor.domain.model.SparkCompletedTrip
import com.gigscope.auditor.domain.model.SparkEarningsBreakdown
import java.time.LocalDate

class SparkDriverAutomator(private val actionHelper: AccessibilityActionHelper) {

    suspend fun navigateAndCollectTrips(
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<SparkCompletedTrip> {
        val collectedTrips = mutableListOf<SparkCompletedTrip>()

        // 1. Locate and click "Trips" Navigation Tab
        actionHelper.findAndClickByText(root, listOf("Trips", "Trip History", "Completed Trips"))
        actionHelper.waitForUiStabilization()

        // 2. Continuous scroll & extract loop
        var reachedPastStartDate = false
        var scrollAttempts = 0
        val maxScrolls = 25

        while (!reachedPastStartDate && scrollAttempts < maxScrolls) {
            val currentWindow = actionHelper.getActiveWindowRoot() ?: break
            val tripCards = actionHelper.findNodesByClass(currentWindow, "androidx.cardview.widget.CardView")

            for (card in tripCards) {
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
                actionHelper.waitForUiStabilization()
            }
        }
        return collectedTrips
    }

    suspend fun navigateAndCollectEarnings(
        root: AccessibilityNodeInfo,
        targetTripIds: Set<String>
    ): List<SparkEarningsBreakdown> {
        // 1. Navigate to "Earnings"
        actionHelper.findAndClickByText(root, listOf("Earnings", "Earnings History"))
        actionHelper.waitForUiStabilization()

        val earningsList = mutableListOf<SparkEarningsBreakdown>()
        // 2. Expand daily/trip breakdowns and scrape Base Pay + Final Tips matching targetTripIds
        // ... (iterates and clicks into detail views)
        return earningsList
    }

    private fun parseCompletedTripCard(card: AccessibilityNodeInfo): SparkCompletedTrip? {
        // Extracts Trip ID, Customer Name/Address, Date, and status
        return null // Implemented using HierarchyCrawler regex matching
    }
}
```

---

### 3.2 OnePay Automation: "Checking" -> "Activity" -> "Show all" with Auto-Scroll

OnePay lists real-time deposits under the Checking account activity. However, initial screen rendering only shows the last 3–5 transactions. The automator must click **"Show all"** and execute forward scroll actions until it reaches the user-specified `startDate`.

#### Programmatic Traversal Workflow
```
[Launch OnePay]
       |
       v
[Find & Click "Checking" Card/Tab]
       |
       v
[Scroll to "Activity" Section]
       |
       v
[Find & Click "Show all" / "View All Activity"]
       |
       v
+---> [Read Visible Transactions]
|     - Check: Sender == "Walmart" / "Spark Driver" / "DDI"
|     - Extract: Deposit Amount, Date, Reference ID
|     - If Transaction Date < StartDate -> STOP SCROLLING
|     - Else -> [Perform ACTION_SCROLL_FORWARD] -> Loop
+------
```

```kotlin
package com.gigscope.auditor.service.automation

import android.view.accessibility.AccessibilityNodeInfo
import com.gigscope.auditor.domain.model.OnePayDeposit
import java.time.LocalDate

class OnePayAutomator(private val actionHelper: AccessibilityActionHelper) {

    suspend fun collectDeposits(
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<OnePayDeposit> {
        val deposits = mutableListOf<OnePayDeposit>()

        // Step 1: Click "Checking"
        actionHelper.findAndClickByText(root, listOf("Checking", "One Checking", "Spend"))
        actionHelper.waitForUiStabilization()

        // Step 2: Locate "Show all" under Activity
        val currentWindow = actionHelper.getActiveWindowRoot() ?: return deposits
        actionHelper.findAndClickByText(currentWindow, listOf("Show all", "View all", "See all activity"))
        actionHelper.waitForUiStabilization()

        // Step 3: Scroll pagination loop with date-boundary check
        var reachedOlderDate = false
        var scrollCount = 0
        val maxScrolls = 30

        while (!reachedOlderDate && scrollCount < maxScrolls) {
            val window = actionHelper.getActiveWindowRoot() ?: break
            val txNodes = actionHelper.findNodesByPattern(window, Regex("""(?i)(Spark|Walmart|DDI)"""))

            for (node in txNodes) {
                val container = actionHelper.findParentContainer(node, depth = 3) ?: continue
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
                actionHelper.waitForUiStabilization(timeoutMs = 400)
            }
        }

        return deposits
    }

    private fun parseOnePayTransaction(container: AccessibilityNodeInfo): OnePayDeposit? {
        // Extracts amount (+$XX.XX), date, and reference string
        return null // Implemented via HierarchyCrawler
    }
}
```

---

### 3.3 Google Photos Automation: "Screenshots" Album & Unaccepted Offer Filtering

Drivers take screenshots of offer cards while on the road. A critical challenge is that **the Screenshots album contains both offers the driver accepted and completed, as well as offers the driver declined or missed.**

#### Programmatic Traversal & Ingestion Workflow
```
[Launch Google Photos]
       |
       v
[Find & Click "Library" or "Albums" Tab]
       |
       v
[Find & Click "Screenshots" Album]
       |
       v
[Traverse Screenshots in Date Range [StartDate, EndDate]]
       |
       v
[Extract: Trip ID, Base Pay, Offered Tip, Delivery Addresses]
       |
       v
[CROSS-FILTERING STEP]:
Check against Spark Driver "Trips" database:
  - DOES Trip ID exist in completed spark_completed_trips?
  - YES  --> STORE in screenshot_offers (Marked as ACCEPTED_OFFER)
  - NO   --> DISCARD or FLAG as UNACCEPTED_OFFER (Ignore in tip reduction math)
```

```kotlin
package com.gigscope.auditor.service.automation

import android.view.accessibility.AccessibilityNodeInfo
import com.gigscope.auditor.domain.model.PhotoOfferRecord
import java.time.LocalDate

class GooglePhotosAutomator(private val actionHelper: AccessibilityActionHelper) {

    suspend fun collectScreenshotOffers(
        root: AccessibilityNodeInfo,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<PhotoOfferRecord> {
        val rawOffers = mutableListOf<PhotoOfferRecord>()

        // 1. Navigate to Library/Albums
        actionHelper.findAndClickByText(root, listOf("Library", "Albums", "Collections"))
        actionHelper.waitForUiStabilization()

        // 2. Click "Screenshots"
        val activeWindow = actionHelper.getActiveWindowRoot() ?: return rawOffers
        actionHelper.findAndClickByText(activeWindow, listOf("Screenshots", "Screens"))
        actionHelper.waitForUiStabilization()

        // 3. Scan screenshot thumbnails & inspect OCR/detail nodes
        // Iterate through items within the specified date boundaries
        return rawOffers
    }
}
```

---

### 3.4 Accessibility Action Helper (Click & Scroll Core)

To make traversal reliable across all three apps, `AccessibilityActionHelper` encapsulates node searches, robust click dispatching, scroll commands, and stabilization delays.

```kotlin
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
}
```

---

## 4. Date-Bounded Reconciliation & Reduced Tip Comparison Engine

### 4.1 Cross-Validation Model & Filtering Logic

```
   +------------------------------+     +-------------------------------+
   |   Google Photos Screenshots  |     |      Spark Driver "Trips"     |
   | (May include unaccepted trips) |     |  (Confirmed trips driver took)|
   +--------------+---------------+     +---------------+---------------+
                  |                                     |
                  |                [ INNER JOIN ]       |
                  +--------------> on Trip ID <---------+
                                        |
                                        v
                          +----------------------------+
                          | Matched Accepted Offers    |
                          | (Initial Offered Tip, OSN) |
                          +--------------+-------------+
                                         |
                                         v
                         [ Compare with Spark "Earnings" ]
                         [     and OnePay Deposits       ]
                                         |
                                         v
                     +---------------------------------------+
                     |        Settlement Evaluation          |
                     |  Offered Tip: $10.00                  |
                     |  Final Received Tip: $2.00            |
                     |  Delta: -$8.00 (REDUCED TIP DETECTED) |
                     +---------------------------------------+
```

### 4.2 Mathematical Tip Reduction Rules
For every trip $T_i$ within the configured date range $[D_{start}, D_{end}]$:

1. **Verify Trip Acceptance**:
   $$\text{IsAccepted}(T_i) = T_i \in \text{SparkCompletedTrips}$$
   If $\text{IsAccepted}(T_i) = \text{false}$, the screenshot record is discarded from the reduction analysis.

2. **Establish Baseline Offered Tip ($Tip_{offered}$)**:
   $$Tip_{offered}(T_i) = \begin{cases}
   Tip_{photo}(T_i), & \text{if screenshot proof exists and } Tip_{photo} > 0 \\
   Tip_{spark\_offer}(T_i), & \text{otherwise}
   \end{cases}$$

3. **Resolve Final Cleared Tip ($Tip_{final}$)**:
   $$Tip_{final}(T_i) = \begin{cases}
   Tip_{spark\_earnings}(T_i), & \text{if earnings record finalized} \\
   \text{Deposit}_{onepay}(T_i, t \in [T_{completed} + 24h, T_{completed} + 48h]), & \text{if OnePay tip payout matched} \\
   0.00, & \text{if } t > T_{completed} + 48h \text{ and no tip posted}
   \end{cases}$$

4. **Calculate Tip Delta ($\Delta Tip$)**:
   $$\Delta Tip(T_i) = Tip_{final}(T_i) - Tip_{offered}(T_i)$$

5. **Discrepancy Classification**:
   $$\text{Status}(T_i) = \begin{cases}
   \mathbf{CONFIRMED\_MATCH}, & \text{if } |\Delta Tip| \le 0.01 \\
   \mathbf{TIP\_INCREASE}, & \text{if } \Delta Tip > 0.01 \\
   \mathbf{PARTIAL\_REDUCTION}\text{ (Tip Baited)}, & \text{if } -Tip_{offered} < \Delta Tip < -0.01 \\
   \mathbf{FULL\_REMOVAL}\text{ (Zeroed Out)}, & \text{if } Tip_{final} \le 0.01 \text{ and } Tip_{offered} > 0.01
   \end{cases}$$

---

### 4.3 Kotlin Reconciliation Engine Implementation

```kotlin
package com.gigscope.auditor.domain.reconciliation

import com.gigscope.auditor.domain.model.*
import java.time.LocalDate

data class CustomerTipDiscrepancy(
    val tripId: String,
    val date: LocalDate,
    val customerIdentifier: String, // e.g. "Stop 2 - 742 Evergreen Terr (Emily R.)"
    val offeredTip: Double,
    val finalReceivedTip: Double,
    val lossAmount: Double,
    val discrepancyType: DiscrepancyType,
    val screenshotProofUri: String?
)

enum class DiscrepancyType {
    CONFIRMED_MATCH,
    TIP_INCREASE,
    PARTIAL_REDUCTION,
    FULL_REMOVAL
}

class ReconciliationService {

    fun analyzeDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
        completedTrips: List<SparkCompletedTrip>,
        earningsBreakdowns: List<SparkEarningsBreakdown>,
        onePayDeposits: List<OnePayDeposit>,
        screenshotOffers: List<PhotoOfferRecord>
    ): List<CustomerTipDiscrepancy> {
        val discrepancies = mutableListOf<CustomerTipDiscrepancy>()

        // Step 1: Filter completed trips within user date boundaries
        val inScopeTrips = completedTrips.filter { trip ->
            !trip.tripDate.isBefore(startDate) && !trip.tripDate.isAfter(endDate)
        }

        // Create fast lookup maps
        val acceptedTripIds = inScopeTrips.map { it.tripId }.toSet()
        val earningsMap = earningsBreakdowns.associateBy { it.tripId }

        // Step 2: Filter screenshots - ONLY consider offers for trips the driver actually took
        val validScreenshotOffers = screenshotOffers
            .filter { acceptedTripIds.contains(it.tripId) }
            .associateBy { it.tripId }

        for (trip in inScopeTrips) {
            // Step 3: Determine initial offered tip
            val photoProof = validScreenshotOffers[trip.tripId]
            val offeredTip = photoProof?.offeredTip ?: trip.initialOfferedTip

            if (offeredTip <= 0.0) {
                // Trip had no tip initially; cannot be tip-baited
                continue
            }

            // Step 4: Determine final received tip
            val sparkEarnings = earningsMap[trip.tripId]
            val finalTip = when {
                sparkEarnings?.confirmedTip != null -> sparkEarnings.confirmedTip
                else -> {
                    // Fallback to secondary OnePay deposit search around T + 24 hours
                    val expectedDeposit = onePayDeposits.firstOrNull { deposit ->
                        deposit.matchedTripId == trip.tripId || 
                        (deposit.date == trip.tripDate.plusDays(1) && deposit.amount <= offeredTip)
                    }
                    expectedDeposit?.amount ?: 0.0
                }
            }

            // Step 5: Delta calculation
            val delta = finalTip - offeredTip

            if (delta < -0.01) {
                val isFullRemoval = finalTip <= 0.01
                discrepancies.add(
                    CustomerTipDiscrepancy(
                        tripId = trip.tripId,
                        date = trip.tripDate,
                        customerIdentifier = trip.customerDropDetails ?: "Customer on Trip #${trip.tripId}",
                        offeredTip = offeredTip,
                        finalReceivedTip = finalTip,
                        lossAmount = kotlin.math.abs(delta),
                        discrepancyType = if (isFullRemoval) DiscrepancyType.FULL_REMOVAL else DiscrepancyType.PARTIAL_REDUCTION,
                        screenshotProofUri = photoProof?.imageUri
                    )
                )
            }
        }

        return discrepancies.sortedByDescending { it.lossAmount }
    }
}
```

---

## 5. Summary & Verification Checklist

| Milestone | Deliverable | Verification Criteria |
| :--- | :--- | :--- |
| **Date Range Selector** | Date Range UI with Material DatePicker & presets | Restricts queries to between `startDate` and `endDate`. |
| **Isolated Test: Spark** | "Test Spark Traversal" button & HUD overlay | Navigates to "Trips" and "Earnings", extracts trips, returns JSON to modal. |
| **Isolated Test: OnePay** | "Test OnePay Traversal" button & auto-scroll | Clicks "Checking" -> "Show all", scrolls down until `startDate`, returns deposit count. |
| **Isolated Test: Photos** | "Test Photos Traversal" button | Clicks "Screenshots", parses offer cards, flags non-accepted offers. |
| **Combined Audit** | "Run Full Reconciliation Audit" master action | Runs 3 scrapers, matches trip IDs, reports tip-baiting customers with screenshot proofs. |
