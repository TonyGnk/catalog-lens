package io.github.tonygnk.cataloglens.map

import com.intellij.openapi.project.Project
import io.github.tonygnk.cataloglens.psi.Coord
import io.github.tonygnk.cataloglens.settings.CatalogLensGlobalSettings
import io.github.tonygnk.cataloglens.settings.CatalogLensProjectSettings

object VersionUrlResolver {

    fun resolveLibrary(project: Project, coord: Coord): List<String> =
        resolve(project, exactKey = coord.key, prefixSource = coord.group)

    fun resolvePluginId(project: Project, pluginId: String): List<String> =
        resolve(project, exactKey = pluginId, prefixSource = pluginId)

    private fun resolve(project: Project, exactKey: String, prefixSource: String): List<String> {
        val global = CatalogLensGlobalSettings.getInstance().state

        lookupUserMappings(
            CatalogLensProjectSettings.getInstance(project).state.mappings, exactKey, prefixSource,
        )?.let { return it }

        lookupUserMappings(global.mappings, exactKey, prefixSource)?.let { return it }

        if (!global.useBundledMap) return emptyList()
        BundledArtifactMap.exact(exactKey)?.let { return it }
        return BundledArtifactMap.byGroupPrefix(prefixSource).orEmpty()
    }

    private fun lookupUserMappings(
        mappings: Map<String, String>,
        exactKey: String,
        prefixSource: String,
    ): List<String>? {
        mappings[exactKey]?.let { return splitUrls(it) }
        return mappings.entries
            .filter { prefixSource == it.key || prefixSource.startsWith(it.key + ".") }
            .maxByOrNull { it.key.length }
            ?.let { splitUrls(it.value) }
    }

    private fun splitUrls(value: String): List<String> =
        value.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
}
