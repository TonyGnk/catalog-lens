package io.github.tonygnk.cataloglens

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.tonygnk.cataloglens.sort.CatalogGroupSorter
import io.github.tonygnk.cataloglens.sort.SortCatalogAction

class SortApplyTest : BasePlatformTestCase() {

    fun testAppliesSortedResult() {
        myFixture.configureByText("libs.versions.toml", "[versions]\nb = \"1\"\na = \"2\"\n")
        val document = myFixture.editor.document
        val current = document.text
        val result = CatalogGroupSorter.sort(current)
        assertTrue(SortCatalogAction.applyResult(project, document, myFixture.file, current, result))
        assertEquals("[versions]\na = \"2\"\nb = \"1\"\n", document.text)
    }

    fun testStaleDocumentAborts() {
        myFixture.configureByText("libs.versions.toml", "[versions]\nb = \"1\"\na = \"2\"\n")
        val document = myFixture.editor.document
        val stale = "[versions]\nb = \"1\"\n"
        val applied = SortCatalogAction.applyResult(
            project, document, myFixture.file, stale, CatalogGroupSorter.sort(stale)
        )
        assertFalse(applied)
        assertEquals("[versions]\nb = \"1\"\na = \"2\"\n", document.text)
    }

    fun testMinimalReplaceKeepsCaretBeforeChange() {
        myFixture.configureByText("libs.versions.toml", "[versions]\nb = \"1\"\na = \"2\"\n")
        val document = myFixture.editor.document
        myFixture.editor.caretModel.moveToOffset(5)
        val current = document.text
        SortCatalogAction.applyResult(project, document, myFixture.file, current, CatalogGroupSorter.sort(current))
        assertEquals(5, myFixture.editor.caretModel.offset)
    }
}
