package io.github.tonygnk.cataloglens.markers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.ui.awt.RelativePoint
import io.github.tonygnk.cataloglens.map.VersionUrlResolver
import io.github.tonygnk.cataloglens.psi.CatalogCoordinateExtractor
import io.github.tonygnk.cataloglens.psi.VersionCatalogDetector
import io.github.tonygnk.cataloglens.psi.headerName
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
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

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.General.Web,
            { "Open upstream release notes" },
            { event, _ ->
                if (urls.size == 1) {
                    BrowserUtil.browse(urls.single())
                } else {
                    JBPopupFactory.getInstance()
                        .createListPopup(UrlPopupStep(urls))
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

private class UrlPopupStep(urls: List<String>) :
    BaseListPopupStep<String>("Open Upstream Link", urls) {

    override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
        BrowserUtil.browse(selectedValue)
        return PopupStep.FINAL_CHOICE
    }
}
