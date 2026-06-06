package io.github.tonygnk.cataloglens.sort

object CatalogGroupSorter {

    data class GroupInfo(val index: Int, val label: String, val lineRange: IntRange)

    class Analysis internal constructor(
        private val segments: List<Segment>,
        private val hadTrailingNewline: Boolean,
    ) {
        val changedGroups: List<GroupInfo> = segments
            .filterIsInstance<Segment.Group>()
            .filter { it.changed }
            .map { GroupInfo(it.index, it.label, it.startLine + 1..it.endLine + 1) }

        fun compose(selectedIndices: Set<Int>): String {
            val out = ArrayList<String>()
            for (segment in segments) {
                when (segment) {
                    is Segment.Delimiter -> out.add(segment.line)
                    is Segment.Group -> {
                        val entries =
                            if (segment.index in selectedIndices) segment.sorted else segment.entries
                        entries.forEach { out.addAll(it.lines) }
                    }
                }
            }
            val result = out.joinToString("\n")
            return if (hadTrailingNewline) "$result\n" else result
        }

        fun sortAll(): String = compose(
            segments.filterIsInstance<Segment.Group>().map { it.index }.toSet()
        )
    }

    fun sort(text: String): String = analyze(text).sortAll()

    fun analyze(text: String): Analysis {
        if (text.isEmpty()) return Analysis(emptyList(), false)
        val hadTrailingNewline = text.endsWith("\n")
        val lines = text.removeSuffix("\n").split("\n")
        val segments = mutableListOf<Segment>()

        val entries = mutableListOf<Entry>()
        var groupStart = 0
        var groupLabel: String? = null
        var groupIndex = 0
        var context: String? = null
        var open: Entry? = null
        var openDelta = 0
        var malformed = false

        fun flushGroup(endLine: Int) {
            if (entries.isEmpty()) return
            val sortable = !malformed && open == null
            segments.add(
                Segment.Group(
                    index = groupIndex++,
                    entries = entries.toList(),
                    sortable = sortable,
                    startLine = groupStart,
                    endLine = endLine,
                    label = groupLabel ?: entries.first().key,
                )
            )
            entries.clear()
            open = null
            openDelta = 0
            malformed = false
            groupLabel = null
        }

        for ((lineNo, line) in lines.withIndex()) {
            val current = open
            if (current != null) {
                current.lines.add(line)
                val delta = bracketDelta(line)
                if (delta == null) malformed = true else openDelta += delta
                if (malformed || openDelta <= 0) {
                    if (openDelta < 0) malformed = true
                    open = null
                    openDelta = 0
                }
                continue
            }

            val trimmed = line.trimStart()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) {
                flushGroup(lineNo - 1)
                if (trimmed.isNotEmpty()) context = trimmed
                segments.add(Segment.Delimiter(line))
                continue
            }

            if (entries.isEmpty()) {
                groupStart = lineNo
                groupLabel = context
            }
            val entry = Entry(keyOf(line), mutableListOf(line))
            entries.add(entry)
            when (val delta = bracketDelta(line)) {
                null -> malformed = true
                else -> when {
                    delta > 0 -> {
                        open = entry
                        openDelta = delta
                    }
                    delta < 0 -> malformed = true
                }
            }
        }
        flushGroup(lines.lastIndex)

        return Analysis(segments, hadTrailingNewline)
    }

    internal sealed interface Segment {
        class Delimiter(val line: String) : Segment

        class Group(
            val index: Int,
            val entries: List<Entry>,
            sortable: Boolean,
            val startLine: Int,
            val endLine: Int,
            val label: String,
        ) : Segment {
            val sorted: List<Entry> =
                if (sortable) {
                    entries.sortedWith(
                        compareBy(String.CASE_INSENSITIVE_ORDER, Entry::key).thenBy(Entry::key)
                    )
                } else {
                    entries
                }
            val changed: Boolean = sorted != entries
        }
    }

    internal class Entry(val key: String, val lines: MutableList<String>)

    private fun keyOf(line: String): String {
        val eq = line.indexOf('=')
        val raw = (if (eq >= 0) line.substring(0, eq) else line).trim()
        return raw.removeSurrounding("\"").removeSurrounding("'")
    }

    private fun bracketDelta(line: String): Int? {
        var delta = 0
        var i = 0
        val n = line.length
        while (i < n) {
            when (line[i]) {
                '#' -> return delta
                '[', '{' -> {
                    delta++
                    i++
                }
                ']', '}' -> {
                    delta--
                    i++
                }
                '"' -> {
                    i++
                    while (i < n && line[i] != '"') {
                        if (line[i] == '\\') i++
                        i++
                    }
                    if (i >= n) return null
                    i++
                }
                '\'' -> {
                    i++
                    while (i < n && line[i] != '\'') i++
                    if (i >= n) return null
                    i++
                }
                else -> i++
            }
        }
        return delta
    }
}
