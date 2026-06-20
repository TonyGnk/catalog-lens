package io.github.tonygnk.cataloglens.resolved

import io.github.tonygnk.cataloglens.resolved.model.ResolvedComponent
import io.github.tonygnk.cataloglens.resolved.model.ResolvedGraphModel

/**
 * Pure A→B delta computation, extracted from [ResolvedDeltaService] so it is unit-testable without the
 * Gradle daemon or the IDE runtime. Operates only on a [Baseline] and a [ResolvedGraphModel] — no
 * platform dependencies. See [ResolvedDeltaService] for how the result is rendered and consumed.
 */
internal object DeltaComputer {

    fun compute(
        baseline: Baseline,
        model: ResolvedGraphModel,
        currentDeclared: Map<String, String>,
        scope: String,
    ): Delta {
        val freshByKey = model.components
            .mapNotNull { c ->
                val g = c.group ?: return@mapNotNull null
                val n = c.name ?: return@mapNotNull null
                // Require a version, mirroring the baseline's versionsOf. A versionless component is still
                // in the graph, so keeping it with a null version made rV null → spurious "REMOVED".
                c.version ?: return@mapNotNull null
                "$g:$n" to c
            }
            .toMap()
        val freshVersions = freshByKey.mapValues { it.value.version }

        val youChanged = mutableListOf<DeltaChange>()
        val overridden = mutableListOf<DeltaChange>()
        val ripple = mutableListOf<DeltaChange>()

        val allKeys = (baseline.versions.keys + freshVersions.keys).toSortedSet()
        allKeys.forEach { key ->
            val bV = baseline.versions[key]
            val rV = freshVersions[key]
            val declaredVersion = currentDeclared[key]
            val isDeclared = currentDeclared.containsKey(key) || baseline.declared.containsKey(key)
            val declaredChanged = declaredVersion != baseline.declared[key]
            if (bV == rV && !declaredChanged) return@forEach

            when {
                declaredChanged -> {
                    val declaredBaseline = baseline.declared[key]
                    val declaredNote = "declared ${declaredBaseline ?: "—"} → ${declaredVersion ?: "—"}"
                    // Only add the resolved line when it tells you something the declared line does not —
                    // i.e. the resolved versions diverge from the pin at either end (else it is redundant).
                    val resolvedDiffers = bV != declaredBaseline || rV != declaredVersion
                    val resolvedNote = if (bV != rV && resolvedDiffers) " · resolves ${bV ?: "—"} → ${rV ?: "—"}" else ""
                    youChanged += DeltaChange(key, bV, rV, declaredNote + resolvedNote, ChangeKind.DECLARED, emptyList())
                }
                // A declared library that dropped out of the resolved graph — you removed its usage
                // (e.g. an implementation(libs.x) line) even though the catalog still declares it.
                isDeclared && bV != null && rV == null -> {
                    youChanged += DeltaChange(key, bV, rV, "removed $bV — no longer resolved", ChangeKind.REMOVED, emptyList())
                }
                // A declared library that now resolves — you added its usage (catalog already declared it).
                isDeclared && bV == null && rV != null -> {
                    youChanged += DeltaChange(key, bV, rV, "added $rV — now resolved", ChangeKind.ADDED, requestedBy(freshByKey[key]))
                }
                // Catalog pins it and it is present in both, but the resolved version moved. When it no
                // longer matches the pin, a transitive overrode the catalog.
                currentDeclared.containsKey(key) -> {
                    val isOverride = rV != declaredVersion
                    val kind = if (isOverride) ChangeKind.OVERRIDDEN else classify(bV, rV)
                    val note = if (isOverride) {
                        "declared ${declaredVersion ?: "—"} · resolves ${bV ?: "—"} → ${rV ?: "—"} (overridden)"
                    } else {
                        "${noteFor(kind, bV, rV)} · now matches declared ${declaredVersion ?: "—"}"
                    }
                    overridden += DeltaChange(key, bV, rV, note, kind, requestedBy(freshByKey[key]))
                }
                else -> {
                    val kind = classify(bV, rV)
                    ripple += DeltaChange(key, bV, rV, noteFor(kind, bV, rV), kind, requestedBy(freshByKey[key]))
                }
            }
        }

        val rejected = model.rejected.map { DeltaNote(it.coordinate, it.reason) }
        val excluded = model.excluded.map { DeltaNote(it.excluded, "inferred — via ${it.origin}") }
        return Delta(scope, allKeys.size, youChanged, overridden, ripple, rejected, excluded)
    }

