package io.github.tonygnk.cataloglens

import io.github.tonygnk.cataloglens.releases.DevsiteChangelogConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevsiteChangelogConverterTest {

    private val baseUri = "https://developer.android.com/jetpack/androidx/releases/room"

    @Test
    fun `scopes to article body and splits into version sections`() {
        val html = """
            <html><body>
              <nav class="devsite-nav"><a href="/jetpack/androidx/releases/activity">Activity</a></nav>
              <div class="devsite-article-body">
                <h2>Version 2.8</h2>
                <h3>Version 2.8.4</h3>
                <p>November 19, 2025</p>
                <p><code>androidx.room:room-*:2.8.4</code> is released.
                   Version 2.8.4 contains <a href="/commit/abc">these commits</a>.</p>
                <h4>Bug Fixes</h4>
                <ul>
                  <li>Added a prepared statement cache. (<a href="https://r.example/5f43bc">5f43bc</a>)</li>
                  <li>Fix an issue with <strong>Room</strong>'s code generation.</li>
                </ul>
                <devsite-feedback>noise</devsite-feedback>
              </div>
              <footer>copyright</footer>
            </body></html>
        """.trimIndent()

        val sections = DevsiteChangelogConverter.toSections(html, baseUri)

        assertEquals(2, sections.size)
        assertEquals("2.8", sections[0].version)
        assertEquals("Version 2.8", sections[0].header)
        assertEquals("2.8.4", sections[1].version)
        assertEquals("Version 2.8.4", sections[1].header)

        val body = sections[1].markdown
        assertTrue(body.contains("### Bug Fixes"))
        assertTrue(body.contains("- Added a prepared statement cache. ([5f43bc](https://r.example/5f43bc))"))
        assertTrue(body.contains("**Room**"))
        // Relative link resolved to absolute against the base URI.
        assertTrue(body.contains("[these commits](https://developer.android.com/commit/abc)"))

        // Nav, footer and feedback noise dropped everywhere.
        val all = sections.joinToString("\n") { it.header + "\n" + it.markdown }
        assertTrue(!all.contains("Activity"))
        assertTrue(!all.contains("copyright"))
        assertTrue(!all.contains("noise"))
    }

    @Test
    fun `drops preamble before the first Version heading`() {
        val html = """
            <html><body>
              <div class="devsite-article-body">
                <h1>Room</h1>
                <h2>Declaring dependencies</h2>
                <p>To add a dependency on Room, add the Google Maven repository.</p>
                <h2>Configuring Compiler Options</h2>
                <p>Room has the following annotation processor options.</p>
                <h2>Version 2.8</h2>
                <h3>Version 2.8.4</h3>
                <p>November 19, 2025</p>
              </div>
            </body></html>
        """.trimIndent()

        val sections = DevsiteChangelogConverter.toSections(html, baseUri)

        assertEquals("Version 2.8", sections.first().header)
        val all = sections.joinToString("\n") { it.header + "\n" + it.markdown }
        assertTrue(!all.contains("Declaring dependencies"))
        assertTrue(!all.contains("Configuring Compiler Options"))
    }

    @Test
    fun `keeps a single null-version section when no Version heading is present`() {
        val html = """
            <html><body>
              <div class="devsite-article-body">
                <h2>Android Gradle plugin 8.5.0</h2>
                <p>Some notes.</p>
              </div>
            </body></html>
        """.trimIndent()

        val sections = DevsiteChangelogConverter.toSections(html, baseUri)

        assertEquals(1, sections.size)
        assertEquals(null, sections.single().version)
        assertTrue(sections.single().markdown.contains("# Android Gradle plugin 8.5.0"))
    }

    @Test
    fun `returns empty when article body is missing`() {
        val html = "<html><body><nav>only nav here</nav></body></html>"
        assertTrue(DevsiteChangelogConverter.toSections(html, baseUri).isEmpty())
    }

    @Test
    fun `returns empty when article body has no headings`() {
        val html = """
            <html><body>
              <div class="devsite-article-body"><p>Just a sentence, no changelog.</p></div>
            </body></html>
        """.trimIndent()
        assertTrue(DevsiteChangelogConverter.toSections(html, baseUri).isEmpty())
    }

    @Test
    fun `collapses whitespace in inline text`() {
        val html = """
            <div class="devsite-article-body">
              <h3>Version 1.0</h3>
              <p>line one
                 still   line one</p>
            </div>
        """.trimIndent()
        val sections = DevsiteChangelogConverter.toSections(html, baseUri)
        assertTrue(sections.single().markdown.contains("line one still line one"))
    }
}
