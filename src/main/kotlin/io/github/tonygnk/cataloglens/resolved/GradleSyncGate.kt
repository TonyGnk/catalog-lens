package io.github.tonygnk.cataloglens.resolved

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * Ensures the Gradle project model reflects the on-disk catalog before a resolution runs. The TAPI
 * resolution reads files from disk, so unsaved edits or a pending Gradle sync make a re-resolve diff
 * against stale inputs (the delta then "finds nothing"). [ensureSynced] saves all documents and, when
 * a sync is pending (or [force] is set), runs one and defers [onSynced] until it completes.
 */
object GradleSyncGate {

    fun ensureSynced(project: Project, externalProjectPath: String, force: Boolean, onSynced: () -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            FileDocumentManager.getInstance().saveAllDocuments()
            val pending = ExternalSystemProjectNotificationAware.getInstance(project).isNotificationVisible()
            if (!force && !pending) {
                onSynced()
                return@invokeLater
            }
            val spec = ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
                .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
                .callback(object : ExternalProjectRefreshCallback {
                    override fun onSuccess(externalProject: DataNode<ProjectData>?) {
                        ApplicationManager.getApplication().invokeLater { onSynced() }
                    }

                    override fun onFailure(errorMessage: String, errorDetails: String?) {
                        // Surface the failure: onSynced carries the caller's entire downstream work
                        // (capture / compute-delta), so silently dropping it leaves the action a no-op
                        // with no feedback. Notify and abort rather than resolve against a stale graph.
                        thisLogger().warn("Gradle sync failed before resolution: $errorMessage")
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("CatalogLens")
                            .createNotification(
                                "Gradle sync failed; cannot resolve dependencies: $errorMessage",
                                NotificationType.ERROR,
                            )
                            .notify(project)
                    }
                })
                .build()
            ExternalSystemUtil.refreshProject(externalProjectPath, spec)
        }
    }
}
