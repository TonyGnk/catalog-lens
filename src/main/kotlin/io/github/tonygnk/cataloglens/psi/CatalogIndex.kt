package io.github.tonygnk.cataloglens.psi

import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

internal class CatalogIndex(
    val versions: Map<String, String>,
    val refCoords: Map<String, List<Coord>>,
    val refPluginIds: Map<String, List<String>>,
)

private val INDEX_KEY = Key.create<CachedValue<CatalogIndex>>("cataloglens.index")

internal fun catalogIndex(file: TomlFile): CatalogIndex =
    CachedValuesManager.getManager(file.project).getCachedValue(
        file,
        INDEX_KEY,
        { CachedValueProvider.Result.create(buildIndex(file), file) },
        false,
    )

private fun buildIndex(file: TomlFile): CatalogIndex {
    val versions = LinkedHashMap<String, String>()
    topLevelTable(file, "versions")?.entries?.forEach { entry ->
        val value = (entry.value as? TomlLiteral)?.stringValue() ?: return@forEach
        versions.putIfAbsent(keyText(entry.key), value)
    }

    val refCoords = LinkedHashMap<String, LinkedHashSet<Coord>>()
    topLevelTable(file, "libraries")?.let { table ->
        for (map in aliasMaps(table)) {
            val ref = map["version.ref"] ?: continue
            val coord = CatalogCoordinateExtractor.coordinateFromMap(map) { versions[it] } ?: continue
            refCoords.getOrPut(ref) { LinkedHashSet() }.add(coord)
        }
    }

    val refPluginIds = LinkedHashMap<String, LinkedHashSet<String>>()
    topLevelTable(file, "plugins")?.let { table ->
        for (map in aliasMaps(table)) {
            val ref = map["version.ref"] ?: continue
            val id = map["id"] ?: continue
            refPluginIds.getOrPut(ref) { LinkedHashSet() }.add(id)
        }
    }

    return CatalogIndex(
        versions,
        refCoords.mapValues { it.value.toList() },
        refPluginIds.mapValues { it.value.toList() },
    )
}

private fun aliasMaps(table: TomlTable): List<Map<String, String?>> {
    val groups = LinkedHashMap<String, MutableList<TomlKeyValue>>()
    for (entry in table.entries) {
        val alias = entry.key.segments.firstOrNull()?.name ?: continue
        groups.getOrPut(alias) { mutableListOf() }.add(entry)
    }
    return groups.values.map { entries ->
        val single = entries.singleOrNull()
        if (single != null && single.key.segments.size == 1) {
            val inline = single.value as? TomlInlineTable ?: return@map emptyMap()
            flatten(inline.entries)
        } else {
            flatten(entries, dropFirstSegment = true)
        }
    }
}
