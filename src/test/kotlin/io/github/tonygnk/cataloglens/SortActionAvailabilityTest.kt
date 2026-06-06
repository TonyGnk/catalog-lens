package io.github.tonygnk.cataloglens

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.tonygnk.cataloglens.sort.SortCatalogAction

class SortActionAvailabilityTest : BasePlatformTestCase() {

    private fun updatedEvent(): com.intellij.openapi.actionSystem.AnActionEvent {
        val event = TestActionEvent.createTestEvent {
            when (it) {
                CommonDataKeys.PSI_FILE.name -> myFixture.file
                CommonDataKeys.EDITOR.name -> myFixture.editor
                CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }
        SortCatalogAction().update(event)
        return event
    }

    fun testEnabledOnVersionCatalog() {
        myFixture.configureByText("libs.versions.toml", "[versions]\na = \"1\"\n")
        assertTrue(updatedEvent().presentation.isEnabledAndVisible)
    }

    fun testDisabledOnPlainToml() {
        myFixture.configureByText("config.toml", "a = \"1\"\n")
        assertFalse(updatedEvent().presentation.isEnabledAndVisible)
    }
}
