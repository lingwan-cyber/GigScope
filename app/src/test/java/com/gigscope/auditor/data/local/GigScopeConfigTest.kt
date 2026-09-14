package com.gigscope.auditor.data.local

import com.gigscope.auditor.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GigScopeConfigTest {

    @Test
    fun testSerializationAndDeserialization_preservesRecipes() {
        val sparkPhaseTrips = AppPhaseRecipe(
            phaseName = "trips",
            description = "Navigate to Trips tab",
            steps = listOf(
                RecordedStep(
                    stepIndex = 0,
                    actionType = ActionType.CLICK,
                    targetText = "Trips",
                    className = "android.widget.TextView"
                ),
                RecordedStep(
                    stepIndex = 1,
                    actionType = ActionType.SCROLL_CONTAINER,
                    className = "androidx.recyclerview.widget.RecyclerView",
                    isScrollable = true
                )
            )
        )

        val sparkPhaseEarnings = AppPhaseRecipe(
            phaseName = "earnings",
            description = "Navigate to Earnings tab",
            steps = listOf(
                RecordedStep(
                    stepIndex = 0,
                    actionType = ActionType.CLICK,
                    targetText = "Earnings",
                    className = "android.widget.TextView"
                )
            )
        )

        val sparkRecipe = AppRecipe(
            packageName = "com.walmart.sparkdriver",
            appName = "Spark Driver",
            phases = mapOf(
                "trips" to sparkPhaseTrips,
                "earnings" to sparkPhaseEarnings
            )
        )

        val originalConfig = GigScopeConfig(
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 13),
            sparkApkPath = "Spark Driver (com.walmart.sparkdriver)",
            onePayApkPath = "OnePay (com.onefinance.one)",
            photosApkPath = "",
            recipes = mapOf("com.walmart.sparkdriver" to sparkRecipe)
        )

        val json = originalConfig.toJson()
        assertTrue(json.contains("\"format\": \"GigScopeConfig\""))
        assertTrue(json.contains("\"com.walmart.sparkdriver\""))
        assertTrue(json.contains("\"trips\""))
        assertTrue(json.contains("\"earnings\""))

        val deserialized = GigScopeConfig.fromJson(json)

        assertEquals(originalConfig.startDate, deserialized.startDate)
        assertEquals(originalConfig.endDate, deserialized.endDate)
        assertEquals(originalConfig.sparkApkPath, deserialized.sparkApkPath)
        assertEquals(originalConfig.onePayApkPath, deserialized.onePayApkPath)

        assertTrue(deserialized.recipes.containsKey("com.walmart.sparkdriver"))
        val loadedSpark = deserialized.recipes["com.walmart.sparkdriver"]!!
        assertEquals("Spark Driver", loadedSpark.appName)
        assertEquals(2, loadedSpark.phases.size)

        val loadedTrips = loadedSpark.phases["trips"]!!
        assertEquals(2, loadedTrips.steps.size)
        assertEquals("Trips", loadedTrips.steps[0].targetText)
        assertEquals(ActionType.CLICK, loadedTrips.steps[0].actionType)
        assertEquals(ActionType.SCROLL_CONTAINER, loadedTrips.steps[1].actionType)
        assertTrue(loadedTrips.steps[1].isScrollable)

        val loadedEarnings = loadedSpark.phases["earnings"]!!
        assertEquals(1, loadedEarnings.steps.size)
        assertEquals("Earnings", loadedEarnings.steps[0].targetText)
    }

    @Test
    fun testRecordingSession_advancesPhasesAndConvertsToAppRecipe() {
        val session = RecordingSession(
            targetPackage = "com.walmart.sparkdriver",
            appName = "Spark Driver",
            totalPhases = listOf("trips", "earnings")
        )

        assertEquals("trips", session.currentPhaseName)
        session.addStepToCurrentPhase(
            RecordedStep(
                stepIndex = 0,
                actionType = ActionType.CLICK,
                targetText = "Trips"
            )
        )
        assertEquals(1, session.currentPhaseStepCount())

        // Transition to Phase 2: Earnings
        session.currentPhaseIndex++
        assertEquals("earnings", session.currentPhaseName)
        session.addStepToCurrentPhase(
            RecordedStep(
                stepIndex = 0,
                actionType = ActionType.CLICK,
                targetText = "Earnings"
            )
        )
        assertEquals(1, session.currentPhaseStepCount())

        val appRecipe = session.toAppRecipe()
        assertEquals("com.walmart.sparkdriver", appRecipe.packageName)
        assertEquals(2, appRecipe.phases.size)
        assertTrue(appRecipe.phases.containsKey("trips"))
        assertTrue(appRecipe.phases.containsKey("earnings"))
    }
}
