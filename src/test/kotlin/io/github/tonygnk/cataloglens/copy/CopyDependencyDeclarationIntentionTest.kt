package io.github.tonygnk.cataloglens.copy

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.DataFlavor

class CopyDependencyDeclarationIntentionTest : BasePlatformTestCase() {

    private fun copied(): String? =
        CopyPasteManager.getInstance().contents?.getTransferData(DataFlavor.stringFlavor) as? String

    private fun invokeCopy(fileName: String, text: String, intentionText: String) {
        myFixture.configureByText(fileName, text)
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
    }

    fun testLibraryEntry() {
        invokeCopy(
            "libs.versions.toml",
            """
            [libraries]
            squareup-okhttp3 = { group = "com.squareup.okhttp3", name = "okhttp", version<caret> = "4.12.0" }
            other = { module = "io.coil-kt:coil", version = "2.6.0" }
            """.trimIndent(),
            "Copy implementation(libs.squareup.okhttp3)",
        )
        assertEquals("implementation(libs.squareup.okhttp3)", copied())
    }

    fun testPluginEntry() {
        invokeCopy(
            "libs.versions.toml",
            """
            [plugins]
            kotlin<caret>-android = { id = "org.jetbrains.kotlin.android", version = "2.1.0" }
            """.trimIndent(),
            "Copy alias(libs.plugins.kotlin.android)",
        )
        assertEquals("alias(libs.plugins.kotlin.android)", copied())
    }

    fun testBundleEntry() {
        invokeCopy(
            "libs.versions.toml",
            """
            [bundles]
            network<caret>ing = ["a", "b"]
            """.trimIndent(),
            "Copy implementation(libs.bundles.networking)",
        )
        assertEquals("implementation(libs.bundles.networking)", copied())
    }

    fun testPrefixDerivedFromFileName() {
        invokeCopy(
            "deps.versions.toml",
            """
            [libraries]
            squareup<caret>-okhttp3 = { module = "com.squareup.okhttp3:okhttp", version = "4.12.0" }
            """.trimIndent(),
            "Copy implementation(deps.squareup.okhttp3)",
        )
        assertEquals("implementation(deps.squareup.okhttp3)", copied())
    }

    // Regression: a dotted TOML key parses into multiple segments; every segment is a Gradle sub-accessor
    // and must be preserved (the bug dropped all but the first → libs.androidx instead of libs.androidx.core.ktx).
    fun testDottedAliasStringNotationPreservesAllSegments() {
        invokeCopy(
            "libs.versions.toml",
            """
            [libraries]
            androidx.core.ktx<caret> = "androidx.core:core-ktx:1.13.0"
            """.trimIndent(),
            "Copy implementation(libs.androidx.core.ktx)",
        )
        assertEquals("implementation(libs.androidx.core.ktx)", copied())
    }

    fun testDottedAliasInlineTablePreservesAllSegments() {
        invokeCopy(
            "libs.versions.toml",
            """
            [libraries]
            my.lib<caret>.core = { module = "com.example:core", version = "1.0" }
            """.trimIndent(),
            "Copy implementation(libs.my.lib.core)",
        )
        assertEquals("implementation(libs.my.lib.core)", copied())
    }

    // Coverage gap: string-notation library (no inline table). Copy only needs alias + table, so this
    // form was never exercised by the original tests.
    fun testStringNotationLibrary() {
        invokeCopy(
            "libs.versions.toml",
            """
            [libraries]
            coil<caret> = "io.coil-kt:coil:2.6.0"
            """.trimIndent(),
            "Copy implementation(libs.coil)",
        )
        assertEquals("implementation(libs.coil)", copied())
    }

    fun testNotAvailableInVersionsTable() {
        myFixture.configureByText(
            "libs.versions.toml",
            """
            [versions]
            ktx = "1.13.0"<caret>
            """.trimIndent(),
        )
        assertEmpty(
            myFixture.availableIntentions.filter { it.familyName == "Copy Gradle dependency declaration" },
        )
    }

    fun testNotAvailableInArbitraryToml() {
        myFixture.configureByText(
            "config.toml",
            """
            [libraries]
            a = { module = "androidx.core:core-ktx", version = "1.0" }<caret>
            """.trimIndent(),
        )
        assertEmpty(
            myFixture.availableIntentions.filter { it.familyName == "Copy Gradle dependency declaration" },
        )
    }
}
