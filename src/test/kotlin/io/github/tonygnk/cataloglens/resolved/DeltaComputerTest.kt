package io.github.tonygnk.cataloglens.resolved

import io.github.tonygnk.cataloglens.resolved.model.ExcludedRule
import io.github.tonygnk.cataloglens.resolved.model.RejectedCandidate
import io.github.tonygnk.cataloglens.resolved.model.RequestedByEdge
import io.github.tonygnk.cataloglens.resolved.model.ResolvedComponent
import io.github.tonygnk.cataloglens.resolved.model.ResolvedGraphModel
import io.github.tonygnk.cataloglens.resolved.model.SelectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [DeltaComputer] — exercises the classification branches and version comparison
 * with fake [ResolvedGraphModel]s, no Gradle daemon or IDE fixture required.
 */
class DeltaComputerTest {

    private val scope = ":app/debugRuntimeClasspath"

    @Test
    fun unchangedRowsAreSkipped() {
        val delta = compute(
            baselineVersions = mapOf("a:a" to "1.0"),
            declaredBaseline = mapOf("a:a" to "1.0"),
            currentDeclared = mapOf("a:a" to "1.0"),
            components = listOf(comp("a:a", "1.0")),
        )
        assertTrue(delta.isEmpty)
        assertEquals(1, delta.comparedCount)
    }

    @Test
    fun declaredVersionChangeLandsInYouChanged() {
        val delta = compute(
            baselineVersions = mapOf("a:a" to "1.0"),
            declaredBaseline = mapOf("a:a" to "1.0"),
            currentDeclared = mapOf("a:a" to "2.0"),
            components = listOf(comp("a:a", "2.0")),
        )
        assertEquals(1, delta.youChanged.size)
        val change = delta.youChanged.single()
        assertEquals(ChangeKind.DECLARED, change.kind)
        assertEquals("declared 1.0 → 2.0", change.note)
        assertTrue(delta.overridden.isEmpty() && delta.ripple.isEmpty())
    }

    @Test
    fun declaredLibraryDroppedFromGraphIsRemoved() {
        val delta = compute(
            baselineVersions = mapOf("a:a" to "1.0"),
            declaredBaseline = mapOf("a:a" to "1.0"),
            currentDeclared = mapOf("a:a" to "1.0"),
            components = emptyList(),
        )
        val change = delta.youChanged.single()
        assertEquals(ChangeKind.REMOVED, change.kind)
        assertEquals("removed 1.0 — no longer resolved", change.note)
    }

    @Test
    fun declaredLibraryNowResolvesIsAddedWithRequestedBy() {
        val delta = compute(
            baselineVersions = emptyMap(),
            declaredBaseline = mapOf("a:a" to "1.0"),
            currentDeclared = mapOf("a:a" to "1.0"),
            components = listOf(comp("a:a", "1.0", edge("root project :app", "1.0", constraint = false))),
        )
        val change = delta.youChanged.single()
        assertEquals(ChangeKind.ADDED, change.kind)
        assertEquals("added 1.0 — now resolved", change.note)
        assertEquals(listOf("root project :app → 1.0"), change.requestedBy)
    }

    @Test
    fun transitiveOverridingDeclaredPinIsOverridden() {
        val delta = compute(
            baselineVersions = mapOf("a:a" to "1.0"),
            declaredBaseline = mapOf("a:a" to "1.0"),
            currentDeclared = mapOf("a:a" to "1.0"),
            components = listOf(comp("a:a", "2.0")),
        )
        val change = delta.overridden.single()
        assertEquals(ChangeKind.OVERRIDDEN, change.kind)
        assertTrue(change.note.contains("(overridden)"))
        assertTrue(delta.youChanged.isEmpty())
    }

    @Test
    fun resolvedMovedToMatchDeclaredIsClassifiedNotOverridden() {
        val delta = compute(
            baselineVersions = mapOf("a:a" to "1.0"),
            declaredBaseline = mapOf("a:a" to "2.0"),
            currentDeclared = mapOf("a:a" to "2.0"),
            components = listOf(comp("a:a", "2.0")),
        )
        val change = delta.overridden.single()
        assertEquals(ChangeKind.BUMPED, change.kind)
        assertTrue(change.note.contains("now matches declared 2.0"))
    }

    @Test
    fun pureTransitiveBumpIsRipple() {
        val delta = compute(
            baselineVersions = mapOf("x:y" to "1.0"),
            declaredBaseline = emptyMap(),
            currentDeclared = emptyMap(),
            components = listOf(comp("x:y", "2.0", edge("a:a:2.0", null, constraint = false))),
        )
        val change = delta.ripple.single()
        assertEquals(ChangeKind.BUMPED, change.kind)
        assertEquals("bumped 1.0 → 2.0", change.note)
        assertEquals(listOf("a:a:2.0"), change.requestedBy)
        assertTrue(delta.youChanged.isEmpty() && delta.overridden.isEmpty())
    }

    @Test
    fun transitiveDowngradeIsRipple() {
        val delta = compute(
            baselineVersions = mapOf("x:y" to "2.0"),
            declaredBaseline = emptyMap(),
            currentDeclared = emptyMap(),
            components = listOf(comp("x:y", "1.0")),
        )
        assertEquals(ChangeKind.DOWNGRADED, delta.ripple.single().kind)
    }

    @Test
    fun rejectedAndExcludedAreMapped() {
        val delta = compute(
            baselineVersions = emptyMap(),
            declaredBaseline = emptyMap(),
            currentDeclared = emptyMap(),
            components = emptyList(),
            rejected = listOf(FakeRejected("g:n:9.9", "rejected by rule")),
            excluded = listOf(FakeExcluded("g:bad", "g:n")),
        )
        assertEquals(DeltaNote("g:n:9.9", "rejected by rule"), delta.rejected.single())
        assertEquals(DeltaNote("g:bad", "inferred — via g:n"), delta.excluded.single())
    }

