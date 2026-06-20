package io.github.tonygnk.cataloglens.resolved.model

import java.io.Serializable

/**
 * Full resolved graph for one (module, configuration) — the baseline data source and the input to
 * the A→B delta. Carries per-component selection reasons + requested-by edges so the delta's
 * "transitive ripple" rows can expand into a requested-by subtree, plus best-effort rejected and
 * (inferred) excluded data for the third delta section. Produced by the same init-script builder
 * as [DependencyInsightModel]. Pure JVM only.
 */
interface ResolvedGraphModel : Serializable {
    val configurationName: String?
    val projectPath: String?
    val errorMessage: String?
    val components: List<ResolvedComponent>
    val rejected: List<RejectedCandidate>
    val excluded: List<ExcludedRule>
    val availableConfigurations: List<String>
}

interface ResolvedComponent : Serializable {
    val group: String?
    val name: String?
    val version: String?
    val selectionReasons: List<SelectionReason>
    val requestedBy: List<RequestedByEdge>
}

interface RejectedCandidate : Serializable {
    val coordinate: String
    val reason: String?
}

/** Inferred from declaration-side exclude rules (an excluded transitive is invisible in the graph). */
interface ExcludedRule : Serializable {
    val excluded: String
    val origin: String
}
