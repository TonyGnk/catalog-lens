package io.github.tonygnk.cataloglens.resolved

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import io.github.tonygnk.cataloglens.resolved.model.DependencyInsightModel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import javax.swing.JComponent
import javax.swing.SwingUtilities

class InsightPanel(private val project: Project) : JBPanel<InsightPanel>(BorderLayout()), InsightView {

    private val header = JBLabel().apply {
        font = JBFont.label().asBold()
        border = JBUI.Borders.empty(8, 10)
    }
    private val content = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(4)))
    private val scrollPane = JBScrollPane(content)

    init {
        add(header, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        renderEmpty()
    }

    override fun render(state: InsightState) {
        content.removeAll()
        when (state) {
            InsightState.NoProject -> {
                header.text = "Resolved dependencies"
                add(line("Open a synced Gradle project to resolve dependencies."))
            }
            is InsightState.Resolving -> {
                header.text = "Resolving ${state.request.coordinate}…"
                add(line("Resolving via ${scope(state.request)} — this may take a moment."))
                add(actionLink("Cancel") { project.service<DependencyInsightService>().cancel() })
            }
            is InsightState.Failed -> {
                header.text = state.request.coordinate
                add(sectionTitle("Resolution failed"))
                add(line(state.message))
            }
            is InsightState.Ready -> renderReady(state)
        }
        content.revalidate()
        content.repaint()
        // Each render is a fresh state (new resolution / scope), so pin the viewport to the top — without
        // this the scroll position carries over from the previous, taller content and lands mid-panel.
        SwingUtilities.invokeLater { scrollPane.viewport.viewPosition = Point(0, 0) }
    }

    private fun renderReady(state: InsightState.Ready) {
        val model = state.model
        val node = model.resolved
        val usedScope = "${model.projectPath ?: state.request.targetProjectPath.ifBlank { ":" }}/${model.configurationName}"
        header.text = if (node?.selectedVersion != null) {
            "${node.group}:${node.name}:${node.selectedVersion}"
        } else {
            state.request.coordinate
        }

        add(line("Scope: $usedScope"))

        model.errorMessage?.let {
            add(sectionTitle("Could not resolve"))
            add(line(it))
        }

        if (node != null) {
            if (node.selectionReasons.isNotEmpty()) {
                add(sectionTitle("Selection reasons"))
                node.selectionReasons.forEach { add(line("• [${it.cause}] ${it.description}")) }
            }
            if (node.requestedBy.isNotEmpty()) {
                add(sectionTitle("Requested by"))
                node.requestedBy.forEach { edge ->
                    val version = edge.requestedVersion?.let { " → $it" } ?: ""
                    val constraint = if (edge.isConstraint) " (constraint)" else ""
                    add(line("• ${edge.fromDisplayName}$version$constraint"))
                }
            }
        } else if (model.errorMessage == null) {
            add(line("${state.request.coordinate} is not present in this configuration's graph."))
        }

        if (model.unresolved.isNotEmpty()) {
            add(sectionTitle("Unresolved"))
            model.unresolved.forEach { add(line("• ${it.requested}${it.failureMessage?.let { m -> " — $m" } ?: ""}")) }
        }

        renderRetargets(model, state.request)
    }

    /** The cheap, no-resolution config enumeration as one-click scopes — the Stage-1 stand-in for a scope selector. */
    private fun renderRetargets(model: DependencyInsightModel, request: InsightRequest) {
        if (model.availableConfigurations.isEmpty()) return
        add(sectionTitle("Resolve in another scope"))
        model.availableConfigurations.forEach { qualified ->
            val configName = qualified.substringAfterLast(':')
            val projectPath = qualified.substringBeforeLast(':')
            add(
                actionLink(qualified) {
                    project.service<DependencyInsightService>().resolve(
                        request.copy(targetProjectPath = projectPath, configurationName = configName),
                    )
                },
            )
        }
    }

    private fun scope(req: InsightRequest): String =
        if (req.configurationName.isBlank()) "auto-detected scope"
        else "${req.targetProjectPath.ifBlank { ":" }}/${req.configurationName}"

    private fun renderEmpty() {
        header.text = "Resolved dependencies"
        content.add(line("Right-click a [libraries] entry → \"Why this version? (resolved)\"."))
    }

    private fun add(component: JComponent) {
        component.alignmentX = Component.LEFT_ALIGNMENT
        content.add(component)
    }

    private fun sectionTitle(text: String) = JBLabel(text).apply {
        font = JBFont.label().asBold()
        border = JBUI.Borders.empty(8, 10, 2, 10)
    }

    private fun line(text: String) = JBLabel(text).apply {
        border = JBUI.Borders.empty(0, 14, 0, 10)
    }

    private fun actionLink(text: String, action: () -> Unit) = ActionLink(text) { action() }.apply {
        border = JBUI.Borders.empty(0, 14, 0, 10)
    }
}
