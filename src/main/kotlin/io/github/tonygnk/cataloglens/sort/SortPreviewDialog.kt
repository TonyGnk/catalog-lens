package io.github.tonygnk.cataloglens.sort

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

class SortPreviewDialog(
    project: Project,
    fileName: String,
    fileType: FileType,
    currentText: String,
    sortedText: String,
) : DialogWrapper(project) {

    private val diffPanel = DiffManager.getInstance().createRequestPanel(project, disposable, null)

    init {
        title = "Sort $fileName"
        setOKButtonText("Apply Sort")
        val factory = DiffContentFactory.getInstance()
        diffPanel.setRequest(
            SimpleDiffRequest(
                "Sort $fileName",
                factory.create(project, currentText, fileType),
                factory.create(project, sortedText, fileType),
                "Current",
                "Sorted",
            )
        )
        init()
    }

    override fun createCenterPanel(): JComponent =
        diffPanel.component.apply { preferredSize = JBUI.size(900, 600) }

    override fun getPreferredFocusedComponent(): JComponent? = diffPanel.preferredFocusedComponent
}
