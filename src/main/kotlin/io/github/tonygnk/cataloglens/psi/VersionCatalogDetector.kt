package io.github.tonygnk.cataloglens.psi

import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlTable

object VersionCatalogDetector {

    private val CATALOG_TABLES = setOf("libraries", "versions", "plugins", "bundles")

    fun isVersionCatalog(file: PsiFile?): Boolean {
        if (file !is TomlFile) return false
        if (file.name.endsWith(".versions.toml")) return true
        val vf = file.virtualFile ?: return false
        if (vf.parent?.name != "gradle") return false
        return hasCatalogTables(file)
    }

    private fun hasCatalogTables(file: TomlFile): Boolean =
        CachedValuesManager.getCachedValue(file) {
            val has = file.children
                .filterIsInstance<TomlTable>()
                .any { it.header.key?.segments?.singleOrNull()?.name in CATALOG_TABLES }
            CachedValueProvider.Result.create(has, file)
        }
}
