package io.github.tonygnk.cataloglens.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.Align
import com.intellij.openapi.ui.DialogPanel
import io.github.tonygnk.cataloglens.links.ArtifactUrlStyle
import javax.swing.JComponent

class CatalogLensConfigurable : Configurable {

    private val settings get() = CatalogLensGlobalSettings.getInstance().state
    private val mappingsPanel = MappingsTablePanel()
    private var dialogPanel: DialogPanel? = null

    override fun getDisplayName(): String = "CatalogLens"

    override fun createComponent(): JComponent {
        val panel = panel {
            row {
                checkBox("Use bundled artifact map")
                    .bindSelected({ settings.useBundledMap }, { settings.useBundledMap = it })
                    .comment("Built-in mappings from popular artifacts to their release notes and changelogs")
            }
            row("Artifact link target:") {
                comboBox(ArtifactUrlStyle.entries)
                    .bindItem(
                        { settings.artifactUrlStyle },
                        { settings.artifactUrlStyle = it ?: ArtifactUrlStyle.MAVEN_CENTRAL },
                    )
                    .comment("Site opened when clicking a [libraries] entry")
            }
            row {
                checkBox("Show resolved-version inlay hints")
                    .bindSelected({ settings.resolvedInlaysEnabled }, { settings.resolvedInlaysEnabled = it })
                    .comment("End-of-line hints showing the resolved version when it differs from the declared one (requires a captured baseline)")
            }
            group("Global Mappings") {
                row {
                    cell(mappingsPanel.component).align(Align.FILL)
                }.resizableRow()
                row {
                    comment("Override or extend bundled upstream links. Project-level mappings take precedence.")
                }
            }
        }
        dialogPanel = panel
        return panel
    }

    override fun isModified(): Boolean =
        dialogPanel?.isModified() == true || mappingsPanel.current() != settings.mappings

    override fun apply() {
        dialogPanel?.apply()
        mappingsPanel.commitEdits()
        settings.mappings = mappingsPanel.current().toMutableMap()
    }

    override fun reset() {
        dialogPanel?.reset()
        mappingsPanel.reset(settings.mappings)
    }

    override fun disposeUIResources() {
        dialogPanel = null
    }
}
