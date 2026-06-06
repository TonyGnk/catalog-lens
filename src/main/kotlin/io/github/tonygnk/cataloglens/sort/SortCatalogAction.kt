package io.github.tonygnk.cataloglens.sort

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware
import io.github.tonygnk.cataloglens.psi.VersionCatalogDetector

class SortCatalogAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            VersionCatalogDetector.isVersionCatalog(e.getData(CommonDataKeys.PSI_FILE))
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val document = editor.document

        val current = document.text
        val sorted = CatalogGroupSorter.sort(current)
        if (sorted == current) {
            HintManager.getInstance().showInformationHint(editor, "Catalog groups already sorted")
            return
        }

        val dialog = SortPreviewDialog(project, psiFile.name, psiFile.fileType, current, sorted)
        if (dialog.showAndGet()) {
            WriteCommandAction.runWriteCommandAction(project, "Sort Catalog Groups", null, {
                document.setText(sorted)
            }, psiFile)
        }
    }
}
