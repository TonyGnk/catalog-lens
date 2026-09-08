package io.github.tonygnk.cataloglens

import io.github.tonygnk.cataloglens.releases.MarkdownChangelogConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownChangelogConverterTest {

    @Test
    fun `splits keep-a-changelog headings and strips the bracket syntax`() {
        val markdown = """
            # Changelog

            ## [3.6.2] - September 4, 2026

            - Fix: crash on empty payload.

            ## [3.5.0-beta01] - May 4, 2026

            - New: `AsyncImage` overload.
        """.trimIndent()

        val sections = MarkdownChangelogConverter.toSections(markdown)

        assertEquals(2, sections.size)
        assertEquals("3.6.2", sections[0].version)
        assertEquals("3.6.2 - September 4, 2026", sections[0].header)
        assertTrue(sections[0].markdown.contains("- Fix: crash on empty payload."))
        assertEquals("3.5.0-beta01", sections[1].version)
        assertEquals("3.5.0-beta01 - May 4, 2026", sections[1].header)
    }

    @Test
    fun `splits Version-prefixed headings`() {
        val markdown = """
            Change Log
            ==========

            ## Version 5.5.0

            Some notes.

            ## Version 5.0.0-alpha.17

            Older notes.
        """.trimIndent()

        val sections = MarkdownChangelogConverter.toSections(markdown)

        assertEquals(2, sections.size)
        assertEquals("5.5.0", sections[0].version)
        assertEquals("Version 5.5.0", sections[0].header)
        assertEquals("5.0.0-alpha.17", sections[1].version)
    }

    @Test
    fun `splits setext headings with an emphasised date`() {
        val markdown = """
            Change Log
            ==========

            Version 2.1.5 *(2025-02-14)*
            ----------------------------

            - Backport a fix.

            Version 2.1.4 *(2024-12-18)*
            ----------------------------

            - Another fix.
        """.trimIndent()

        val sections = MarkdownChangelogConverter.toSections(markdown)

        assertEquals(2, sections.size)
        assertEquals("2.1.5", sections[0].version)
        assertEquals("Version 2.1.5 (2025-02-14)", sections[0].header)
        assertTrue(sections[0].markdown.contains("- Backport a fix."))
        assertEquals("2.1.4", sections[1].version)
    }

    @Test
    fun `keeps deeper subsections inside the release body`() {
        val markdown = """
            # Changelog

            ## [18.3.0]
            ### Added
            - Something new.
            ### Fixed
            - Something fixed.

            ## [18.2.3]
            ### Added
            - Older thing.
        """.trimIndent()

        val sections = MarkdownChangelogConverter.toSections(markdown)

        assertEquals(2, sections.size)
        assertEquals("18.3.0", sections[0].version)
        assertTrue(sections[0].markdown.contains("### Added"))
        assertTrue(sections[0].markdown.contains("### Fixed"))
        assertTrue(sections[0].markdown.contains("- Something fixed."))
        assertEquals("18.2.3", sections[1].version)
    }

    @Test
    fun `folds a same-level heading without a version into the release above it`() {
        // facebook-android-sdk writes some subsections at the release level.
        val markdown = """
            # Changelog

            ## [18.3.0]
            ## Added
            - Something new.

            ## [18.2.3]
            - Older thing.
        """.trimIndent()

        val sections = MarkdownChangelogConverter.toSections(markdown)

        assertEquals(2, sections.size)
        assertEquals("18.3.0", sections[0].version)
        assertTrue(sections[0].markdown.contains("## Added"))
        assertTrue(sections[0].markdown.contains("- Something new."))
    }

    @Test
    fun `inlines reference links and drops their definitions`() {
        val markdown = """
            # Change Log

            ## Version 5.5.0

            - Support [Encrypted Client Hello][ech] and [Okio].
            - See [the docs][].

            [ech]: https://example.com/ech
            [okio]: https://example.com/okio
            [the docs]: https://example.com/docs
        """.trimIndent()

        val sections = MarkdownChangelogConverter.toSections(markdown)

        val body = sections.single().markdown
        assertTrue(body.contains("[Encrypted Client Hello](https://example.com/ech)"))
        // Shortcut reference, matched case-insensitively against the definition.
        assertTrue(body.contains("[Okio](https://example.com/okio)"))
        // Collapsed reference: the text doubles as the label.
        assertTrue(body.contains("[the docs](https://example.com/docs)"))
        assertTrue(!body.contains("]: https://"))
    }

    @Test
    fun `leaves undefined bracket text alone`() {
        val markdown = """
            # Changelog

            ## [3.6.2] - September 4, 2026

            - Fixed something in [3.6.1].
        """.trimIndent()

        val sections = MarkdownChangelogConverter.toSections(markdown)

        assertTrue(sections.single().markdown.contains("[3.6.1]"))
    }

    @Test
    fun `keeps an Unreleased preamble but offers no version for it`() {
        val markdown = """
            # Change Log

            ## [Unreleased]

            - Work in progress.

            ## [3.0.0] - 2025-05-15

            - Shipped.
        """.trimIndent()

        val sections = MarkdownChangelogConverter.toSections(markdown)

        assertEquals(2, sections.size)
        assertEquals(null, sections[0].version)
        assertEquals("Change Log", sections[0].header)
        assertTrue(sections[0].markdown.contains("Work in progress"))
        assertEquals("3.0.0", sections[1].version)
    }

    @Test
    fun `drops a title-only preamble`() {
        val markdown = """
            # Changelog

            ## Version 1.2.0

            - Notes.
        """.trimIndent()

        val sections = MarkdownChangelogConverter.toSections(markdown)

        assertEquals(1, sections.size)
        assertEquals("1.2.0", sections.single().version)
    }

    @Test
    fun `ignores headings and definitions inside fenced code`() {
        val markdown = """
            # Changelog

            ## Version 1.2.0

            ```markdown
            ## Version 9.9.9
            [ref]: https://example.com/nope
            ```

            - Real notes.
        """.trimIndent()

        val sections = MarkdownChangelogConverter.toSections(markdown)

        assertEquals(1, sections.size)
        assertEquals("1.2.0", sections.single().version)
        assertTrue(sections.single().markdown.contains("## Version 9.9.9"))
        assertTrue(sections.single().markdown.contains("[ref]: https://example.com/nope"))
    }

    @Test
    fun `returns empty when nothing looks like a version`() {
        val markdown = """
            # Contributing

            Please read the guidelines before opening a pull request.
        """.trimIndent()

        assertTrue(MarkdownChangelogConverter.toSections(markdown).isEmpty())
    }

    @Test
    fun `returns empty for blank input`() {
        assertTrue(MarkdownChangelogConverter.toSections("").isEmpty())
    }
}
