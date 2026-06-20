package io.github.tonygnk.cataloglens.resolved

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI

class ResolvedToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val insightPanel = InsightPanel(project)
        val insightService = project.service<DependencyInsightService>()
        insightService.attachView(insightPanel)
        Disposer.register(toolWindow.disposable) { insightService.detachView(insightPanel) }

        val deltaPanel = DeltaPanel(project)
        val deltaService = project.service<ResolvedDeltaService>()
        deltaService.attachView(deltaPanel)
        Disposer.register(toolWindow.disposable) { deltaService.detachView(deltaPanel) }

        val factory = ContentFactory.getInstance()
        toolWindow.contentManager.addContent(factory.createContent(insightPanel, INSIGHT_TAB, false))
        toolWindow.contentManager.addContent(factory.createContent(deltaPanel, DELTA_TAB, false))

        narrowToDefault(toolWindow)

        project.messageBus.connect(toolWindow.disposable).subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                // The TOPIC is project-wide: the changed window must be checked, or hiding any other
                // tool window would strip this one's stripe button.
                override fun stateChanged(
                    manager: ToolWindowManager,
                    changedToolWindow: ToolWindow,
                    changeType: ToolWindowManagerListener.ToolWindowManagerEventType,
                ) {
                    if (changeType == ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow &&
                        changedToolWindow.id == TOOL_WINDOW_ID
                    ) {
                        toolWindow.isAvailable = false
                    }
                }
            },
        )
    }

    /**
     * Default the right-anchored window to a narrower width. The platform exposes only relative
     * [ToolWindowEx.stretchWidth], so we shrink toward [DEFAULT_WIDTH] only when the current width is
     * wider — this never widens (safe at width 0 during layout) and respects a narrower saved width.
     */
    private fun narrowToDefault(toolWindow: ToolWindow) {
        if (toolWindow !is ToolWindowEx) return
        ApplicationManager.getApplication().invokeLater {
            val current = toolWindow.component.width
            val target = JBUI.scale(DEFAULT_WIDTH)
            if (current > target) toolWindow.stretchWidth(target - current)
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean = false

    companion object {
        const val TOOL_WINDOW_ID = "CatalogLens Resolved"
        private const val INSIGHT_TAB = "Why this version?"
        private const val DELTA_TAB = "Delta"
        private const val DEFAULT_WIDTH = 550

        fun activateInsight(project: Project, req: InsightRequest) {
            activate(project, INSIGHT_TAB) { project.service<DependencyInsightService>().resolve(req) }
        }

        fun activateDelta(
            project: Project,
            catalogFile: com.intellij.openapi.vfs.VirtualFile,
            catalogText: String,
            currentDeclared: Map<String, String>,
            gradleProjectPath: String,
        ) {
            activate(project, DELTA_TAB) {
                project.service<ResolvedDeltaService>()
                    .computeDelta(catalogFile, catalogText, currentDeclared, gradleProjectPath)
            }
        }

        private fun activate(project: Project, tab: String, action: () -> Unit) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
            toolWindow.isAvailable = true
            toolWindow.activate {
                toolWindow.contentManager.findContent(tab)?.let { toolWindow.contentManager.setSelectedContent(it) }
                action()
            }
        }
    }
}
