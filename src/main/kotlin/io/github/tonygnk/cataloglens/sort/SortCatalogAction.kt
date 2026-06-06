package io.github.tonygnk.cataloglens.sort

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
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
        val analysis = CatalogGroupSorter.analyze(current)
        if (analysis.changedGroups.isEmpty()) {
            HintManager.getInstance().showInformationHint(editor, "Catalog groups already sorted")
            return
        }

        val dialog = SortPreviewDialog(project, psiFile.name, psiFile.fileType, current, analysis)
        if (!dialog.showAndGet()) return
        val result = dialog.resultText
        if (result == current) return
        if (!applyResult(project, document, psiFile, current, result)) {
            HintManager.getInstance().showInformationHint(editor, "Document changed, sort cancelled")
        }
    }

    companion object {
        internal fun applyResult(
            project: Project,
            document: Document,
            psiFile: PsiFile,
            expectedBefore: String,
            result: String,
        ): Boolean {
            var applied = false
            WriteCommandAction.runWriteCommandAction(project, "Sort Catalog Groups", null, {
                val old = document.text
                if (old != expectedBefore) return@runWriteCommandAction
                val limit = minOf(old.length, result.length)
                var prefix = 0
                while (prefix < limit && old[prefix] == result[prefix]) prefix++
                var suffix = 0
                while (suffix < limit - prefix &&
                    old[old.length - 1 - suffix] == result[result.length - 1 - suffix]
                ) suffix++
                document.replaceString(
                    prefix,
                    old.length - suffix,
                    result.substring(prefix, result.length - suffix),
                )
                applied = true
            }, psiFile)
            return applied
        }
    }
}
