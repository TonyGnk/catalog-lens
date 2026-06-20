package io.github.tonygnk.cataloglens.resolved

import io.github.tonygnk.cataloglens.resolved.model.DependencyInsightModel
import io.github.tonygnk.cataloglens.resolved.model.ResolvedGraphModel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/** Builds the injected init script that registers the shared CatalogLens tooling-model builder. */
internal object InsightInitScript {

    private const val RESOURCE = "/io/github/tonygnk/cataloglens/resolved/insight-init.gradle"

    /** Model FQNs the daemon-side builder matches in [canBuild]; kept in sync with the interfaces. */
    val insightModelName: String = DependencyInsightModel::class.java.name
    val graphModelName: String = ResolvedGraphModel::class.java.name

    fun build(req: InsightRequest): Path {
        val template = javaClass.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { it.readText() }
            ?: error("Missing init-script template at $RESOURCE")
        val content = template
            .replace("%INSIGHT_FQN%", insightModelName)
            .replace("%GRAPH_FQN%", graphModelName)
            .replace("%CONFIG_NAME%", req.configurationName)
            .replace("%TARGET_GA%", req.coordinate)
            .replace("%TARGET_PROJECT_PATH%", req.targetProjectPath)
        val path = Files.createTempFile("cataloglens-resolved-", ".gradle")
        path.writeText(content)
        // Cleanup is the caller's (GradleModelResolver deletes it after the resolve completes); no
        // deleteOnExit, which would leak a static String entry per resolve for the JVM's lifetime.
        return path
    }
}
