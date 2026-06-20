package io.github.tonygnk.cataloglens.resolved.inlay

import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.editor.Editor
import io.github.tonygnk.cataloglens.resolved.InsightRequest
import io.github.tonygnk.cataloglens.resolved.ResolvedToolWindowFactory

/** Clicking a resolved-version inlay opens "Why this version?" for that coordinate. */
class ResolvedInlayActionHandler : InlayActionHandler {

    override fun handleClick(editor: Editor, payload: InlayActionPayload) {
        val coordinate = (payload as? StringInlayActionPayload)?.text ?: return
        val project = editor.project ?: return
        val basePath = project.basePath ?: return
        ResolvedToolWindowFactory.activateInsight(
            project,
            InsightRequest(basePath, "", "", coordinate, coordinate),
        )
    }

    companion object {
        const val HANDLER_ID = "cataloglens.resolved.why"
    }
}
