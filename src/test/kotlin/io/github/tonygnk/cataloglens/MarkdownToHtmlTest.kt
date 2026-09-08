package io.github.tonygnk.cataloglens

import io.github.tonygnk.cataloglens.releases.MarkdownToHtml
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownToHtmlTest {

    @Test
    fun `renders underscore emphasis`() {
        // OkHttp dates every release this way.
        val html = MarkdownToHtml.toHtml("_2026-08-16_")
        assertTrue(html, html.contains("<i>2026-08-16</i>"))
    }

    @Test
    fun `leaves underscores inside identifiers and urls alone`() {
        val html = MarkdownToHtml.toHtml("Use `snake_case_name` and see https://example.com/_docs_/api")
        assertTrue(html, !html.contains("<i>"))
        assertTrue(html, html.contains("https://example.com/_docs_/api"))
    }

    @Test
    fun `renders a link inside emphasis-heavy text without corrupting the href`() {
        val html = MarkdownToHtml.toHtml("_See_ [the docs](https://example.com/a_b_c) now")
        assertTrue(html, html.contains("href=\"https://example.com/a_b_c\""))
        assertTrue(html, html.contains("<i>See</i>"))
    }

    @Test
    fun `falls back to a notice for empty notes`() {
        assertTrue(MarkdownToHtml.toHtml(null).contains("No release notes"))
        assertTrue(MarkdownToHtml.toHtml("   ").contains("No release notes"))
    }
}
