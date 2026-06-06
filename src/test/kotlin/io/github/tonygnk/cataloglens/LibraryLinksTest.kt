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
            "https://maven.google.com/web/index.html#androidx.core:core-ktx",
        )
    }

    fun testModuleVersionRef() {
        assertContainsElements(
            webUrls(),
            "https://central.sonatype.com/artifact/com.squareup.okhttp3/okhttp/1.13.0/versions",
        )
    }

    fun testModuleLiteralVersion() {
        assertContainsElements(
            webUrls(),
            "https://central.sonatype.com/artifact/io.coil-kt/coil/2.6.0/versions",
        )
    }

    fun testStringNotation() {
        assertContainsElements(
            webUrls(),
            "https://central.sonatype.com/artifact/com.google.code.gson/gson/2.11.0/versions",
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
            "https://maven.google.com/web/index.html#androidx.core:core-ktx",
        )
    }

    fun testGoogleMavenRouting() {
        myFixture.configureByText(
            "libs.versions.toml",
            """
            [libraries]
            messaging = { group = "com.google.firebase", name = "firebase-messaging", version = "25.0.0" }
            material = { module = "com.google.android.material:material", version = "1.13.0" }
            desugar = { module = "com.android.tools:desugar_jdk_libs", version = "2.1.5" }
            """.trimIndent(),
        )
        val urls = PsiTreeUtil.findChildrenOfType(myFixture.file, TomlLiteral::class.java)
            .flatMap { it.references.toList() }
            .filterIsInstance<WebReference>()
            .mapNotNull { it.url }
        assertContainsElements(
            urls,
            "https://maven.google.com/web/index.html#com.google.firebase:firebase-messaging",
            "https://maven.google.com/web/index.html#com.google.android.material:material",
            "https://maven.google.com/web/index.html#com.android.tools:desugar_jdk_libs",
        )
    }

    fun testCentralHostedGoogleGroupsStayOnCentral() {
        myFixture.configureByText(
            "libs.versions.toml",
            """
            [libraries]
            hilt = { group = "com.google.dagger", name = "hilt-android", version = "2.57" }
            jbCompose = { module = "org.jetbrains.compose.ui:ui", version = "1.11.1" }
            """.trimIndent(),
        )
        val urls = PsiTreeUtil.findChildrenOfType(myFixture.file, TomlLiteral::class.java)
            .flatMap { it.references.toList() }
            .filterIsInstance<WebReference>()
            .mapNotNull { it.url }
        assertContainsElements(
            urls,
            "https://central.sonatype.com/artifact/com.google.dagger/hilt-android/2.57/versions",
            "https://central.sonatype.com/artifact/org.jetbrains.compose.ui/ui/1.11.1/versions",
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
