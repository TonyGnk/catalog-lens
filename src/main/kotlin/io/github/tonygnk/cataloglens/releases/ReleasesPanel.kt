package io.github.tonygnk.cataloglens.releases

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextPane
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit

class ReleasesPanel(private val project: Project) : JBPanel<ReleasesPanel>(BorderLayout()), ReleasesView {

    private val titleLabel = JBLabel().apply { font = JBFont.label().asBold() }
    private val openLink = ActionLink("Open in browser") {}
    private val content = ScrollableContent()
    private val scrollPane = JBScrollPane(
        content,
        JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
    )
    private val currentColor = JBColor(0x4C8C3F, 0x57965C)
    private var currentState: ContentState? = null

    init {
        val refresh = JButton(AllIcons.Actions.Refresh).apply {
            toolTipText = "Reload"
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusable = false
            margin = JBUI.emptyInsets()
            border = JBUI.Borders.empty()
            addActionListener { project.service<GithubReleasesService>().refresh() }
        }
        val header = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(6, 16, 6, 10)
            add(titleLabel, BorderLayout.WEST)
            val east = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                isOpaque = false
                add(openLink)
                add(Box.createHorizontalStrut(JBUI.scale(10)))
                add(refresh)
            }
            add(east, BorderLayout.EAST)
        }
        add(header, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }

    override fun render(state: ContentState) = render(state, preserveScroll = false)

    private fun render(state: ContentState, preserveScroll: Boolean) {
        val scrollPos = if (preserveScroll) scrollPane.viewport.viewPosition else null
        currentState = state
        titleLabel.text = state.target.title
        for (l in openLink.actionListeners) openLink.removeActionListener(l)
        openLink.addActionListener { BrowserUtil.browse(state.target.url) }

        content.removeAll()
        when (state) {
            is ContentState.Loading -> addCentered(JBLabel("Loading…"))
            is ContentState.Releases -> {
                if (state.releases.isEmpty()) {
                    addCentered(JBLabel("No releases published."))
                } else {
                    state.releases.forEach { content.add(releaseCard(it, state.target.edit)) }
                }
            }
            is ContentState.Article ->
                state.sections.forEach { content.add(sectionCard(it, state.target.title, state.target.edit)) }
            is ContentState.Failed -> addCentered(failurePanel(state))
        }
        content.revalidate()
        content.repaint()
        if (scrollPos != null) {
            // Re-render after a write rebuilds the card list; without this the viewport jumps
            // (focus leaves the removed ActionLink and the scroll pane resets). Pin it back.
            SwingUtilities.invokeLater { scrollPane.viewport.viewPosition = scrollPos }
        }
    }

    /** After a successful write, redraw with the new current version so the badge moves — no refetch. */
    private fun onVersionBumped(newVersion: String) {
        val state = currentState ?: return
        val newEdit = state.target.edit?.copy(currentVersion = newVersion) ?: return
        val updated = when (state) {
            is ContentState.Releases -> state.copy(target = state.target.withEdit(newEdit))
            is ContentState.Article -> state.copy(target = state.target.withEdit(newEdit))
            else -> return
        }
        render(updated, preserveScroll = true)
    }

    private fun addCentered(component: JComponent) {
        component.alignmentX = Component.LEFT_ALIGNMENT
        content.add(component)
    }

    private fun markdownPane(markdown: String?): JTextPane = JTextPane().apply {
        editorKit = styledEditorKit()
        isEditable = false
        isOpaque = false
        border = JBUI.Borders.emptyTop(2)
        text = "<html><body>${MarkdownToHtml.toHtml(markdown)}</body></html>"
        addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                event.url?.let { BrowserUtil.browse(it.toString()) }
                    ?: event.description?.let { BrowserUtil.browse(it) }
            }
        }
    }

    /**
     * HTML kit carrying a theme-derived stylesheet. The default HTMLEditorKit CSS gives headings
     * huge, uneven margins and crams paragraphs/lists together; this normalises the heading scale,
     * adds consistent vertical rhythm between blocks, and pulls foreground / link / code colours
     * from the current IDE theme so it reads the same in light and dark.
     */
    private fun styledEditorKit(): HTMLEditorKit {
        val kit = HTMLEditorKitBuilder().build()
        val font = JBFont.label()
        val fg = ColorUtil.toHex(UIUtil.getLabelForeground())
        val link = ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED)
        val codeBg = ColorUtil.toHex(ColorUtil.mix(UIUtil.getPanelBackground(), UIUtil.getLabelForeground(), 0.10))
        kit.styleSheet.apply {
            addRule("body { font-family:'${font.family}'; font-size:${font.size + 3}pt; color:#$fg; line-height:1.4; margin:0; }")
            addRule("h2 { font-size:130%; margin:18px 0 5px 0; }")
            addRule("h3 { font-size:116%; margin:15px 0 4px 0; }")
            addRule("h4 { font-size:108%; margin:12px 0 3px 0; }")
            addRule("p { margin:0 0 11px 0; }")
            addRule("ul { margin:4px 0 12px 0; }")
            addRule("li { margin:0 0 6px 0; }")
            addRule("a { color:#$link; }")
            addRule("code { background-color:#$codeBg; }")
            addRule("pre { background-color:#$codeBg; margin:8px 0 12px 0; }")
        }
        return kit
    }

    private fun releaseCard(release: GithubRelease, edit: VersionEditContext?): JComponent {
        val header = StringBuilder(release.tagName)
        if (!release.name.isNullOrBlank() && release.name != release.tagName) {
            header.append("  —  ").append(release.name)
        }
        release.publishedAt?.takeIf { it.length >= 10 }?.let { header.append("   ·   ").append(it.substring(0, 10)) }
        if (release.prerelease) header.append("   (pre-release)")
        return versionCard(header.toString(), release.tagName, markdownPane(release.body), edit)
    }

    private fun sectionCard(section: ChangelogSection, fallbackTitle: String, edit: VersionEditContext?): JComponent {
        val header = section.header.ifBlank { fallbackTitle }
        return versionCard(header, section.version, markdownPane(section.markdown), edit)
    }

    private fun versionCard(
        headerText: String,
        rawVersion: String?,
        body: JComponent,
        edit: VersionEditContext?,
    ): JComponent {
        val card = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(4, 2, 16, 2)
        }
        val headerField = JTextPane().apply {
            contentType = "text/plain"
            text = headerText
            isEditable = false
            isOpaque = false
            border = null
            margin = JBUI.emptyInsets()
            font = JBFont.label().asBold().deriveFont(JBFont.label().size2D + 1f)
            foreground = UIUtil.getLabelForeground()
            caretPosition = 0
        }
        val top = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(headerField, BorderLayout.CENTER)
            versionControl(rawVersion, edit)?.let { add(it, BorderLayout.EAST) }
        }
        card.add(top, BorderLayout.NORTH)
        card.add(body, BorderLayout.CENTER)
        return card
    }

    /**
     * The per-card control: a "current" badge when [rawVersion] matches the catalog version, else a
     * link that pins this version into libs.versions.toml. Null when there is no edit context or no
     * parseable version token.
     */
    private fun versionControl(rawVersion: String?, edit: VersionEditContext?): JComponent? {
        if (edit == null) return null
        if (VersionMatcher.matches(edit.currentVersion, rawVersion)) {
            return JBLabel("current", AllIcons.Actions.Checked, SwingConstants.LEFT).apply {
                font = JBFont.label().asBold()
                foreground = currentColor
                border = JBUI.Borders.emptyLeft(12)
            }
        }
        val pinnable = VersionMatcher.extractPinnable(rawVersion) ?: return null
        return ActionLink("Use $pinnable") {
            if (VersionCatalogWriter.bump(project, edit, pinnable)) onVersionBumped(pinnable)
        }.apply { border = JBUI.Borders.emptyLeft(12) }
    }

    private fun failurePanel(state: ContentState.Failed): JComponent {
        val message = when (state.kind) {
            FailureKind.RATE_LIMIT ->
                "GitHub API rate limit reached (60 requests/hour when unauthenticated). Try again later."
            FailureKind.NETWORK ->
                "Could not load this page. Check your connection and try again."
        }
        return JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(80))
            add(JBLabel(message).apply { alignmentX = Component.LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(ActionLink("Open in browser") { BrowserUtil.browse(state.target.url) }.apply {
                alignmentX = Component.LEFT_ALIGNMENT
            })
        }
    }
}

/**
 * Vertical stack that tracks the viewport width, forcing child [JTextPane]s to soft-wrap their HTML
 * at the tool-window width instead of growing horizontally.
 */
private class ScrollableContent : JBPanel<ScrollableContent>(), Scrollable {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(10, 16, 12, 16)
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(64)
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false
}
