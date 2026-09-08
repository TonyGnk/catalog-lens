package io.github.tonygnk.cataloglens.releases

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

object UrlActionDispatcher {

    const val TOOL_WINDOW_ID = "CatalogLens Releases"

    private val GITHUB_RELEASES = Regex("""^https?://github\.com/([^/]+)/([^/]+)/releases/?$""")
    // Lazy path plus optional ?query / #fragment, so "CHANGELOG.md?plain=1" and "…#L20" still map
    // to the file itself.
    private val GITHUB_BLOB =
        Regex("""^https?://github\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+?)(?:\?[^#]*)?(?:#.*)?$""")
    private val DEVSITE = Regex("""^https?://developer\.android\.com/(\S+)$""")
    private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdown")

    fun open(project: Project, url: String, edit: VersionEditContext? = null) {
        val target = classify(url)?.withEdit(edit)
        if (target == null) {
            BrowserUtil.browse(url)
            return
        }
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        if (toolWindow == null) {
            BrowserUtil.browse(url)
            return
        }
        toolWindow.isAvailable = true
        toolWindow.activate {
            project.service<GithubReleasesService>().show(target)
        }
    }

    private fun classify(url: String): ContentTarget? {
        val trimmed = url.trim()
        GITHUB_RELEASES.matchEntire(trimmed)?.let {
            val (owner, repo) = it.destructured
            return ContentTarget.GithubReleases(owner, repo, trimmed)
        }
        GITHUB_BLOB.matchEntire(trimmed)?.let {
            val (owner, repo, ref, path) = it.destructured
            // Only Markdown is worth parsing; any other blob stays a browser link.
            if (path.substringAfterLast('.', "").lowercase() in MARKDOWN_EXTENSIONS) {
                return ContentTarget.GithubMarkdown(owner, repo, ref, path, trimmed)
            }
        }
        DEVSITE.matchEntire(trimmed)?.let { match ->
            val path = match.groupValues[1].substringBefore('?').substringBefore('#')
            val label = path.trim('/').substringAfterLast('/').ifBlank { "developer.android.com" }
            return ContentTarget.DevsiteArticle(label, trimmed)
        }
        return null
    }
}