    @Test
    fun requestedByRendersConstraintSuffix() {
        val delta = compute(
            baselineVersions = emptyMap(),
            declaredBaseline = mapOf("a:a" to "1.0"),
            currentDeclared = mapOf("a:a" to "1.0"),
            components = listOf(comp("a:a", "1.0", edge("platform:bom", "1.0", constraint = true))),
        )
        assertEquals(listOf("platform:bom → 1.0 (constraint)"), delta.youChanged.single().requestedBy)
    }

    @Test
    fun compareVersionsUsesNumericNotLexicalOrdering() {
        assertTrue("2.0.0 < 10.0.0", DeltaComputer.compareVersions("2.0.0", "10.0.0") < 0)
        assertTrue("1.10 > 1.9", DeltaComputer.compareVersions("1.10", "1.9") > 0)
        assertEquals(0, DeltaComputer.compareVersions("1.2.3", "1.2.3"))
        assertEquals("1.0 == 1.0.0", 0, DeltaComputer.compareVersions("1.0", "1.0.0"))
    }

    @Test
    fun compareVersionsRanksPreReleaseBelowRelease() {
        assertTrue("pre-release < release", DeltaComputer.compareVersions("1.0.0-alpha", "1.0.0") < 0)
        assertTrue("release > pre-release", DeltaComputer.compareVersions("1.0.0", "1.0.0-rc1") > 0)
        assertTrue("alpha < beta", DeltaComputer.compareVersions("1.0.0-alpha", "1.0.0-beta") < 0)
        assertTrue("numeric id < alphanumeric id", DeltaComputer.compareVersions("1.0.0-1", "1.0.0-alpha") < 0)
        assertTrue("numeric pre-release ids compared numerically", DeltaComputer.compareVersions("1.0.0-2", "1.0.0-11") < 0)
        assertTrue("fewer pre-release ids < more", DeltaComputer.compareVersions("1.0.0-alpha", "1.0.0-alpha.1") < 0)
        assertEquals("build metadata ignored", 0, DeltaComputer.compareVersions("1.0.0+a", "1.0.0+b"))
    }

    @Test
    fun preReleaseToReleaseIsBumpNotDowngrade() {
        val delta = compute(
            baselineVersions = mapOf("x:y" to "1.0.0-beta"),
            declaredBaseline = emptyMap(),
            currentDeclared = emptyMap(),
            components = listOf(comp("x:y", "1.0.0")),
        )
        // Lexically "1.0.0-beta" > "1.0.0", which used to mislabel this as DOWNGRADED.
        assertEquals(ChangeKind.BUMPED, delta.ripple.single().kind)
    }

    @Test
    fun versionlessFreshComponentIsExcluded() {
        val delta = compute(
            baselineVersions = emptyMap(),
            declaredBaseline = emptyMap(),
            currentDeclared = emptyMap(),
            components = listOf(comp("a:a", "1.0"), comp("b:b", null)),
        )
        // The versionless component is dropped (mirrors the baseline), so only a:a is compared.
        assertEquals(1, delta.comparedCount)
        assertEquals("a:a", delta.ripple.single().coordinateKey)
    }

    // --- helpers -------------------------------------------------------------

    private fun compute(
        baselineVersions: Map<String, String>,
        declaredBaseline: Map<String, String>,
        currentDeclared: Map<String, String>,
        components: List<ResolvedComponent>,
        rejected: List<RejectedCandidate> = emptyList(),
        excluded: List<ExcludedRule> = emptyList(),
    ): Delta = DeltaComputer.compute(
        baseline = Baseline(
            catalogUrl = "file:///gradle/libs.versions.toml",
            projectPath = ":app",
            configurationName = "debugRuntimeClasspath",
            capturedAt = 0L,
            versions = baselineVersions,
            declared = declaredBaseline,
        ),
        model = FakeGraph(components, rejected, excluded),
        currentDeclared = currentDeclared,
        scope = scope,
    )

    private fun comp(key: String, version: String?, vararg edges: RequestedByEdge): ResolvedComponent {
        val parts = key.split(":", limit = 2)
        return FakeComponent(parts[0], parts.getOrNull(1), version, edges.toList())
    }

    private fun edge(from: String, requestedVersion: String?, constraint: Boolean): RequestedByEdge =
        FakeEdge(from, requestedVersion, constraint)
}

private data class FakeEdge(
    override val fromDisplayName: String,
    override val requestedVersion: String?,
    override val isConstraint: Boolean,
) : RequestedByEdge

private data class FakeComponent(
    override val group: String?,
    override val name: String?,
    override val version: String?,
    override val requestedBy: List<RequestedByEdge> = emptyList(),
    override val selectionReasons: List<SelectionReason> = emptyList(),
) : ResolvedComponent

private data class FakeRejected(
    override val coordinate: String,
    override val reason: String?,
) : RejectedCandidate

private data class FakeExcluded(
    override val excluded: String,
    override val origin: String,
) : ExcludedRule

private data class FakeGraph(
    override val components: List<ResolvedComponent>,
    override val rejected: List<RejectedCandidate> = emptyList(),
    override val excluded: List<ExcludedRule> = emptyList(),
    override val configurationName: String? = ":app/debugRuntimeClasspath",
    override val projectPath: String? = ":app",
    override val errorMessage: String? = null,
    override val availableConfigurations: List<String> = emptyList(),
) : ResolvedGraphModel
