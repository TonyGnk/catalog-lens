package io.github.tonygnk.cataloglens

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.tonygnk.cataloglens.map.BundledArtifactMap
import io.github.tonygnk.cataloglens.map.VersionUrlResolver
import io.github.tonygnk.cataloglens.psi.CatalogCoordinateExtractor
import io.github.tonygnk.cataloglens.psi.Coord
import org.toml.lang.psi.TomlFile

class VersionMarkersTest : BasePlatformTestCase() {

    private val catalog = """
        [versions]
        okhttp = "4.12.0"
        coilVersion = "2.6.0"
        agp = "8.5.0"
        unknown = "1.0.0"

        [libraries]
        okhttp-core = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
        okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
        coil = { module = "io.coil-kt:coil-compose", version.ref = "coilVersion" }
        mystery = { module = "com.example.internal:thing", version.ref = "unknown" }

        [plugins]
        android-application = { id = "com.android.application", version.ref = "agp" }
    """.trimIndent()

    private fun tomlFile(): TomlFile {
        myFixture.configureByText("libs.versions.toml", catalog)
        return myFixture.file as TomlFile
    }

    fun testCoordinatesForVersionRef() {
        val coords = CatalogCoordinateExtractor.coordinatesForVersionRef(tomlFile(), "okhttp")
        assertSameElements(
            coords,
            Coord("com.squareup.okhttp3", "okhttp", "4.12.0"),
            Coord("com.squareup.okhttp3", "logging-interceptor", "4.12.0"),
        )
    }

    fun testPluginIdsForVersionRef() {
        val ids = CatalogCoordinateExtractor.pluginIdsForVersionRef(tomlFile(), "agp")
        assertSameElements(ids, "com.android.application")
    }

    fun testExactArtifactLookup() {
        val urls = VersionUrlResolver.resolveLibrary(
            project, Coord("com.squareup.okhttp3", "okhttp", null),
        )
        assertContainsElements(urls, "https://square.github.io/okhttp/changelogs/changelog/")
    }

    fun testGroupPrefixFallback() {
        // logging-interceptor has no exact entry; group prefix com.squareup.okhttp3 catches it
        val urls = VersionUrlResolver.resolveLibrary(
            project, Coord("com.squareup.okhttp3", "logging-interceptor", null),
        )
        assertContainsElements(urls, "https://square.github.io/okhttp/changelogs/changelog/")
    }

    fun testPrefixMatchesOnDotBoundaryOnly() {
        // group "androidx.roomba" must NOT match prefix "androidx.room", but matches "androidx"
        val urls = BundledArtifactMap.byGroupPrefix("androidx.roomba")
        assertEquals(listOf("https://developer.android.com/jetpack/androidx/versions"), urls)
    }

    fun testLongestPrefixWins() {
        val urls = BundledArtifactMap.byGroupPrefix("androidx.room")
        assertEquals(listOf("https://developer.android.com/jetpack/androidx/releases/room"), urls)
    }

    fun testPluginIdResolution() {
        val urls = VersionUrlResolver.resolvePluginId(project, "com.android.application")
        assertContainsElements(urls, "https://developer.android.com/build/releases/gradle-plugin")
    }

    fun testUnknownArtifactResolvesEmpty() {
        val urls = VersionUrlResolver.resolveLibrary(
            project, Coord("com.example.internal", "thing", null),
        )
        assertEmpty(urls)
    }

    fun testGutterMarkersAppearOnMappedVersionKeys() {
        myFixture.configureByText("libs.versions.toml", catalog)
        val notesGutters = myFixture.findAllGutters()
            .filter { it.tooltipText == "Open upstream release notes" }
        // okhttp, coilVersion, agp mapped; unknown not -> exactly 3
        assertEquals(3, notesGutters.size)
    }

    fun testLongFormPluginVersionRef() {
        myFixture.configureByText(
            "libs.versions.toml",
            """
            [versions]
            agp = "8.5.0"

            [plugins]
            android-library.id = "com.android.library"
            android-library.version.ref = "agp"
            """.trimIndent(),
        )
        val ids = CatalogCoordinateExtractor.pluginIdsForVersionRef(
            myFixture.file as TomlFile, "agp",
        )
        assertSameElements(ids, "com.android.library")

        val notesGutters = myFixture.findAllGutters()
            .filter { it.tooltipText == "Open upstream release notes" }
        assertEquals(1, notesGutters.size)
    }
}
