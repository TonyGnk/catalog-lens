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

    private class Builder(val version: String?, val header: String) {
        val body = StringBuilder()
    }

    fun toSections(html: String, baseUri: String): List<ChangelogSection> {
        val doc = Jsoup.parse(html, baseUri)
        val body = doc.selectFirst("div.devsite-article-body") ?: return emptyList()

        val builders = mutableListOf(Builder(version = null, header = ""))
        appendBlocks(body, builders)

        val sections = builders
            .map { ChangelogSection(it.version, it.header, it.body.toString().normalize()) }
            .filter { it.header.isNotBlank() || it.markdown.isNotBlank() }
        // A real changelog has at least a version section or a heading; a bare paragraph body is a
        // non-article page — return empty so the caller falls back to the browser.
        if (sections.none { it.version != null || it.markdown.contains('#') }) return emptyList()

        // When the page has versioned sections, drop the leading preamble (matches prior behaviour
        // of starting at the first "Version" heading). Pages without any (e.g. AGP) keep everything.
        val firstVersioned = sections.indexOfFirst { it.version != null }
        return if (firstVersioned > 0) sections.drop(firstVersioned) else sections
    }

    private fun appendBlocks(container: Element, builders: MutableList<Builder>) {
        for (el in container.children()) {
            if (isDropped(el)) continue
            when (el.tagName()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val text = inline(el)
                    if (VERSION_HEADING.containsMatchIn(text)) {
                        builders.add(Builder(VersionMatcher.extractPinnable(text), text))
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
