package io.github.tonygnk.cataloglens.releases

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import io.github.tonygnk.cataloglens.psi.keyText
import io.github.tonygnk.cataloglens.psi.stringValue
import io.github.tonygnk.cataloglens.psi.topLevelTable
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlLiteral

/**
 * Rewrites a single [versions] entry's value in libs.versions.toml. The PSI is re-resolved inside
 * the write command (the marker's elements are long gone by the time the user clicks), and the
 * existing quote character is preserved. Returns false if the entry vanished or is no longer a
 * plain string literal.
 */
object VersionCatalogWriter {

    fun bump(project: Project, ctx: VersionEditContext, newVersion: String): Boolean {
        if (!ctx.file.isValid) return false
        var applied = false
        WriteCommandAction.runWriteCommandAction(project, "Update ${ctx.refName} Version", null, {
            val file = PsiManager.getInstance(project).findFile(ctx.file) as? TomlFile
                ?: return@runWriteCommandAction
            val entry = topLevelTable(file, "versions")?.entries
                ?.firstOrNull { keyText(it.key) == ctx.refName }
                ?: return@runWriteCommandAction
            val literal = entry.value as? TomlLiteral ?: return@runWriteCommandAction
            if (literal.stringValue() == null) return@runWriteCommandAction

            val document = PsiDocumentManager.getInstance(project).getDocument(file)
                ?: return@runWriteCommandAction
            val raw = literal.text
            val quote = raw.firstOrNull()?.takeIf { it == '"' || it == '\'' } ?: '"'
            document.replaceString(
                literal.textRange.startOffset,
                literal.textRange.endOffset,
                "$quote$newVersion$quote",
            )
            applied = true
        })
        if (!applied) thisLogger().warn("Could not rewrite version for ref '${ctx.refName}'")
        return applied
    }
}
