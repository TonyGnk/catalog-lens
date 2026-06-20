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

class CaptureBaselineAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible =
            VersionCatalogDetector.isVersionCatalog(e.getData(CommonDataKeys.PSI_FILE)) &&
                project != null &&
                project.service<ResolvedBaselineService>().isSyncFinished()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? TomlFile ?: return
        val vFile = psiFile.virtualFile ?: return
        // Sync only when the IDE reports pending changes ("Gradle files have changed since last sync");
        // otherwise the project is already in sync and re-capturing must not trigger a needless sync.
        GradleSyncGate.ensureSynced(project, basePath, force = false) {
            val toml = PsiManager.getInstance(project).findFile(vFile) as? TomlFile ?: return@ensureSynced
            val text = FileDocumentManager.getInstance().getDocument(vFile)?.text ?: toml.text
            val declared = CatalogEntrySupport.declaredVersions(toml)
            project.service<ResolvedBaselineService>().capture(vFile, text, declared, basePath)
        }
    }
}
