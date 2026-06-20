package io.github.tonygnk.cataloglens.resolved

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.tonygnk.cataloglens.psi.CatalogCoordinateExtractor
import io.github.tonygnk.cataloglens.psi.Coord
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

/** Maps a catalog `[libraries]` entry (or a caret inside one) to its group:name coordinate. */
internal object CatalogEntrySupport {

    /** The coordinate of [entry] if it is a `[libraries]` entry, resolving any version.ref. */
    fun coordForEntry(entry: TomlKeyValue): Coord? =
        PsiTreeUtil.findChildrenOfType(entry, TomlLiteral::class.java)
            .firstNotNullOfOrNull { CatalogCoordinateExtractor.coordinateForLinkLiteral(it) }

    /** The outermost key-value (the alias-level entry) containing [offset]. */
    fun entryAt(file: PsiFile, offset: Int): TomlKeyValue? {
        val element = file.findElementAt(offset) ?: return null
        return generateSequence(PsiTreeUtil.getParentOfType(element, TomlKeyValue::class.java)) {
            PsiTreeUtil.getParentOfType(it, TomlKeyValue::class.java)
        }.lastOrNull()
    }

    /** Declared `group:name` → version for every `[libraries]` entry that has a (resolvable) version. */
    fun declaredVersions(file: TomlFile): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        PsiTreeUtil.findChildrenOfType(file, TomlKeyValue::class.java)
            .filter { it.parent is TomlTable }
            .forEach { entry ->
                val coord = coordForEntry(entry) ?: return@forEach
                coord.version?.let { out[coord.key] = it }
            }
        return out
    }

    /** The PSI entry whose coordinate matches [coordinateKey] (group:name), for navigation. */
    fun entryForCoordinate(file: TomlFile, coordinateKey: String): TomlKeyValue? =
        PsiTreeUtil.findChildrenOfType(file, TomlKeyValue::class.java)
            .filter { it.parent is TomlTable }
            .firstOrNull { coordForEntry(it)?.key == coordinateKey }
}
