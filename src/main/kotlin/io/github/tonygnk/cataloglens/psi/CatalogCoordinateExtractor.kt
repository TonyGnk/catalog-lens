package io.github.tonygnk.cataloglens.psi

import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

data class Coord(val group: String, val name: String, val version: String?) {
    val key: String get() = "$group:$name"
}

object CatalogCoordinateExtractor {

    fun coordinateForLinkLiteral(literal: TomlLiteral): Coord? {
        val kv = literal.parent as? TomlKeyValue ?: return null
        val file = literal.containingFile as? TomlFile ?: return null
        return when (val container = kv.parent) {
            is TomlInlineTable -> {
                if (keyText(kv.key) !in LINK_KEYS) return null
                val outerKv = container.parent as? TomlKeyValue ?: return null
                val table = outerKv.parent as? TomlTable ?: return null
                if (headerName(table) != "libraries") return null
                coordinateFromMap(flatten(container.entries), file)
            }
            is TomlTable -> {
                if (headerName(container) != "libraries") return null
                val segments = kv.key.segments.mapNotNull { it.name }
                when {
                    // String notation: lib = "group:name:version"
                    segments.size == 1 -> coordinateFromNotation(literal)
                    segments.last() in LINK_KEYS ->
                        coordinateFromMap(longFormMap(container, segments.first()), file)
                    else -> null
                }
            }
            else -> null
        }
    }

    fun coordinatesForVersionRef(file: TomlFile, refName: String): List<Coord> {
        val libraries = topLevelTable(file, "libraries") ?: return emptyList()
        val result = mutableListOf<Coord>()

        for (entry in libraries.entries) {
            val segments = entry.key.segments.mapNotNull { it.name }
            val map = when {
                entry.value is TomlInlineTable && segments.size == 1 ->
                    flatten((entry.value as TomlInlineTable).entries)
                segments.size > 1 -> {
                    // Long form: only process the alias once, at its first entry
                    val alias = segments.first()
                    val first = libraries.entries.first {
                        it.key.segments.firstOrNull()?.name == alias
                    }
                    if (first != entry) continue
                    longFormMap(libraries, alias)
                }
                else -> continue
            }
            if (map["version.ref"] != refName) continue
            coordinateFromMap(map, file)?.let { result.add(it) }
        }
        return result.distinct()
    }

    fun pluginIdsForVersionRef(file: TomlFile, refName: String): List<String> {
        val plugins = topLevelTable(file, "plugins") ?: return emptyList()
        return plugins.entries.mapNotNull { entry ->
            val inline = entry.value as? TomlInlineTable ?: return@mapNotNull null
            val map = flatten(inline.entries)
            if (map["version.ref"] != refName) return@mapNotNull null
            map["id"]
        }.distinct()
    }

    fun resolveVersionRef(file: TomlFile, refName: String): String? {
        val versions = topLevelTable(file, "versions") ?: return null
        val entry = versions.entries.firstOrNull { keyText(it.key) == refName } ?: return null
        return (entry.value as? TomlLiteral)?.stringValue()
    }

    private fun coordinateFromNotation(literal: TomlLiteral): Coord? {
        val parts = literal.stringValue()?.split(":") ?: return null
        return when (parts.size) {
            2 -> Coord(parts[0], parts[1], null)
            3 -> Coord(parts[0], parts[1], parts[2])
            else -> null
        }
    }

    private fun coordinateFromMap(map: Map<String, String?>, file: TomlFile): Coord? {
        val group = map["group"]
        val name = map["name"]
        val module = map["module"]
        val (g, n) = when {
            group != null && name != null -> group to name
            module != null && ":" in module -> module.split(":", limit = 2).let { it[0] to it[1] }
            else -> return null
        }
        val version = map["version"]
            ?: map["version.ref"]?.let { resolveVersionRef(file, it) }
        return Coord(g, n, version)
    }

    private fun longFormMap(table: TomlTable, alias: String): Map<String, String?> {
        val siblings = table.entries.filter { it.key.segments.firstOrNull()?.name == alias }
        return flatten(siblings, dropFirstSegment = true)
    }

    private fun flatten(
        entries: List<TomlKeyValue>,
        dropFirstSegment: Boolean = false,
    ): Map<String, String?> {
        val out = mutableMapOf<String, String?>()
        fun walk(entries: List<TomlKeyValue>, prefix: List<String>) {
            for (e in entries) {
                var segments = e.key.segments.mapNotNull { it.name }
                if (dropFirstSegment && prefix.isEmpty()) segments = segments.drop(1)
                val path = prefix + segments
                when (val v = e.value) {
                    is TomlInlineTable -> walk(v.entries, path)
                    is TomlLiteral -> out[path.joinToString(".")] = v.stringValue()
                    else -> {}
                }
            }
        }
        walk(entries, emptyList())
        return out
    }

    private fun topLevelTable(file: TomlFile, name: String): TomlTable? =
        file.children.filterIsInstance<TomlTable>().firstOrNull { headerName(it) == name }

    private fun keyText(key: TomlKey): String =
        key.segments.joinToString(".") { it.name.orEmpty() }

    private val LINK_KEYS = setOf("name", "module")
}

fun TomlLiteral.stringValue(): String? = (kind as? TomlLiteralKind.String)?.value

internal fun headerName(table: TomlTable): String? =
    table.header.key?.segments?.singleOrNull()?.name
