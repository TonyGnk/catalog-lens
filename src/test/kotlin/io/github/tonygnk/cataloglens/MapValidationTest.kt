package io.github.tonygnk.cataloglens

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

class MapValidationTest {

    private class MapData(
        val artifacts: Map<String, List<String>> = emptyMap(),
        val groupPrefixes: Map<String, List<String>> = emptyMap(),
    )

    private val data: MapData by lazy {
        val stream = MapValidationTest::class.java.getResourceAsStream("/cataloglens/artifact-map.json")
        assertNotNull("artifact-map.json missing from resources", stream)
        stream!!.use { Gson().fromJson(InputStreamReader(it, Charsets.UTF_8), MapData::class.java) }
    }

    @Test
    fun mapsAreNonEmpty() {
        assertTrue(data.artifacts.isNotEmpty())
        assertTrue(data.groupPrefixes.isNotEmpty())
    }

    @Test
    fun noBlankKeysOrEmptyValues() {
        for ((key, urls) in data.artifacts + data.groupPrefixes) {
            assertTrue("blank key", key.isNotBlank())
            assertTrue("empty url list for $key", urls.isNotEmpty())
            assertTrue("blank url for $key", urls.all { it.isNotBlank() })
        }
    }

    @Test
    fun allUrlsAreHttps() {
        for ((key, urls) in data.artifacts + data.groupPrefixes) {
            urls.forEach { url ->
                assertTrue("non-https url for $key: $url", url.startsWith("https://"))
            }
        }
    }

    @Test
    fun prefixKeysMatchOnDotBoundaries() {
        for (key in data.groupPrefixes.keys) {
            assertFalse("prefix key must not end with dot: $key", key.endsWith("."))
            assertFalse("prefix key must not contain whitespace: $key", key.any { it.isWhitespace() })
        }
    }
}
