package io.github.tonygnk.cataloglens.resolved

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import io.github.tonygnk.cataloglens.links.ArtifactUrlBuilder
import io.github.tonygnk.cataloglens.map.VersionUrlResolver
import io.github.tonygnk.cataloglens.psi.Coord
import io.github.tonygnk.cataloglens.releases.UrlActionDispatcher
import io.github.tonygnk.cataloglens.settings.CatalogLensGlobalSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class DeltaPanel(private val project: Project) : JBPanel<DeltaPanel>(BorderLayout()), DeltaView {

    private sealed class Row(val label: String) {
        open val baseline: String? = null
        open val resolved: String? = null
        open val note: String? = null

        class Section(label: String) : Row(label)
        class Change(val change: DeltaChange) : Row(change.coordinateKey) {
            override val baseline = change.baseline
            override val resolved = change.resolved
            override val note = change.note
        }
        class Requester(text: String) : Row(text)
        class Note(deltaNote: DeltaNote) : Row(deltaNote.label) {
            override val note = deltaNote.detail
        }

        override fun toString(): String = label
    }

    private val header = JBLabel().apply { border = JBUI.Borders.empty(6, 10) }
    private val root = DefaultMutableTreeNode()
    private val model = ListTreeTableModelOnColumns(root, columns())
    private val treeTable = object : TreeTable(model) {
        // Full text on hover — so a column too narrow to show it all truncates with "…" but never hides it.
        override fun getToolTipText(event: MouseEvent): String? {
            val r = rowAtPoint(event.point)
            val c = columnAtPoint(event.point)
            if (r < 0 || c < 0) return null
            val rowObj = (tree.getPathForRow(r)?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? Row
                ?: return null
            return (if (c == 0) rowObj.label else rowObj.note)?.takeIf { it.isNotBlank() }
        }
    }.apply {
        setRootVisible(false)
        tree.showsRootHandles = true
        setTreeCellRenderer(DeltaTreeRenderer())
        // Flex with the panel: the long "Change / Reason" column absorbs/yields width on resize, and the
        // table tracks the viewport so it shrinks (truncating) instead of forcing a horizontal scrollbar.
        setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN)
        columnModel.getColumn(0).preferredWidth = JBUI.scale(240)
    }
    private val scrollPane = JBScrollPane(treeTable).apply {
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }

    init {
        val toolbar = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            add(header)
        }
        add(toolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        installMouse()
        render(DeltaState.Empty)
    }

    override fun render(state: DeltaState) {
        when (state) {
            DeltaState.Empty -> {
                header.text = "No delta yet — edit the catalog, then Compute delta."
                setTree(emptyList())
            }
            is DeltaState.Computing -> {
                header.text = "Computing delta for ${state.scope}…"
                setTree(emptyList())
            }
            is DeltaState.Failed -> {
                header.text = state.message
                setTree(emptyList())
            }
            is DeltaState.Ready -> renderReady(state.delta)
        }
    }

    private fun renderReady(delta: Delta) {
        header.text = "Δ ${delta.scope} · ${delta.comparedCount} artifacts compared"

        val sections = buildList {
            // The two core diff sections are always shown — even at (0) — so an empty ripple reads
            // as "computed, nothing moved" rather than "did not run".
            add(section("You changed (${delta.youChanged.size})", delta.youChanged.map(::changeNode)))
            // Declared libs the catalog pins but a transitive overrode. Shown only when it fires, and
            // placed right under "You changed" because a contradicted pin outranks pure ripple.
            if (delta.overridden.isNotEmpty()) {
                add(section("Declared, overridden (${delta.overridden.size})", delta.overridden.map(::changeNode)))
            }
            add(section("Transitive ripple (${delta.ripple.size})", delta.ripple.map(::changeNode)))
            if (delta.rejected.isNotEmpty()) {
                add(section("Rejected (${delta.rejected.size})", delta.rejected.map { noteNode(it) }))
            }
            if (delta.excluded.isNotEmpty()) {
                add(section("Excluded (${delta.excluded.size})", delta.excluded.map { noteNode(it) }))
            }
        }
        setTree(sections)
        expandSections()
    }

    private fun section(label: String, children: List<DefaultMutableTreeNode>): DefaultMutableTreeNode =
        DefaultMutableTreeNode(Row.Section(label)).apply {
            if (children.isEmpty()) add(DefaultMutableTreeNode(Row.Requester("— none —")))
            else children.forEach(::add)
        }

    private fun changeNode(change: DeltaChange): DefaultMutableTreeNode =
        DefaultMutableTreeNode(Row.Change(change)).apply {
            change.requestedBy.forEach { add(DefaultMutableTreeNode(Row.Requester(it))) }
        }

    private fun noteNode(note: DeltaNote): DefaultMutableTreeNode =
        DefaultMutableTreeNode(Row.Note(note))

    private fun setTree(sections: List<DefaultMutableTreeNode>) {
        root.removeAllChildren()
        sections.forEach(root::add)
        model.reload()
        // reload() rebuilds the tree and leaves the viewport wherever the previous (often longer) delta
        // had it; a fresh compute should start at the first section. expandSections() runs synchronously
        // before this fires, so the top is computed against the final expanded layout.
        SwingUtilities.invokeLater { scrollPane.viewport.viewPosition = Point(0, 0) }
    }

    private fun expandSections() {
        for (i in 0 until root.childCount) {
            treeTable.tree.expandPath(TreePath(arrayOf<Any>(root, root.getChildAt(i))))
        }
    }

    private fun columns(): Array<ColumnInfo<*, *>> = arrayOf(
        TreeColumnInfo("Artifact"),
        textColumn("Change / Reason") { it.note },
    )

    private fun textColumn(name: String, value: (Row) -> String?): ColumnInfo<DefaultMutableTreeNode, String> =
        object : ColumnInfo<DefaultMutableTreeNode, String>(name) {
            override fun valueOf(node: DefaultMutableTreeNode): String =
                (node.userObject as? Row)?.let(value).orEmpty()
        }

    private fun installMouse() {
        treeTable.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = maybePopup(e)
            override fun mouseReleased(e: MouseEvent) = maybePopup(e)
        })
    }

    private fun maybePopup(e: MouseEvent) {
        if (!e.isPopupTrigger) return
        val change = changeAt(e) ?: return
        JPopupMenu().apply {
            add(JMenuItem("Why this version? (resolved)").apply { addActionListener { openInsight(change.coordinateKey) } })
            add(JMenuItem("Open Maven page").apply { addActionListener { openMaven(change) } })
            add(JMenuItem("Open changelog").apply { addActionListener { openChangelog(change) } })
        }.show(e.component, e.x, e.y)
    }

    private fun changeAt(e: MouseEvent): DeltaChange? {
        val path = treeTable.tree.getClosestPathForLocation(e.x, e.y) ?: return null
        treeTable.tree.selectionPath = path
        return (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject?.let { it as? Row.Change }?.change
    }

    private fun coordOf(change: DeltaChange): Coord {
        val parts = change.coordinateKey.split(":", limit = 2)
        return Coord(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, change.resolved ?: change.baseline)
    }

    private fun openInsight(coordinateKey: String) {
        val basePath = project.basePath ?: return
        ResolvedToolWindowFactory.activateInsight(
            project,
            InsightRequest(basePath, "", "", coordinateKey, coordinateKey),
        )
    }

    private fun openMaven(change: DeltaChange) {
        val style = CatalogLensGlobalSettings.getInstance().state.artifactUrlStyle
        BrowserUtil.browse(ArtifactUrlBuilder.url(coordOf(change), style))
    }

    private fun openChangelog(change: DeltaChange) {
        val url = VersionUrlResolver.resolveLibrary(project, coordOf(change)).firstOrNull()
            ?: return openMaven(change)
        UrlActionDispatcher.open(project, url)
    }

    private inner class DeltaTreeRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            val node = value as? DefaultMutableTreeNode ?: return
            when (val rowObj = node.userObject) {
                is Row.Section -> append(rowObj.label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                is Row.Change -> append(rowObj.label, attributesFor(rowObj.change.kind))
                is Row.Requester -> append(rowObj.label, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                is Row.Note -> append(rowObj.label, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }
    }

    private fun attributesFor(kind: ChangeKind): SimpleTextAttributes = when (kind) {
        ChangeKind.ADDED -> SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ADDED)
        ChangeKind.REMOVED -> SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, JBColor.GRAY)
        ChangeKind.DOWNGRADED -> SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, REMOVED)
        ChangeKind.OVERRIDDEN -> SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, OVERRIDDEN)
        ChangeKind.BUMPED -> SimpleTextAttributes.REGULAR_ATTRIBUTES
        ChangeKind.DECLARED -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
    }

    private companion object {
        val ADDED = JBColor(0x59A869, 0x499C54)
        val REMOVED = JBColor(0xC75450, 0xC75450)
        val OVERRIDDEN = JBColor(0xB8860B, 0xCC7832)
    }
}
