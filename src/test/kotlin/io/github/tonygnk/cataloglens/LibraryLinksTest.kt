package io.github.tonygnk.cataloglens

import com.intellij.openapi.paths.WebReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.toml.lang.psi.TomlLiteral

class LibraryLinksTest : BasePlatformTestCase() {

    private val catalog = """
        [versions]
        ktx = "1.13.0"

        [libraries]
        a = { group = "androidx.core", name = "core-ktx", version.ref = "ktx" }
        b = { module = "com.squareup.okhttp3:okhttp", version.ref = "ktx" }
        c = { module = "io.coil-kt:coil", version = "2.6.0" }
        e = "com.google.code.gson:gson:2.11.0"

        [bundles]
        all = ["a", "b"]
    """.trimIndent()

    private fun webUrls(): List<String> {
        myFixture.configureByText("libs.versions.toml", catalog)
        return PsiTreeUtil.findChildrenOfType(myFixture.file, TomlLiteral::class.java)
            .flatMap { it.references.toList() }
            .filterIsInstance<WebReference>()
            .mapNotNull { it.url }
    }

    fun testGroupNameVersionRef() {
        assertContainsElements(
            webUrls(),
            "https://central.sonatype.com/artifact/androidx.core/core-ktx/1.13.0",
        )
    }

    fun testModuleVersionRef() {
        assertContainsElements(
            webUrls(),
            "https://central.sonatype.com/artifact/com.squareup.okhttp3/okhttp/1.13.0",
        )
    }

    fun testModuleLiteralVersion() {
        assertContainsElements(
            webUrls(),
            "https://central.sonatype.com/artifact/io.coil-kt/coil/2.6.0",
        )
    }

    fun testStringNotation() {
        assertContainsElements(
            webUrls(),
            "https://central.sonatype.com/artifact/com.google.code.gson/gson/2.11.0",
        )
    }

    fun testLongFormDottedKeys() {
        myFixture.configureByText(
            "libs.versions.toml",
            """
            [versions]
            ktx = "1.13.0"

            [libraries]
            d.module = "androidx.core:core-ktx"
            d.version.ref = "ktx"
            """.trimIndent(),
        )
        val urls = PsiTreeUtil.findChildrenOfType(myFixture.file, TomlLiteral::class.java)
            .flatMap { it.references.toList() }
            .filterIsInstance<WebReference>()
            .mapNotNull { it.url }
        assertContainsElements(
            urls,
            "https://central.sonatype.com/artifact/androidx.core/core-ktx/1.13.0",
        )
    }

    fun testNoLinksOutsideLibraries() {
        myFixture.configureByText(
            "libs.versions.toml",
            """
            [versions]
            ktx = "1.13.0"

            [plugins]
            kotlin = { id = "org.jetbrains.kotlin.android", version.ref = "ktx" }
            """.trimIndent(),
        )
        val urls = PsiTreeUtil.findChildrenOfType(myFixture.file, TomlLiteral::class.java)
            .flatMap { it.references.toList() }
            .filterIsInstance<WebReference>()
        assertEmpty(urls.toList())
    }

    fun testNoLinksInArbitraryToml() {
        myFixture.configureByText(
            "config.toml",
            """
            [libraries]
            a = { group = "androidx.core", name = "core-ktx", version = "1.0" }
            """.trimIndent(),
        )
        val urls = PsiTreeUtil.findChildrenOfType(myFixture.file, TomlLiteral::class.java)
            .flatMap { it.references.toList() }
            .filterIsInstance<WebReference>()
        assertEmpty(urls.toList())
    }
}
