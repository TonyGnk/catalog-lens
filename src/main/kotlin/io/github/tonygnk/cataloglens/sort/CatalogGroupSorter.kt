package io.github.tonygnk.cataloglens.sort

object CatalogGroupSorter {

    private class Entry(val key: String, val lines: MutableList<String>)

    fun sort(text: String): String {
        if (text.isEmpty()) return text
        val hadTrailingNewline = text.endsWith("\n")
        val lines = text.removeSuffix("\n").split("\n")
        val out = ArrayList<String>(lines.size)

        val group = mutableListOf<Entry>()
        var open: Entry? = null
        var openDelta = 0
        var malformed = false

        fun flushGroup() {
            val flushed = if (malformed || open != null) {
                group.toList()
            } else {
                group.sortedWith(
                    compareBy(String.CASE_INSENSITIVE_ORDER, Entry::key).thenBy(Entry::key)
                )
            }
            flushed.forEach { out.addAll(it.lines) }
            group.clear()
            open = null
            openDelta = 0
            malformed = false
        }

        for (line in lines) {
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
                flushGroup()
                out.add(line)
                continue
            }

            val entry = Entry(keyOf(line), mutableListOf(line))
            group.add(entry)
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
        flushGroup()

        val result = out.joinToString("\n")
        return if (hadTrailingNewline) "$result\n" else result
    }

    private fun keyOf(line: String): String {
        val eq = line.indexOf('=')
        return (if (eq >= 0) line.substring(0, eq) else line).trim()
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