    private fun requestedBy(component: ResolvedComponent?): List<String> =
        component?.requestedBy.orEmpty().map { e ->
            buildString {
                append(e.fromDisplayName)
                e.requestedVersion?.let { append(" → ").append(it) }
                if (e.isConstraint) append(" (constraint)")
            }
        }

    private fun classify(b: String?, r: String?): ChangeKind = when {
        b == null -> ChangeKind.ADDED
        r == null -> ChangeKind.REMOVED
        compareVersions(b, r) > 0 -> ChangeKind.DOWNGRADED
        else -> ChangeKind.BUMPED
    }

    private fun noteFor(kind: ChangeKind, b: String?, r: String?): String = when (kind) {
        ChangeKind.ADDED -> "added (transitive) ${r ?: "—"}"
        ChangeKind.REMOVED -> "removed ${b ?: "—"}"
        ChangeKind.DOWNGRADED -> "downgraded $b → $r"
        ChangeKind.BUMPED -> "bumped $b → $r"
        ChangeKind.OVERRIDDEN -> "overridden $b → $r"
        ChangeKind.DECLARED -> "changed"
    }

    /**
     * Best-effort SemVer-style comparison; 0 when equal or uncomparable. Build metadata (after `+`) is
     * ignored. The release part is compared numerically segment-by-segment (a missing segment counts as
     * 0, so `1.0` == `1.0.0`); a pre-release (`-suffix`) ranks below the same release, and pre-release
     * identifiers compare numerically when both numeric, with numeric below alphanumeric.
     */
    internal fun compareVersions(a: String, b: String): Int {
        if (a == b) return 0
        val ca = a.substringBefore('+')
        val cb = b.substringBefore('+')
        compareReleaseParts(ca.substringBefore('-'), cb.substringBefore('-')).let { if (it != 0) return it }

        val aPre = ca.substringAfter('-', "")
        val bPre = cb.substringAfter('-', "")
        // Equal release parts: a release outranks a pre-release of the same version.
        if (aPre.isEmpty() && bPre.isEmpty()) return 0
        if (aPre.isEmpty()) return 1
        if (bPre.isEmpty()) return -1
        return comparePreReleaseParts(aPre, bPre)
    }

    private fun compareReleaseParts(a: String, b: String): Int {
        val pa = a.split('.', '_')
        val pb = b.split('.', '_')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val na = pa.getOrNull(i)?.toIntOrNull() ?: 0
            val nb = pb.getOrNull(i)?.toIntOrNull() ?: 0
            if (na != nb) return na.compareTo(nb)
        }
        return 0
    }

    private fun comparePreReleaseParts(a: String, b: String): Int {
        val pa = a.split('.', '-', '_')
        val pb = b.split('.', '-', '_')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            // A larger set of identifiers outranks a smaller one when all preceding are equal.
            val sa = pa.getOrNull(i) ?: return -1
            val sb = pb.getOrNull(i) ?: return 1
            val na = sa.toIntOrNull()
            val nb = sb.toIntOrNull()
            val cmp = when {
                na != null && nb != null -> na.compareTo(nb)
                na != null -> -1 // numeric identifiers have lower precedence than alphanumeric
                nb != null -> 1
                else -> sa.compareTo(sb)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }
}
