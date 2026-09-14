package com.gigscope.auditor.data.local

import com.gigscope.auditor.domain.model.ActionType
import com.gigscope.auditor.domain.model.AppPhaseRecipe
import com.gigscope.auditor.domain.model.AppRecipe
import com.gigscope.auditor.domain.model.RecordedStep
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class GigScopeConfig(
    val startDate: LocalDate = LocalDate.now().minusDays(14),
    val endDate: LocalDate = LocalDate.now(),
    val sparkApkPath: String = "",
    val onePayApkPath: String = "",
    val photosApkPath: String = "",
    val recipes: Map<String, AppRecipe> = emptyMap()
) {
    fun toJson(indent: Int = 2): String {
        val json = JSONObject()
        json.put("format", "GigScopeConfig")
        json.put("version", 2)
        json.put(
            "exportTime",
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", java.util.Locale.US).format(java.util.Date())
        )
        json.put("startDate", startDate.toString())
        json.put("endDate", endDate.toString())
        json.put("sparkApkPath", sparkApkPath)
        json.put("onePayApkPath", onePayApkPath)
        json.put("photosApkPath", photosApkPath)

        val recipesObj = JSONObject()
        for ((pkg, recipe) in recipes) {
            val appObj = JSONObject()
            appObj.put("packageName", recipe.packageName)
            appObj.put("appName", recipe.appName)

            val phasesObj = JSONObject()
            for ((phaseName, phase) in recipe.phases) {
                val phaseObj = JSONObject()
                phaseObj.put("phaseName", phase.phaseName)
                phaseObj.put("description", phase.description)

                val stepsArray = JSONArray()
                for (step in phase.steps) {
                    val stepObj = JSONObject()
                    stepObj.put("stepIndex", step.stepIndex)
                    stepObj.put("actionType", step.actionType.name)
                    if (step.targetText != null) stepObj.put("targetText", step.targetText)
                    if (step.contentDescription != null) stepObj.put("contentDescription", step.contentDescription)
                    if (step.viewId != null) stepObj.put("viewId", step.viewId)
                    if (step.className != null) stepObj.put("className", step.className)
                    stepObj.put("isScrollable", step.isScrollable)
                    stepsArray.put(stepObj)
                }
                phaseObj.put("steps", stepsArray)
                phasesObj.put(phaseName, phaseObj)
            }
            appObj.put("phases", phasesObj)
            recipesObj.put(pkg, appObj)
        }
        json.put("recipes", recipesObj)

        return json.toString(indent)
    }

    companion object {
        fun fromJson(jsonText: String): GigScopeConfig {
            val json = JSONObject(jsonText)
            val sDate = try {
                LocalDate.parse(json.optString("startDate"))
            } catch (e: Exception) {
                LocalDate.now().minusDays(14)
            }
            val eDate = try {
                LocalDate.parse(json.optString("endDate"))
            } catch (e: Exception) {
                LocalDate.now()
            }
            val sApk = json.optString("sparkApkPath", "")
            val oApk = json.optString("onePayApkPath", "")
            val pApk = json.optString("photosApkPath", "")

            val recipesMap = mutableMapOf<String, AppRecipe>()
            val recipesObj = json.optJSONObject("recipes")
            if (recipesObj != null) {
                val keys = recipesObj.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    val appObj = recipesObj.optJSONObject(pkg) ?: continue
                    val appName = appObj.optString("appName", pkg)

                    val phasesMap = mutableMapOf<String, AppPhaseRecipe>()
                    val phasesObj = appObj.optJSONObject("phases")
                    if (phasesObj != null) {
                        val phaseKeys = phasesObj.keys()
                        while (phaseKeys.hasNext()) {
                            val phaseKey = phaseKeys.next()
                            val phaseObj = phasesObj.optJSONObject(phaseKey) ?: continue
                            val phaseName = phaseObj.optString("phaseName", phaseKey)
                            val desc = phaseObj.optString("description", "")

                            val stepsList = mutableListOf<RecordedStep>()
                            val stepsArray = phaseObj.optJSONArray("steps")
                            if (stepsArray != null) {
                                for (i in 0 until stepsArray.length()) {
                                    val stepObj = stepsArray.optJSONObject(i) ?: continue
                                    val actName = stepObj.optString("actionType", ActionType.CLICK.name)
                                    val actType = try { ActionType.valueOf(actName) } catch (e: Exception) { ActionType.CLICK }
                                    stepsList.add(
                                        RecordedStep(
                                            stepIndex = stepObj.optInt("stepIndex", i),
                                            actionType = actType,
                                            targetText = if (stepObj.has("targetText")) stepObj.getString("targetText") else null,
                                            contentDescription = if (stepObj.has("contentDescription")) stepObj.getString("contentDescription") else null,
                                            viewId = if (stepObj.has("viewId")) stepObj.getString("viewId") else null,
                                            className = if (stepObj.has("className")) stepObj.getString("className") else null,
                                            isScrollable = stepObj.optBoolean("isScrollable", false)
                                        )
                                    )
                                }
                            }
                            phasesMap[phaseKey] = AppPhaseRecipe(phaseName, desc, stepsList)
                        }
                    }
                    recipesMap[pkg] = AppRecipe(pkg, appName, phasesMap)
                }
            }

            return GigScopeConfig(
                startDate = sDate,
                endDate = eDate,
                sparkApkPath = sApk,
                onePayApkPath = oApk,
                photosApkPath = pApk,
                recipes = recipesMap
            )
        }
    }
}
