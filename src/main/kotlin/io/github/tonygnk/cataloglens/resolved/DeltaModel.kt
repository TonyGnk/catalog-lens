package io.github.tonygnk.cataloglens.resolved

/** Classification of a single artifact's change between baseline and the re-resolved graph. */
enum class ChangeKind { DECLARED, OVERRIDDEN, BUMPED, DOWNGRADED, ADDED, REMOVED }

/** One changed artifact row. [requestedBy] populates the expandable subtree for ripple rows. */
data class DeltaChange(
    val coordinateKey: String,
    val baseline: String?,
    val resolved: String?,
    val note: String,
    val kind: ChangeKind,
    val requestedBy: List<String>,
)

data class DeltaNote(val label: String, val detail: String?)

/** The full A→B diff for one scope: the sections the tool window renders. */
data class Delta(
    val scope: String,
    val comparedCount: Int,
    val youChanged: List<DeltaChange>,
    val overridden: List<DeltaChange>,
    val ripple: List<DeltaChange>,
    val rejected: List<DeltaNote>,
    val excluded: List<DeltaNote>,
) {
    val isEmpty: Boolean
        get() = youChanged.isEmpty() && overridden.isEmpty() && ripple.isEmpty() &&
            rejected.isEmpty() && excluded.isEmpty()
}

sealed interface DeltaState {
    data object Empty : DeltaState
    data class Computing(val scope: String) : DeltaState
    data class Ready(val delta: Delta) : DeltaState
    data class Failed(val message: String) : DeltaState
}

interface DeltaView {
    fun render(state: DeltaState)
}
