package io.github.tonygnk.cataloglens.releases

/**
 * Minimal Markdown -> HTML 3.2 converter for GitHub release-note bodies.
 * Targets Swing's JEditorPane/HTMLEditorKit (no JavaScript, limited CSS), so it emits
 * only basic tags: h2-h4, p, ul/li, pre, code, b, i, a. Not a full CommonMark engine.
 */
object MarkdownToHtml {

    fun toHtml(markdown: String?): String {
        if (markdown.isNullOrBlank()) return "<p><i>No release notes.</i></p>"

        val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val sb = StringBuilder()
        var inList = false
        var i = 0

        fun closeList() {
            if (inList) {
                sb.append("</ul>")
                inList = false
            }
        }

        while (i < lines.size) {
            val line = lines[i].trimEnd()
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                closeList()
                i++
                val code = StringBuilder()
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.append(escape(lines[i])).append("\n")
                    i++
                }
                if (i < lines.size) i++
                sb.append("<pre>").append(code.toString().trimEnd('\n')).append("</pre>")
                continue
            }

            if (trimmed.isEmpty()) {
                closeList()
                i++
                continue
            }

            val heading = HEADING.matchEntire(trimmed)
            if (heading != null) {
                closeList()
                val level = heading.groupValues[1].length
                val tag = "h" + (level + 1).coerceIn(2, 4)
                sb.append("<$tag>").append(inline(heading.groupValues[2])).append("</$tag>")
                i++
                continue
            }

            val item = LIST_ITEM.matchEntire(trimmed)
            if (item != null) {
                if (!inList) {
                    sb.append("<ul>")
                    inList = true
                }
                sb.append("<li>").append(inline(item.groupValues[2])).append("</li>")
                i++
                continue
            }

            closeList()
            sb.append("<p>").append(inline(trimmed)).append("</p>")
            i++
        }
        closeList()
        return sb.toString()
    }

    private fun inline(text: String): String {
        var s = escape(text)
        s = INLINE_CODE.replace(s) { "<code>${it.groupValues[1]}</code>" }
        s = LINK.replace(s) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }
        s = BOLD.replace(s) { "<b>${it.groupValues[1]}</b>" }
        s = ITALIC.replace(s) { "<i>${it.groupValues[1]}</i>" }
        s = BARE_URL.replace(s) { "<a href=\"${it.groupValues[1]}\">${it.groupValues[1]}</a>" }
        return s
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val LIST_ITEM = Regex("^([-*+]|\\d+[.)])\\s+(.*)$")
    private val INLINE_CODE = Regex("`([^`]+)`")
    private val LINK = Regex("\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)")
    private val BOLD = Regex("\\*\\*([^*]+)\\*\\*")
    private val ITALIC = Regex("(?<![*\\w])\\*([^*\\n]+)\\*(?![*\\w])")
    private val BARE_URL = Regex("(?<![\"=>/])\\b(https?://[^\\s<)]+)")
}
