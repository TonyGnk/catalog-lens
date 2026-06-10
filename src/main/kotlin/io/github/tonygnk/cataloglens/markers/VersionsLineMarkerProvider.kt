package io.github.tonygnk.cataloglens.markers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.ui.awt.RelativePoint
import io.github.tonygnk.cataloglens.map.VersionUrlResolver
import io.github.tonygnk.cataloglens.releases.UrlActionDispatcher
import io.github.tonygnk.cataloglens.releases.VersionEditContext
import io.github.tonygnk.cataloglens.psi.CatalogCoordinateExtractor
import io.github.tonygnk.cataloglens.psi.VersionCatalogDetector
import io.github.tonygnk.cataloglens.psi.headerName
import io.github.tonygnk.cataloglens.psi.stringValue
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import javax.swing.Icon

class VersionsLineMarkerProvider : LineMarkerProviderDescriptor() {

    override fun getName(): String = "Version catalog upstream links"

    override fun getIcon(): Icon = AllIcons.General.Web

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is LeafPsiElement) return null
        val segment = element.parent as? TomlKeySegment ?: return null
        val key = segment.parent as? TomlKey ?: return null
        val keyValue = key.parent as? TomlKeyValue ?: return null
        val table = keyValue.parent as? TomlTable ?: return null
        if (headerName(table) != "versions") return null

        val file = element.containingFile as? TomlFile ?: return null
        if (!VersionCatalogDetector.isVersionCatalog(file)) return null

        val refName = segment.name ?: return null
        val urls = resolveUrls(file, refName)
        if (urls.isEmpty()) return null

        val currentVersion = (keyValue.value as? TomlLiteral)?.stringValue()
        val edit = file.virtualFile?.let { VersionEditContext(it, refName, currentVersion) }

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.General.Web,
            { "Open upstream release notes" },
            { event, elt ->
                val project = elt.project
                if (urls.size == 1) {
                    UrlActionDispatcher.open(project, urls.single(), edit)
                } else {
                    JBPopupFactory.getInstance()
                        .createListPopup(UrlPopupStep(project, urls, edit))
                        .show(RelativePoint(event))
                }
            },
            GutterIconRenderer.Alignment.RIGHT,
            { "Upstream release notes" },
        )
    }

    private fun resolveUrls(file: TomlFile, refName: String): List<String> {
        val project = file.project
        val fromLibraries = CatalogCoordinateExtractor.coordinatesForVersionRef(file, refName)
            .flatMap { VersionUrlResolver.resolveLibrary(project, it) }
        val fromPlugins = CatalogCoordinateExtractor.pluginIdsForVersionRef(file, refName)
            .flatMap { VersionUrlResolver.resolvePluginId(project, it) }
        return (fromLibraries + fromPlugins).distinct()
    }
}

private class UrlPopupStep(
    private val project: Project,
    urls: List<String>,
    private val edit: VersionEditContext?,
) : BaseListPopupStep<String>("Open Upstream Link", urls) {

    override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
        UrlActionDispatcher.open(project, selectedValue, edit)
        return PopupStep.FINAL_CHOICE
    }
}
