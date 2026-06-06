package io.github.tonygnk.cataloglens.sort

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class SortPreviewDialog(
    project: Project,
    fileName: String,
    fileType: FileType,
    currentText: String,
    sortedText: String,
) : DialogWrapper(project) {

    private val diffPanel = DiffManager.getInstance().createRequestPanel(project, disposable, null)
    private val resultContent = DiffContentFactory.getInstance().createEditable(project, sortedText, fileType)

    val resultText: String
        get() = resultContent.document.text

    init {
        title = "Sort $fileName"
        setOKButtonText("Apply Sort")
        diffPanel.setRequest(
            SimpleDiffRequest(
                "Sort $fileName",
                DiffContentFactory.getInstance().create(project, currentText, fileType),
                resultContent,
                "Current",
                "Result",
            )
        )
        init()
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(6))).apply {
        add(diffPanel.component.apply { preferredSize = JBUI.size(900, 600) }, BorderLayout.CENTER)
        add(
            JBLabel("Use the gutter arrows to revert individual changes; the result pane is editable.").apply {
                foreground = UIUtil.getContextHelpForeground()
                border = JBUI.Borders.emptyLeft(2)
            },
            BorderLayout.SOUTH,
        )
    }

    override fun getPreferredFocusedComponent(): JComponent? = diffPanel.preferredFocusedComponent
}
