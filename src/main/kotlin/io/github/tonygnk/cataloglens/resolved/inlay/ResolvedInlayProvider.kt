package io.github.tonygnk.cataloglens.resolved.inlay

import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.tonygnk.cataloglens.psi.VersionCatalogDetector
import io.github.tonygnk.cataloglens.resolved.CatalogEntrySupport
import io.github.tonygnk.cataloglens.resolved.ResolvedBaselineService
import io.github.tonygnk.cataloglens.settings.CatalogLensGlobalSettings
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

/**
 * Surface 3: end-of-line hint on a `[libraries]` entry showing the resolved version when it differs
 * from the declared one. Reads the live snapshot (auto-refreshed on every Gradle sync) with a fallback
 * to the persisted baseline, so the hints stay current and do not vanish on a build-file edit — between
 * an edit and the next sync they show the last synced values, which the IDE's "needs sync" ribbon flags.
 */
class ResolvedInlayProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        if (file !is TomlFile || !VersionCatalogDetector.isVersionCatalog(file)) return null
        if (!CatalogLensGlobalSettings.getInstance().state.resolvedInlaysEnabled) return null
        val vFile = file.virtualFile ?: return null
        val service = file.project.service<ResolvedBaselineService>()
        // Render only on the catalog the baseline was captured from. resolvedVersion is project-scoped
        // (keyed by group:name, file-agnostic), so without this gate a second/unrelated catalog would get
        // the baseline catalog's resolved versions stamped onto its matching entries.
        if (!service.hasBaselineFor(vFile)) return null
        return Collector(editor.document, vFile, service)
    }

    private class Collector(
        private val document: Document,
        private val file: VirtualFile,
        private val service: ResolvedBaselineService,
    ) : SharedBypassCollector {

        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            if (element !is TomlKeyValue || element.parent !is TomlTable) return
            val coord = CatalogEntrySupport.coordForEntry(element) ?: return
            val resolved = service.resolvedVersion(file, coord.key) ?: return
            if (resolved == coord.version) return
            val line = document.getLineNumber(element.textRange.startOffset)
            val declaredNote = coord.version?.let { " (declared $it)" } ?: ""
            sink.addPresentation(
                EndOfLinePosition(line),
                tooltip = "Resolves to $resolved$declaredNote",
                hasBackground = true,
            ) {
                text(
                    "→ resolves $resolved",
                    InlayActionData(StringInlayActionPayload(coord.key), ResolvedInlayActionHandler.HANDLER_ID),
                )
            }
        }
    }
}
