package io.github.tonygnk.cataloglens

import io.github.tonygnk.cataloglens.sort.CatalogGroupSorter
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogGroupSorterTest {

    @Test
    fun sortsSingleGroup() {
        val input = """
            retrofit = "2.11.0"
            moshi = "1.15.1"
            okhttp = "4.12.0"
        """.trimIndent()
        val expected = """
            moshi = "1.15.1"
            okhttp = "4.12.0"
            retrofit = "2.11.0"
        """.trimIndent()
        assertEquals(expected, CatalogGroupSorter.sort(input))
    }

    @Test
    fun blankLineSeparatesGroups() {
        val input = """
            b = "1"
            a = "2"

            d = "3"
            c = "4"
        """.trimIndent()
        val expected = """
            a = "2"
            b = "1"

            c = "4"
            d = "3"
        """.trimIndent()
        assertEquals(expected, CatalogGroupSorter.sort(input))
    }

    @Test
    fun commentSeparatesGroupsAndStaysInPlace() {
        val input = """
            b = "1"
            a = "2"
            # networking
            d = "3"
            c = "4"
        """.trimIndent()
        val expected = """
            a = "2"
            b = "1"
            # networking
            c = "4"
            d = "3"
        """.trimIndent()
        assertEquals(expected, CatalogGroupSorter.sort(input))
    }

    @Test
    fun tableHeaderActsAsDelimiter() {
        val input = """
            [versions]
            b = "1"
            a = "2"
            [libraries]
            d = { module = "x:d", version.ref = "a" }
            c = { module = "x:c", version.ref = "b" }
        """.trimIndent()
        val expected = """
            [versions]
            a = "2"
            b = "1"
            [libraries]
            c = { module = "x:c", version.ref = "b" }
            d = { module = "x:d", version.ref = "a" }
        """.trimIndent()
        assertEquals(expected, CatalogGroupSorter.sort(input))
    }

    @Test
    fun multilineArrayMovesAsUnit() {
        val input = """
            [bundles]
            network = [
                "okhttp",
                "retrofit",
            ]
            compose = ["ui", "material"]
        """.trimIndent()
        val expected = """
            [bundles]
            compose = ["ui", "material"]
            network = [
                "okhttp",
                "retrofit",
            ]
        """.trimIndent()
        assertEquals(expected, CatalogGroupSorter.sort(input))
    }

    @Test
    fun bracketsAndHashInsideStringsIgnored()  {
        val input = """
            b = "value [ with { brackets"
            a = "value # not a comment"
        """.trimIndent()
        val expected = """
            a = "value # not a comment"
            b = "value [ with { brackets"
        """.trimIndent()
        assertEquals(expected, CatalogGroupSorter.sort(input))
    }

    @Test
    fun trailingCommentOnEntryLineIgnoredForBrackets() {
        val input = """
            b = "1" # opens [
            a = "2"
        """.trimIndent()
        val expected = """
            a = "2"
            b = "1" # opens [
        """.trimIndent()
        assertEquals(expected, CatalogGroupSorter.sort(input))
    }

    @Test
    fun unbalancedGroupLeftUntouched() {
        val input = """
            b = [ "never closed"
            a = "2"
        """.trimIndent()
        assertEquals(input, CatalogGroupSorter.sort(input))
    }

    @Test
    fun caseInsensitiveSort() {
        val input = """
            Zebra = "1"
            apple = "2"
            Mango = "3"
        """.trimIndent()
        val expected = """
            apple = "2"
            Mango = "3"
            Zebra = "1"
        """.trimIndent()
        assertEquals(expected, CatalogGroupSorter.sort(input))
    }

    @Test
    fun alreadySortedIsIdentity() {
        val input = """
            [versions]
            a = "1"
            b = "2"

            # group
            c = "3"
            d = "4"
        """.trimIndent() + "\n"
        assertEquals(input, CatalogGroupSorter.sort(input))
    }

    @Test
    fun trailingNewlinePreserved() {
        assertEquals("a = \"1\"\nb = \"2\"\n", CatalogGroupSorter.sort("b = \"2\"\na = \"1\"\n"))
        assertEquals("a = \"1\"\nb = \"2\"", CatalogGroupSorter.sort("b = \"2\"\na = \"1\""))
    }

    @Test
    fun emptyAndBlankInputUnchanged() {
        assertEquals("", CatalogGroupSorter.sort(""))
        assertEquals("\n\n", CatalogGroupSorter.sort("\n\n"))
    }

    @Test
    fun stableForEqualKeys() {
        val input = """
            a = "first"
            a = "second"
        """.trimIndent()
        assertEquals(input, CatalogGroupSorter.sort(input))
    }

    @Test
    fun analyzeReportsOnlyChangedGroups() {
        val input = """
            [versions]
            b = "1"
            a = "2"

            # sorted already
            c = "3"
            d = "4"
        """.trimIndent()
        val groups = CatalogGroupSorter.analyze(input).changedGroups
        assertEquals(1, groups.size)
        assertEquals("[versions]", groups[0].label)
        assertEquals(2..3, groups[0].lineRange)
    }

    @Test
    fun analyzeLabelFallsBackToFirstKey() {
        val input = """
            b = "1"
            a = "2"
        """.trimIndent()
        val groups = CatalogGroupSorter.analyze(input).changedGroups
        assertEquals("b", groups[0].label)
    }

    @Test
    fun composeSortsOnlySelectedGroups() {
        val input = """
            b = "1"
            a = "2"

            d = "3"
            c = "4"
        """.trimIndent()
        val analysis = CatalogGroupSorter.analyze(input)
        val groups = analysis.changedGroups
        assertEquals(2, groups.size)
        val expected = """
            a = "2"
            b = "1"

            d = "3"
            c = "4"
        """.trimIndent()
        assertEquals(expected, analysis.compose(setOf(groups[0].index)))
    }

    @Test
    fun composeEmptySelectionIsIdentity() {
        val input = """
            b = "1"
            a = "2"
        """.trimIndent()
        assertEquals(input, CatalogGroupSorter.analyze(input).compose(emptySet()))
    }

    @Test
    fun quotedKeysSortWithoutQuotes() {
        val input = """
            "zebra" = "1"
            apple = "2"
        """.trimIndent()
        val expected = """
            apple = "2"
            "zebra" = "1"
        """.trimIndent()
        assertEquals(expected, CatalogGroupSorter.sort(input))
    }

    @Test
    fun sortIsIdempotent() {
        val input = """
            [versions]
            b = "1"
            a = "2"
            # comment
            Zebra = "3"
            apple = "4"

            [bundles]
            network = [
                "okhttp",
            ]
            compose = ["ui"]
        """.trimIndent()
        val once = CatalogGroupSorter.sort(input)
        assertEquals(once, CatalogGroupSorter.sort(once))
    }

    @Test
    fun commentInsideOpenArrayGluedToEntry() {
        val input = """
            b = [
                # inner comment
                "x",
            ]
            a = ["y"]
        """.trimIndent()
        val expected = """
            a = ["y"]
            b = [
                # inner comment
                "x",
            ]
        """.trimIndent()
        assertEquals(expected, CatalogGroupSorter.sort(input))
    }

    @Test
    fun blankLineInsideOpenArrayGluedToEntry() {
        val input = """
            b = [
                "x",

                "z",
            ]
            a = ["y"]
        """.trimIndent()
        val expected = """
            a = ["y"]
            b = [
                "x",

                "z",
            ]
        """.trimIndent()
        assertEquals(expected, CatalogGroupSorter.sort(input))
    }

    @Test
    fun lineWithoutEqualsSortsByWholeLine() {
        val input = "b\na"
        assertEquals("a\nb", CatalogGroupSorter.sort(input))
    }

    @Test
    fun extraClosingBracketLeavesGroupUntouched() {
        val input = """
            b = "1"]
            a = "2"
        """.trimIndent()
        assertEquals(input, CatalogGroupSorter.sort(input))
    }
}
