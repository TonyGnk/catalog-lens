package io.github.tonygnk.cataloglens.map

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.logger
import java.io.InputStreamReader

object BundledArtifactMap {

    private val LOG = logger<BundledArtifactMap>()

    private class MapData(
        val artifacts: Map<String, List<String>> = emptyMap(),
        val groupPrefixes: Map<String, List<String>> = emptyMap(),
    )

    private val data: MapData by lazy {
        try {
            javaClass.getResourceAsStream("/cataloglens/artifact-map.json")?.use { stream ->
                Gson().fromJson(InputStreamReader(stream, Charsets.UTF_8), MapData::class.java)
            } ?: MapData()
        } catch (e: Exception) {
            LOG.warn("Failed to load bundled artifact map", e)
            MapData()
        }
    }

    fun exact(key: String): List<String>? = data.artifacts[key]

    fun byGroupPrefix(group: String): List<String>? =
        data.groupPrefixes.entries
            .filter { group == it.key || group.startsWith(it.key + ".") }
            .maxByOrNull { it.key.length }
            ?.value
}
