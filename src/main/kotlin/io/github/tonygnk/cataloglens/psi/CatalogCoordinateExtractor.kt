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
                coordinateFromMap(flatten(container.entries)) { resolveVersionRef(file, it) }
            }
            is TomlTable -> {
                if (headerName(container) != "libraries") return null
                val segments = kv.key.segments.mapNotNull { it.name }
                when {
                    // String notation: lib = "group:name:version"
                    segments.size == 1 -> coordinateFromNotation(literal)
                    segments.last() in LINK_KEYS ->
                        coordinateFromMap(longFormMap(container, segments.first())) {
                            resolveVersionRef(file, it)
                        }
                    else -> null
                }
            }
            else -> null
        }
    }

    fun coordinatesForVersionRef(file: TomlFile, refName: String): List<Coord> =
        catalogIndex(file).refCoords[refName].orEmpty()

    fun pluginIdsForVersionRef(file: TomlFile, refName: String): List<String> =
        catalogIndex(file).refPluginIds[refName].orEmpty()

    fun resolveVersionRef(file: TomlFile, refName: String): String? =
        catalogIndex(file).versions[refName]

    private fun coordinateFromNotation(literal: TomlLiteral): Coord? {
        val parts = literal.stringValue()?.split(":") ?: return null
        return when (parts.size) {
            2 -> Coord(parts[0], parts[1], null)
            3 -> Coord(parts[0], parts[1], parts[2])
            else -> null
        }
    }

    internal fun coordinateFromMap(
        map: Map<String, String?>,
        resolveRef: (String) -> String?,
    ): Coord? {
        val group = map["group"]
        val name = map["name"]
        val module = map["module"]
        val (g, n) = when {
            group != null && name != null -> group to name
            module != null && ":" in module -> module.split(":", limit = 2).let { it[0] to it[1] }
            else -> return null
        }
        val version = map["version"] ?: map["version.ref"]?.let(resolveRef)
        return Coord(g, n, version)
    }

    private fun longFormMap(table: TomlTable, alias: String): Map<String, String?> {
        val siblings = table.entries.filter { it.key.segments.firstOrNull()?.name == alias }
        return flatten(siblings, dropFirstSegment = true)
    }

    private val LINK_KEYS = setOf("name", "module")
}

internal fun flatten(
    entries: List<TomlKeyValue>,
    dropFirstSegment: Boolean = false,
): Map<String, String?> {
    val out = mutableMapOf<String, String?>()
    fun walk(entries: List<TomlKeyValue>, prefix: List<String>, dropFirst: Boolean) {
        for (e in entries) {
            var segments = e.key.segments.mapNotNull { it.name }
            if (dropFirst) segments = segments.drop(1)
            val path = prefix + segments
            when (val v = e.value) {
                is TomlInlineTable -> walk(v.entries, path, dropFirst = false)
                is TomlLiteral -> out[path.joinToString(".")] = v.stringValue()
                else -> {}
            }
        }
    }
    walk(entries, emptyList(), dropFirstSegment)
    return out
}

internal fun topLevelTable(file: TomlFile, name: String): TomlTable? =
    file.children.filterIsInstance<TomlTable>().firstOrNull { headerName(it) == name }

internal fun keyText(key: TomlKey): String =
    key.segments.joinToString(".") { it.name.orEmpty() }

fun TomlLiteral.stringValue(): String? = (kind as? TomlLiteralKind.String)?.value

internal fun headerName(table: TomlTable): String? =
    table.header.key?.segments?.singleOrNull()?.name
