package io.github.tonygnk.cataloglens.releases

/**
 * Converts a Markdown changelog (a repo's `CHANGELOG.md`) into per-version sections, so the panel
 * renders one card per release with the same "current" badge and "use this version" control it
 * gives GitHub Releases and developer.android.com pages.
 *
 * Section bodies stay as Markdown — [MarkdownToHtml] already renders them — with the one fix-up the
 * real files need: reference-style links are inlined and their `[label]: url` definition block is
 * dropped, because okhttp and moshi write most of their links that way and the notes would
 * otherwise read as raw brackets.
 *
 * Which heading level starts a release is decided per document rather than assumed: the level that
 * carries the most version headings wins. That covers `## Version 5.5.0` (okhttp),
 * `## [3.6.2] - September 4, 2026` (coil, moshi, retrofit), a setext `Version 2.1.5 *(2018-03-07)*`
 * over `-----` (picasso, desugar_jdk_libs), and leaves deeper `### Added` subsections inside the
 * body where they belong. Returns an empty list when no version heading is found at all, so callers
 * can fall back to opening the URL in the browser.
 */
object MarkdownChangelogConverter {

    private val ATX = Regex("^ {0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val SETEXT_H1 = Regex("^ {0,3}=+\\s*$")
    private val SETEXT_H2 = Regex("^ {0,3}-{2,}\\s*$")
    private val FENCE = Regex("^ {0,3}(```|~~~)")

    // [label]: https://example.com "optional title"
    private val LINK_DEF = Regex("^ {0,3}\\[([^\\]]+)]:\\s*<?(\\S+?)>?\\s*(?:\"[^\"]*\"|'[^']*'|\\([^)]*\\))?\\s*$")

    private val REF_FULL = Regex("\\[([^\\]]+)]\\[([^\\]]*)]")
    private val REF_SHORTCUT = Regex("(?<!])\\[([^\\]]+)](?![\\[(:])")
    private val INLINE_LINK = Regex("\\[([^\\]]+)]\\((?:[^)\\s]+)\\)")
    private val BRACKETED = Regex("\\[([^\\]]+)]")
    private val EMPHASIS = Regex("[*_]{1,2}([^*_]+)[*_]{1,2}")

    private sealed interface Token {
        data class Heading(val level: Int, val text: String) : Token
        data class Body(val text: String) : Token
    }

    private class Builder(val version: String?, val header: String) {
        val body = StringBuilder()
    }

    fun toSections(markdown: String): List<ChangelogSection> {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val definitions = mutableMapOf<String, String>()
        val tokens = tokenize(lines, definitions).toMutableList()

        val sectionLevel = tokens.filterIsInstance<Token.Heading>()
            .filter { VersionMatcher.extractPinnable(it.text) != null }
            .groupingBy { it.level }
            .eachCount()
            .entries
            // Most version headings wins; on a tie the shallower level is the release level.
            .minWithOrNull(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            ?.key
            ?: return emptyList()

        // A leading "# Changelog" titles the document rather than a release — keep it as the header
        // of whatever precedes the first release (usually an "Unreleased" block, often nothing).
        val title = (tokens.firstOrNull() as? Token.Heading)
            ?.takeIf { it.level < sectionLevel && VersionMatcher.extractPinnable(it.text) == null }
            ?.also { tokens.removeAt(0) }

        val builders = mutableListOf(Builder(version = null, header = title?.let { headerText(it.text) } ?: ""))
        for (token in tokens) {
            when (token) {
                is Token.Heading -> {
                    val version = VersionMatcher.extractPinnable(token.text)
                    if (token.level == sectionLevel && version != null) {
                        builders.add(Builder(version, headerText(token.text)))
                    } else {
                        builders.last().body.appendLine("${"#".repeat(token.level)} ${token.text}")
                    }
                }
                is Token.Body -> builders.last().body.appendLine(token.text)
            }
        }

        return builders.mapNotNull { builder ->
            val body = resolveReferences(builder.body.toString(), definitions).normalize()
            // An empty release still earns a card — it can be pinned. An empty preamble does not.
            if (body.isBlank() && builder.version == null) null
            else ChangelogSection(builder.version, builder.header, body)
        }
    }

    private fun tokenize(lines: List<String>, definitions: MutableMap<String, String>): List<Token> {
        val tokens = mutableListOf<Token>()
        var fenced = false
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (FENCE.containsMatchIn(line)) {
                fenced = !fenced
                tokens.add(Token.Body(line))
                index++
                continue
            }
            if (fenced) {
                tokens.add(Token.Body(line))
                index++
                continue
            }

            val atx = ATX.matchEntire(line)
            if (atx != null) {
                tokens.add(Token.Heading(atx.groupValues[1].length, atx.groupValues[2].trim()))
                index++
                continue
            }

            val definition = LINK_DEF.matchEntire(line)
            if (definition != null) {
                definitions[definition.groupValues[1].lowercase()] = definition.groupValues[2]
                index++
                continue
            }

            // Setext: this line is the heading text, the next one underlines it.
            val underline = lines.getOrNull(index + 1)
            if (line.isNotBlank() && underline != null) {
                val level = when {
                    SETEXT_H1.matches(underline) -> 1
                    SETEXT_H2.matches(underline) -> 2
                    else -> null
                }
                if (level != null) {
                    tokens.add(Token.Heading(level, line.trim()))
                    index += 2
                    continue
                }
            }

            tokens.add(Token.Body(line))
            index++
        }
        return tokens
    }

    /**
     * Rewrites `[text][label]` and shortcut `[label]` into inline `[text](url)` for every label the
     * document defined. Unknown labels are left alone — coil's `[3.6.2]` release headings are plain
     * brackets, not links.
     */
    private fun resolveReferences(text: String, definitions: Map<String, String>): String {
        if (definitions.isEmpty()) return text
        val full = REF_FULL.replace(text) { match ->
            val label = match.groupValues[2].ifBlank { match.groupValues[1] }
            val url = definitions[label.lowercase()] ?: return@replace match.value
            "[${match.groupValues[1]}]($url)"
        }
        return REF_SHORTCUT.replace(full) { match ->
            val url = definitions[match.groupValues[1].lowercase()] ?: return@replace match.value
            "[${match.groupValues[1]}]($url)"
        }
    }

    /** Heading text as the card's plain-text header: no link syntax, brackets or emphasis markers. */
    private fun headerText(raw: String): String = raw
        .replace(INLINE_LINK) { it.groupValues[1] }
        .replace(REF_FULL) { it.groupValues[1] }
        .replace(BRACKETED) { it.groupValues[1] }
        .replace(EMPHASIS) { it.groupValues[1] }
        .replace("`", "")
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ")

    private fun String.normalize(): String = replace(Regex("\n{3,}"), "\n\n").trim()
}
