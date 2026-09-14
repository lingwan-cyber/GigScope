package com.gigscope.auditor.domain.model

enum class ActionType {
    CLICK,
    SCROLL_CONTAINER,
    WAIT
}

data class RecordedStep(
    val stepIndex: Int,
    val actionType: ActionType,
    val targetText: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val isScrollable: Boolean = false
)

data class AppPhaseRecipe(
    val phaseName: String,
    val description: String = "",
    val steps: List<RecordedStep> = emptyList()
)

data class AppRecipe(
    val packageName: String,
    val appName: String,
    val phases: Map<String, AppPhaseRecipe> = emptyMap()
)

data class RecordingSession(
    val targetPackage: String,
    val appName: String,
    val totalPhases: List<String>,
    var currentPhaseIndex: Int = 0,
    val recordedPhases: MutableMap<String, MutableList<RecordedStep>> = mutableMapOf()
) {
    val currentPhaseName: String
        get() = totalPhases.getOrElse(currentPhaseIndex) { "completed" }

    val isFinished: Boolean
        get() = currentPhaseIndex >= totalPhases.size

    fun addStepToCurrentPhase(step: RecordedStep) {
        val list = recordedPhases.getOrPut(currentPhaseName) { mutableListOf() }
        list.add(step)
    }

    fun currentPhaseStepCount(): Int {
        return recordedPhases[currentPhaseName]?.size ?: 0
    }

    fun toAppRecipe(): AppRecipe {
        val phaseMap = recordedPhases.mapValues { (phase, steps) ->
            AppPhaseRecipe(
                phaseName = phase,
                description = "Demonstrated $phase flow",
                steps = steps.toList()
            )
        }
        return AppRecipe(
            packageName = targetPackage,
            appName = appName,
            phases = phaseMap
        )
    }
}
