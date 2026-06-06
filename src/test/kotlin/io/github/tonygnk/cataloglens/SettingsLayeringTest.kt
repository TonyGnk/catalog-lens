package io.github.tonygnk.cataloglens

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.tonygnk.cataloglens.map.VersionUrlResolver
import io.github.tonygnk.cataloglens.psi.Coord
import io.github.tonygnk.cataloglens.settings.CatalogLensGlobalSettings
import io.github.tonygnk.cataloglens.settings.CatalogLensProjectSettings

class SettingsLayeringTest : BasePlatformTestCase() {

    private val okhttp = Coord("com.squareup.okhttp3", "okhttp", null)

    override fun setUp() {
        super.setUp()
        CatalogLensGlobalSettings.getInstance().loadState(CatalogLensGlobalSettings.State())
        CatalogLensProjectSettings.getInstance(project).loadState(CatalogLensProjectSettings.State())
    }

    override fun tearDown() {
        try {
            CatalogLensGlobalSettings.getInstance().loadState(CatalogLensGlobalSettings.State())
            CatalogLensProjectSettings.getInstance(project).loadState(CatalogLensProjectSettings.State())
        } finally {
            super.tearDown()
        }
    }

    fun testBundledMapDisabledYieldsEmpty() {
        CatalogLensGlobalSettings.getInstance().state.useBundledMap = false
        assertEmpty(VersionUrlResolver.resolveLibrary(project, okhttp))
    }

    fun testGlobalMappingShadowsBundled() {
        CatalogLensGlobalSettings.getInstance().state.mappings["com.squareup.okhttp3:okhttp"] =
            "https://example.com/global"
        assertEquals(
            listOf("https://example.com/global"),
            VersionUrlResolver.resolveLibrary(project, okhttp),
        )
    }

    fun testProjectMappingShadowsGlobal() {
        CatalogLensGlobalSettings.getInstance().state.mappings["com.squareup.okhttp3:okhttp"] =
            "https://example.com/global"
        CatalogLensProjectSettings.getInstance(project).state.mappings["com.squareup.okhttp3:okhttp"] =
            "https://example.com/project"
        assertEquals(
            listOf("https://example.com/project"),
            VersionUrlResolver.resolveLibrary(project, okhttp),
        )
    }

    fun testUserMappingWorksWhenBundledDisabled() {
        val global = CatalogLensGlobalSettings.getInstance().state
        global.useBundledMap = false
        global.mappings["com.example"] = "https://example.com/a https://example.com/b"
        assertEquals(
            listOf("https://example.com/a", "https://example.com/b"),
            VersionUrlResolver.resolveLibrary(project, Coord("com.example.sub", "thing", null)),
        )
    }

    fun testUserPrefixMappingDotBoundary() {
        val global = CatalogLensGlobalSettings.getInstance().state
        global.useBundledMap = false
        global.mappings["com.example"] = "https://example.com/a"
        assertEmpty(
            VersionUrlResolver.resolveLibrary(project, Coord("com.examples", "thing", null)),
        )
    }
}
