package io.github.tonygnk.cataloglens.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class CatalogLensProjectConfigurable(private val project: Project) : Configurable {

    private val settings get() = CatalogLensProjectSettings.getInstance(project).state
    private val mappingsPanel = MappingsTablePanel()

    override fun getDisplayName(): String = "Project Mappings"

    override fun createComponent(): JComponent = panel {
        group("Project Mappings") {
            row {
                cell(mappingsPanel.component).align(Align.FILL)
            }.resizableRow()
            row {
                comment(
                    "Project-scoped upstream link overrides, stored in .idea/cataloglens.xml " +
                        "and shareable with your team. These shadow global and bundled mappings.",
                )
            }
        }
    }

    override fun isModified(): Boolean = mappingsPanel.current() != settings.mappings

    override fun apply() {
        mappingsPanel.commitEdits()
        settings.mappings = mappingsPanel.current().toMutableMap()
    }

    override fun reset() {
        mappingsPanel.reset(settings.mappings)
    }
}
