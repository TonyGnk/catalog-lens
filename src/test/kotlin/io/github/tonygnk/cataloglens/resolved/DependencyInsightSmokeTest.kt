package io.github.tonygnk.cataloglens.resolved

import io.github.tonygnk.cataloglens.resolved.model.DependencyInsightModel
import io.github.tonygnk.cataloglens.resolved.model.ResolvedGraphModel
import org.gradle.tooling.GradleConnector
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Gate A: proves the injected init script registers a [ToolingModelBuilder] and a typed
 * [DependencyInsightModel] round-trips back over the Tooling API. Runs against this very project
 * (whose dependencies are already cached, so no network is needed) and resolves a known direct
 * dependency. Not a platform test — talks to a real Gradle daemon via raw GradleConnector.
 */
class DependencyInsightSmokeTest {

    @Test
    fun resolvesKnownDependencyAndReturnsTypedModel() {
        val projectDir = File(System.getProperty("user.dir"))
        val req = InsightRequest(
            gradleProjectPath = projectDir.absolutePath,
            targetProjectPath = "",
            configurationName = "runtimeClasspath",
            coordinate = "org.jsoup:jsoup",
            title = "smoke",
        )
        val initScript = InsightInitScript.build(req)

        val model = GradleConnector.newConnector()
            .forProjectDirectory(projectDir)
            .connect()
            .use { connection ->
                connection.model(DependencyInsightModel::class.java)
                    .withArguments("--init-script", initScript.toString(), "--no-configuration-cache")
                    .get()
            }

        assertNotNull("model should round-trip", model)
        assertTrue("available configurations should be enumerated", model.availableConfigurations.isNotEmpty())
        assertTrue("runtimeClasspath should resolve without error: ${model.errorMessage}", model.errorMessage == null)
        val node = model.resolved
        assertNotNull("jsoup should be present on runtimeClasspath", node)
        assertNotNull("jsoup should have a selected version", node!!.selectedVersion)
        assertTrue("selection reasons should be populated", node.selectionReasons.isNotEmpty())
    }

    /** Auto-pick branch: empty configuration name → the builder chooses a resolvable scope itself. */
    @Test
    fun autoPicksAResolvableScopeWhenNoneRequested() {
        val projectDir = File(System.getProperty("user.dir"))
        val req = InsightRequest(
            gradleProjectPath = projectDir.absolutePath,
            targetProjectPath = "",
            configurationName = "",
            coordinate = "org.jsoup:jsoup",
            title = "auto",
        )
        val initScript = InsightInitScript.build(req)

        val model = GradleConnector.newConnector()
            .forProjectDirectory(projectDir)
            .connect()
            .use { connection ->
                connection.model(DependencyInsightModel::class.java)
                    .withArguments("--init-script", initScript.toString(), "--no-configuration-cache")
                    .get()
            }

        assertTrue("auto-pick should resolve without error: ${model.errorMessage}", model.errorMessage == null)
        assertTrue("auto-pick should choose a configuration", !model.configurationName.isNullOrBlank())
        assertNotNull("auto-pick should choose a project", model.projectPath)
    }

    /** Graph-model branch (baseline source): the same builder returns the full artifact list. */
    @Test
    fun graphModelReturnsResolvedArtifacts() {
        val projectDir = File(System.getProperty("user.dir"))
        val req = InsightRequest(
            gradleProjectPath = projectDir.absolutePath,
            targetProjectPath = "",
            configurationName = "runtimeClasspath",
            coordinate = "",
            title = "graph",
        )
        val initScript = InsightInitScript.build(req)

        val model = GradleConnector.newConnector()
            .forProjectDirectory(projectDir)
            .connect()
            .use { connection ->
                connection.model(ResolvedGraphModel::class.java)
                    .withArguments("--init-script", initScript.toString(), "--no-configuration-cache")
                    .get()
            }

        assertTrue("graph should resolve without error: ${model.errorMessage}", model.errorMessage == null)
        assertTrue("graph should contain components", model.components.isNotEmpty())
        assertTrue(
            "jsoup should be among resolved components",
            model.components.any { it.group == "org.jsoup" && it.name == "jsoup" },
        )
        val jsoup = model.components.first { it.group == "org.jsoup" }
        assertTrue("components should carry selection reasons", jsoup.selectionReasons.isNotEmpty())
    }
}
