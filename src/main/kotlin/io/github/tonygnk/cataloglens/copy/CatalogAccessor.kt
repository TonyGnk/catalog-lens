package io.github.tonygnk.cataloglens.copy

import com.intellij.psi.PsiFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

/** Builds Gradle Kotlin-DSL dependency declarations from catalog entries. */
internal object CatalogAccessor {

    /** Catalog name derived from the file name: `libs.versions.toml` -> `libs`. */
    fun catalogName(file: PsiFile): String {
        val name = file.name
        if (!name.endsWith(".versions.toml")) return "libs"
        return name.removeSuffix(".versions.toml").ifBlank { "libs" }
    }

    /** Gradle alias normalization: `-` and `_` separators become `.`. */
    fun accessorSegment(alias: String): String =
        alias.replace('-', '.').replace('_', '.')

    /** Declaration for an alias under [tableName], or null for tables that have no accessor. */
    fun declaration(tableName: String?, prefix: String, alias: String): String? {
        val seg = accessorSegment(alias)
        return when (tableName) {
            "libraries" -> "implementation($prefix.$seg)"
            "bundles" -> "implementation($prefix.bundles.$seg)"
            "plugins" -> "alias($prefix.plugins.$seg)"
            else -> null
        }
    }

    /** Declaration for [entry], or null if it is not a libraries/plugins/bundles entry. */
    fun declarationForEntry(file: PsiFile, entry: TomlKeyValue): String? {
        val table = entry.parent as? TomlTable ?: return null
        val tableName = table.header.key?.segments?.singleOrNull()?.name ?: return null
        // A dotted TOML key (`foo.bar = ...`) is parsed into multiple segments; Gradle treats each dot as
        // a sub-accessor separator, so the whole key must be preserved (firstOrNull would drop `.bar`).
        val alias = entry.key.segments.mapNotNull { it.name }.joinToString(".").ifEmpty { return null }
        return declaration(tableName, catalogName(file), alias)
    }
}
