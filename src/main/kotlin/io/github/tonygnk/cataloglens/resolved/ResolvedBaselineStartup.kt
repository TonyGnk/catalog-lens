package io.github.tonygnk.cataloglens.resolved

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Forces [ResolvedBaselineService] to be created on project open so it subscribes to the IDE's
 * sync-notification topic immediately — otherwise the service is lazy and would miss build-file
 * changes until the user happens to open the catalog.
 */
class ResolvedBaselineStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<ResolvedBaselineService>()
    }
}
