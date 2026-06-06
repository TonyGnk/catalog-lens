package io.github.tonygnk.cataloglens.sort

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class SortPreviewDialog(
    private val project: Project,
    fileName: String,
    private val fileType: FileType,
    private val currentText: String,
    private val analysis: CatalogGroupSorter.Analysis,
) : DialogWrapper(project) {

    private val diffPanel = DiffManager.getInstance().createRequestPanel(project, disposable, null)
    private val groupList = CheckBoxList<Int>()
    private val factory = DiffContentFactory.getInstance()
    private val currentContent = factory.create(project, currentText, fileType)
    private val diffTitle = "Sort $fileName"

    var resultText: String = currentText
        private set

    init {
        title = diffTitle
        setOKButtonText("Apply Sort")
        for (group in analysis.changedGroups) {
            val label = StringUtil.shortenTextWithEllipsis(group.label, 40, 0)
            val lines = group.lineRange
            groupList.addItem(group.index, "$label  (lines ${lines.first}–${lines.last})", true)
        }
        groupList.setCheckBoxListListener { _, _ -> updatePreview() }
        updatePreview()
        init()
    }

    private fun selectedIndices(): Set<Int> = buildSet {
        for (i in 0 until groupList.model.size) {
            val index = groupList.getItemAt(i) ?: continue
            if (groupList.isItemSelected(i)) add(index)
        }
    }

    private fun updatePreview() {
        resultText = analysis.compose(selectedIndices())
        diffPanel.setRequest(
            SimpleDiffRequest(
                diffTitle,
                currentContent,
                factory.create(project, resultText, fileType),
                "Current",
                "Result",
            )
        )
        isOKActionEnabled = resultText != currentText
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
        add(
            JPanel(BorderLayout(0, JBUI.scale(4))).apply {
                add(JBLabel("Groups to sort:"), BorderLayout.NORTH)
                add(
                    JBScrollPane(groupList).apply {
                        preferredSize = Dimension(JBUI.scale(260), 0)
                    },
                    BorderLayout.CENTER,
                )
            },
            BorderLayout.WEST,
        )
        add(diffPanel.component.apply { preferredSize = JBUI.size(800, 600) }, BorderLayout.CENTER)
    }

    override fun getDimensionServiceKey(): String = "CatalogLens.SortPreviewDialog"

    override fun getPreferredFocusedComponent(): JComponent = groupList
}
