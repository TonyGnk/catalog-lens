package io.github.tonygnk.cataloglens.resolved

import io.github.tonygnk.cataloglens.resolved.model.DependencyInsightModel

/**
 * One "Why this version?" request, fully self-describing so the panel can render its scope header
 * (Design principle 2 — scope always explicit) without reaching back into PSI.
 */
data class InsightRequest(
    val gradleProjectPath: String,
    val targetProjectPath: String,
    val configurationName: String,
    val coordinate: String,
    val title: String,
)

sealed interface InsightState {
    val request: InsightRequest?

    data object NoProject : InsightState {
        override val request: InsightRequest? = null
    }

    data class Resolving(override val request: InsightRequest) : InsightState
    data class Ready(override val request: InsightRequest, val model: DependencyInsightModel) : InsightState
    data class Failed(override val request: InsightRequest, val message: String) : InsightState
}

interface InsightView {
    fun render(state: InsightState)
}
