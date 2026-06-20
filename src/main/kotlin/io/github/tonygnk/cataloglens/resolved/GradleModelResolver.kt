package io.github.tonygnk.cataloglens.resolved

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.nio.file.Files

/**
 * Runs one on-demand Tooling API resolution for an arbitrary custom model, reusing IntelliJ's
 * Gradle execution settings (project distribution + JVM). Shared by the insight and baseline
 * services so both go through the same init-script injection and argument set.
 */
internal object GradleModelResolver {

    fun <T> resolve(
        project: Project,
        req: InsightRequest,
        modelClass: Class<T>,
        token: CancellationToken,
    ): T {
        val settings = ExternalSystemApiUtil.getExecutionSettings<GradleExecutionSettings>(
            project, req.gradleProjectPath, GradleConstants.SYSTEM_ID,
        )
        val scriptPath = InsightInitScript.build(req)
        val args = listOf(
            GradleConstants.INIT_SCRIPT_CMD_OPTION, scriptPath.toString(),
            "--no-configuration-cache",
        )
        val connector = GradleConnector.newConnector()
            .forProjectDirectory(File(req.gradleProjectPath))
        // A local install must be pointed at explicitly; wrapper/default are auto-detected from the project dir.
        if (settings.distributionType == DistributionType.LOCAL) {
            settings.gradleHome?.let { connector.useInstallation(File(it)) }
        }
        try {
            return connector.connect().use { connection: ProjectConnection ->
                val builder = connection.model(modelClass)
                builder.withArguments(args)
                builder.withCancellationToken(token)
                settings.javaHome?.let { builder.setJavaHome(File(it)) }
                builder.get()
            }
        } finally {
            // The daemon has read the init script by the time the model resolves; drop the temp file
            // eagerly instead of letting it accumulate until JVM exit (one per resolve, incl. every sync).
            runCatching { Files.deleteIfExists(scriptPath) }
        }
    }
}
