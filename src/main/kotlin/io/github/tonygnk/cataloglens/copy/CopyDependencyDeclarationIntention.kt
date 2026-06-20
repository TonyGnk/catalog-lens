package io.github.tonygnk.cataloglens.copy

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.github.tonygnk.cataloglens.CatalogLensBundle
import io.github.tonygnk.cataloglens.psi.VersionCatalogDetector
import io.github.tonygnk.cataloglens.resolved.CatalogEntrySupport
import java.awt.datatransfer.StringSelection

/** Alt+Enter on a catalog entry: copies the Gradle dependency declaration to the clipboard. */
class CopyDependencyDeclarationIntention : IntentionAction {

    @Volatile
    private var cached: String? = null

    override fun getFamilyName(): String = CatalogLensBundle.message("intention.copy.declaration.family")

    override fun getText(): String =
        cached?.let { "Copy $it" } ?: familyName

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || !VersionCatalogDetector.isVersionCatalog(file)) return false
        val declaration = declarationAt(editor, file!!)
        cached = declaration
        return declaration != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val declaration = declarationAt(editor, file) ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(declaration))
        HintManager.getInstance().showInformationHint(editor, "Copied $declaration")
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    private fun declarationAt(editor: Editor, file: PsiFile): String? {
        val entry = CatalogEntrySupport.entryAt(file, editor.caretModel.offset) ?: return null
        return CatalogAccessor.declarationForEntry(file, entry)
    }
}
