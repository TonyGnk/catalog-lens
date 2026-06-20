package io.github.tonygnk.cataloglens.resolved.model

import java.io.Serializable

/**
 * Wire model returned by the injected [ToolingModelBuilder] in the Gradle daemon and proxied back
 * to the IDE by the Tooling API. Pure JVM only — no IntelliJ imports — and structurally matched by
 * the Tooling API (getters), so the daemon-side object need not implement this interface. Selection
 * causes are carried as Strings (the [org.gradle.api.artifacts.result.ComponentSelectionCause] name)
 * to avoid enum classloading mismatch across the daemon and the IDE.
 */
interface DependencyInsightModel : Serializable {
    val requested: String?
    val configurationName: String?
    val projectPath: String?
    val resolved: ResolvedNode?
    val unresolved: List<UnresolvedEdge>
    val errorMessage: String?
    val availableConfigurations: List<String>
}

interface ResolvedNode : Serializable {
    val group: String?
    val name: String?
    val selectedVersion: String?
    val selectionReasons: List<SelectionReason>
    val requestedBy: List<RequestedByEdge>
}

interface SelectionReason : Serializable {
    val cause: String
    val description: String
}

interface RequestedByEdge : Serializable {
    val fromDisplayName: String
    val requestedVersion: String?
    val isConstraint: Boolean
}

interface UnresolvedEdge : Serializable {
    val requested: String
    val failureMessage: String?
}
