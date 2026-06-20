package io.github.tonygnk.cataloglens.resolved

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import io.github.tonygnk.cataloglens.psi.Coord
import io.github.tonygnk.cataloglens.psi.VersionCatalogDetector
import org.toml.lang.psi.TomlFile

class WhyThisVersionAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = coordAt(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val coord = coordAt(e) ?: return
        ResolvedToolWindowFactory.activateInsight(
            project,
            InsightRequest(
                gradleProjectPath = basePath,
                targetProjectPath = "",
                configurationName = "", // empty = let the model builder auto-pick the best scope
                coordinate = coord.key,
                title = coord.key,
            ),
        )
    }

    private fun coordAt(e: AnActionEvent): Coord? {
        val file = e.getData(CommonDataKeys.PSI_FILE) as? TomlFile ?: return null
        if (!VersionCatalogDetector.isVersionCatalog(file)) return null
        val offset = e.getData(CommonDataKeys.CARET)?.offset ?: return null
        // Caret may sit anywhere on the entry line — alias key, an inline-table value, or a
        // string-notation value — and still resolve, by climbing to the outermost entry.
        val entry = CatalogEntrySupport.entryAt(file, offset) ?: return null
        return CatalogEntrySupport.coordForEntry(entry)
    }
}
