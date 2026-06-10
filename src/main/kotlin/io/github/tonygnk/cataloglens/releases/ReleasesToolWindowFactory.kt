package io.github.tonygnk.cataloglens.releases

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory

class ReleasesToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ReleasesPanel(project)
        val service = project.service<GithubReleasesService>()
        service.attachView(panel)
        Disposer.register(toolWindow.disposable) { service.detachView(panel) }

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        project.messageBus.connect(toolWindow.disposable).subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun stateChanged(
                    manager: ToolWindowManager,
                    changeType: ToolWindowManagerListener.ToolWindowManagerEventType,
                ) {
                    if (changeType == ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow) {
                        toolWindow.isAvailable = false
                    }
                }
            }
        )
    }

    override fun shouldBeAvailable(project: Project): Boolean = false
}
