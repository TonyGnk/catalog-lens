package io.github.tonygnk.cataloglens.releases

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Converts a developer.android.com (Google devsite) release-notes page into per-version sections.
 *
 * The whole trick is scoping to `div.devsite-article-body` before converting — without it the
 * left-nav listing every androidx.* library swamps the output. The body is then split at each
 * "Version X" heading so the panel can render one card per version (with a "use this version"
 * control). Returns an empty list when the article body is missing or empty (a non-article page or
 * a devsite DOM rework) so callers fall back to opening the URL in the system browser.
 */
object DevsiteChangelogConverter {

    private val DROP_TAGS = setOf("devsite-feedback", "script", "style", "nav", "devsite-toc")
    private val DROP_CLASSES = setOf("devsite-floating-action-buttons", "devsite-heading-link")

    // A heading whose text starts with "Version" marks the start of a changelog entry. Everything
    // before the first such heading (declaring dependencies, compiler-options preamble) is dropped.
    private val VERSION_HEADING = Regex("(?i)^version\\b")

    private class Builder(val version: String?, val header: String, val level: Int) {
        val body = StringBuilder()
    }

    fun toSections(html: String, baseUri: String): List<ChangelogSection> {
        val doc = Jsoup.parse(html, baseUri)
        val body = doc.selectFirst("div.devsite-article-body") ?: return emptyList()

        val all = mutableListOf(Builder(version = null, header = "", level = 0))
        appendBlocks(body, all)

        // When the page has versioned sections, drop the leading preamble (matches prior behaviour
        // of starting at the first "Version" heading). Pages without any (e.g. AGP) keep everything.
        val firstVersioned = all.indexOfFirst { it.version != null }
        val builders = if (firstVersioned > 0) all.drop(firstVersioned) else all

        val sections = builders.mapIndexedNotNull { i, builder ->
            val markdown = builder.body.toString().normalize()
            val seriesHeading = isSeriesHeading(builders, i)
            when {
                // A series heading is not a release: it carries no notes of its own, so there is
                // nothing to show and nothing to pin. Drop the card entirely.
                seriesHeading && markdown.isBlank() -> null
                builder.header.isBlank() && markdown.isBlank() -> null
                // A series heading that does carry text (a compatibility note, a "changes since"
                // summary) stays as a divider, but its version is still not pinnable.
                else -> ChangelogSection(builder.version.takeUnless { seriesHeading }, builder.header, markdown)
            }
        }
        // A real changelog has at least a version section or a heading; a bare paragraph body is a
        // non-article page — return empty so the caller falls back to the browser.
        if (sections.none { it.version != null || it.markdown.contains('#') }) return emptyList()
        return sections
    }

    /**
     * True when the "Version X" heading at [index] merely groups the releases nested under it
     * instead of naming one. devsite wraps every release in such a heading: "Version 1.3" over
     * "Version 1.3.0-alpha10", or a bare "Version 1.11.0" over the 1.11.0 entry itself. Neither
     * "1.3" nor the repeated "1.11.0" is worth offering — the deeper heading already is.
     *
     * The whole nested block is scanned, not just the first entry, because a group is ordered
     * newest-first and so may open with a later patch ("Version 1.7.0" over "Version 1.7.1", then
     * "Version 1.7.0"). A block that only ever appends pre-release qualifiers ("Version 1.0.0" over
     * "Version 1.0.0-rc01") is the opposite case: there the outer heading *is* the stable release,
     * carrying its own notes, and nothing below repeats it.
     */
    private fun isSeriesHeading(builders: List<Builder>, index: Int): Boolean {
        val heading = builders[index]
        val version = heading.version ?: return false
        for (i in index + 1 until builders.size) {
            val nested = builders[i]
            if (nested.level <= heading.level) return false
            val nestedVersion = nested.version ?: continue
            if (nestedVersion == version || nestedVersion.numericCore().startsWith("$version.")) return true
        }
        return false
    }

    /** The leading dotted-number part of a version, without any `-rc01` / `+meta` qualifier. */
    private fun String.numericCore(): String = takeWhile { it.isDigit() || it == '.' }

    private fun appendBlocks(container: Element, builders: MutableList<Builder>) {
        for (el in container.children()) {
            if (isDropped(el)) continue
            when (el.tagName()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val text = inline(el)
                    if (VERSION_HEADING.containsMatchIn(text)) {
                        val level = el.tagName().substring(1).toInt()
                        builders.add(Builder(VersionMatcher.extractPinnable(text), text, level))
                    } else {
                        val hashes = when (el.tagName()) {
                            "h1", "h2" -> "#"
                            "h3" -> "##"
                            else -> "###"
                        }
                        builders.last().body.appendLine("$hashes $text").appendLine()
                    }
                }
                "p" -> inline(el).takeIf { it.isNotBlank() }
                    ?.let { builders.last().body.appendLine(it).appendLine() }
                "ul", "ol" -> {
                    val sb = builders.last().body
                    el.children().filter { it.tagName() == "li" }.forEach { li ->
                        sb.appendLine("- ${inline(li)}")
                    }
                    sb.appendLine()
                }
                "pre" -> builders.last().body
                    .appendLine("```").appendLine(el.wholeText().trim()).appendLine("```").appendLine()
                "section", "div", "article" -> appendBlocks(el, builders)
            }
        }
    }

    private fun String.normalize(): String = replace(Regex("\n{3,}"), "\n\n").trim()

    private fun isDropped(el: Element): Boolean =
        el.tagName() in DROP_TAGS || el.classNames().any { it in DROP_CLASSES }

    private fun inline(el: Element): String {
        val out = StringBuilder()
        for (node in el.childNodes()) appendInline(node, out)
        return out.toString().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun appendInline(node: Node, out: StringBuilder) {
        when (node) {
            is TextNode -> out.append(node.text())
            is Element -> {
                if (isDropped(node)) return
                when (node.tagName()) {
                    "code" -> out.append("`").append(node.text().trim()).append("`")
                    "a" -> {
                        val text = node.text().trim()
                        if (text.isEmpty()) return
                        val href = node.absUrl("href")
                        if (href.isNotEmpty()) {
                            out.append("[").append(text).append("](").append(href).append(")")
                        } else {
                            out.append(text)
                        }
                    }
                    "strong", "b" -> out.append("**").append(node.text().trim()).append("**")
                    "em", "i" -> out.append("*").append(node.text().trim()).append("*")
                    "br" -> out.append(" ")
                    else -> node.childNodes().forEach { appendInline(it, out) }
                }
            }
        }
    }
}
