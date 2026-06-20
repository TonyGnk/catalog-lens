package io.github.tonygnk.cataloglens.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogAccessorTest {

    @Test
    fun accessorSegmentNormalizesSeparators() {
        assertEquals("squareup.okhttp3", CatalogAccessor.accessorSegment("squareup-okhttp3"))
        assertEquals("androidx.core.ktx", CatalogAccessor.accessorSegment("androidx-core-ktx"))
        assertEquals("kotlinx.serialization", CatalogAccessor.accessorSegment("kotlinx_serialization"))
        assertEquals("coil", CatalogAccessor.accessorSegment("coil"))
    }

    @Test
    fun declarationPerTableType() {
        assertEquals(
            "implementation(libs.squareup.okhttp3)",
            CatalogAccessor.declaration("libraries", "libs", "squareup-okhttp3"),
        )
        assertEquals(
            "implementation(libs.bundles.networking)",
            CatalogAccessor.declaration("bundles", "libs", "networking"),
        )
        assertEquals(
            "alias(libs.plugins.kotlin.android)",
            CatalogAccessor.declaration("plugins", "libs", "kotlin-android"),
        )
        assertEquals(
            "implementation(deps.squareup.okhttp3)",
            CatalogAccessor.declaration("libraries", "deps", "squareup-okhttp3"),
        )
    }

    @Test
    fun declarationNullForNonAccessorTables() {
        assertNull(CatalogAccessor.declaration("versions", "libs", "ktx"))
        assertNull(CatalogAccessor.declaration(null, "libs", "ktx"))
    }
}
