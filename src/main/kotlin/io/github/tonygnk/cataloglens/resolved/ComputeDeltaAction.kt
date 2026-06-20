package io.github.tonygnk.cataloglens.resolved

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiManager
import io.github.tonygnk.cataloglens.psi.VersionCatalogDetector
import org.toml.lang.psi.TomlFile

/**
 * Toolbar / popup action shown next to Sort. Visible only when a baseline exists and the catalog is
 * stale (a tracked build file changed since capture). The click syncs first only when a sync is
 * pending, computes the delta, and auto-promotes the fresh graph to the new baseline (no separate
 * "Set as baseline" step).
 */
class ComputeDeltaAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.PSI_FILE) as? TomlFile
        val vFile = file?.virtualFile
        if (project == null || vFile == null || !VersionCatalogDetector.isVersionCatalog(file)) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val service = project.service<ResolvedBaselineService>()
        // Show only once synced: a delta is meaningful against the on-disk graph, and the change vs the
        // reference (isStale) is real only after the edit has been synced.
        e.presentation.isEnabledAndVisible =
            service.hasBaselineFor(vFile) && service.isStale(vFile) && service.isSyncFinished()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? TomlFile ?: return
        val vFile = psiFile.virtualFile ?: return
        GradleSyncGate.ensureSynced(project, basePath, force = false) {
            val toml = PsiManager.getInstance(project).findFile(vFile) as? TomlFile ?: return@ensureSynced
            val text = FileDocumentManager.getInstance().getDocument(vFile)?.text ?: toml.text
            val declared = CatalogEntrySupport.declaredVersions(toml)
            ResolvedToolWindowFactory.activateDelta(project, vFile, text, declared, basePath)
        }
    }
}
