package io.github.tonygnk.cataloglens.links

import com.intellij.openapi.paths.WebReference
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import io.github.tonygnk.cataloglens.psi.CatalogCoordinateExtractor
import io.github.tonygnk.cataloglens.psi.VersionCatalogDetector
import io.github.tonygnk.cataloglens.settings.CatalogLensGlobalSettings
import org.toml.lang.psi.TomlLiteral

class LibrariesReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(TomlLiteral::class.java),
            LibrariesWebReferenceProvider(),
        )
    }
}

private class LibrariesWebReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        val literal = element as? TomlLiteral ?: return PsiReference.EMPTY_ARRAY
        if (!VersionCatalogDetector.isVersionCatalog(literal.containingFile)) {
            return PsiReference.EMPTY_ARRAY
        }
        val coord = CatalogCoordinateExtractor.coordinateForLinkLiteral(literal)
            ?: return PsiReference.EMPTY_ARRAY
        val style = CatalogLensGlobalSettings.getInstance().state.artifactUrlStyle
        val url = ArtifactUrlBuilder.url(coord, style)
        val range = ElementManipulators.getValueTextRange(literal)
        return arrayOf(WebReference(literal, range, url))
    }

    override fun acceptsTarget(target: PsiElement): Boolean = false
}
