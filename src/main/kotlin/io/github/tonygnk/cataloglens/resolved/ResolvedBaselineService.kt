package io.github.tonygnk.cataloglens.resolved

import com.intellij.ide.ActivityTracker
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.ui.EditorNotifications
import io.github.tonygnk.cataloglens.resolved.model.ResolvedGraphModel
import io.github.tonygnk.cataloglens.settings.CatalogLensGlobalSettings
import org.toml.lang.psi.TomlFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gradle.tooling.GradleConnector

/** One resolved-graph snapshot for a catalog file at a moment, keyed by that file's content hash. */
data class Baseline(
    val catalogUrl: String,
    val projectPath: String,
    val configurationName: String,
    val capturedAt: Long,
    val versions: Map<String, String>,
    val declared: Map<String, String>,
)

/**
 * The live, project-level resolved graph that the inline hints read. Auto-refreshed on every successful
 * Gradle sync (so hints stay current and never vanish), independent of the [Baseline] the delta compares
 * against. Transient — rebuilt on the first sync after a reopen; until then hints fall back to the
 * persisted [Baseline].
 */
data class LiveSnapshot(val scope: String, val versions: Map<String, String>, val resolvedAt: Long)

/**
 * Holds the current [Baseline] and captures new ones on explicit action only (never ambiently).
 * The inline overlay reads resolved versions from here; the overlay and the Compute Delta toolbar
 * action gate on [isStale], which tracks the IDE's pending-sync state across all build files.
 */
@Service(Service.Level.PROJECT)
@State(name = "CatalogLensResolvedBaseline", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ResolvedBaselineService(private val project: Project, private val cs: CoroutineScope) :
    Disposable, PersistentStateComponent<ResolvedBaselineService.State> {

    /** Persisted form of [Baseline]; survives project close so the user need not re-capture each session. */
    class State {
        var catalogUrl: String? = null
        var projectPath: String = ""
        var configurationName: String = ""
        var capturedAt: Long = 0L
        var stale: Boolean = false
        var versions: MutableMap<String, String> = mutableMapOf()
        var declared: MutableMap<String, String> = mutableMapOf()
    }

    @Volatile
    var baseline: Baseline? = null
        private set

    /**
     * Live resolved graph for the inline hints — see [LiveSnapshot]. Set by [store] (capture/adopt) and
     * re-resolved on each successful sync via [refreshLive]; hints read it through [resolvedVersion].
     */
    @Volatile
    var live: LiveSnapshot? = null
        private set

    @Volatile
    private var currentJob: Job? = null

    @Volatile
    private var liveJob: Job? = null

    /**
     * Sticky staleness: set true when the IDE flags a pending Gradle sync (any build file changed),
     * cleared only by an explicit capture/adopt. Persisted so it survives a project reopen.
     */
    @Volatile
    private var stale: Boolean = false

    /**
     * Pending-sync signal that works in Android Studio (where the platform's notification TOPIC is dead):
     * set true on any tracked build-file edit, cleared on a successful sync. Unlike [stale] (sticky until
     * capture), this tracks "are there edits the IDE hasn't synced yet" — drives the Capture icon, which
     * appears only once the project is synced.
     */
    @Volatile
    private var needsSync: Boolean = false

    /**
     * Wall-clock of the last successful Gradle sync (external-system data import) seen for this project,
     * 0 if none yet. The on-disk graph the Tooling API reads is current as of this moment, so a later
     * Compute Delta can skip a redundant forced sync. Not persisted (resets each session).
     */
    @Volatile
    var lastImportFinishedAt: Long = 0L
        private set

    init {
        // Primary, IDE-agnostic signal: any edit to a tracked build file (catalog, *.gradle[.kts],
        // gradle.properties) marks the baseline stale. Android Studio drives its "Gradle files changed"
        // banner through its own machinery and does NOT deliver the platform TOPIC below, so the document
        // listener is what actually works there; the TOPIC stays as a secondary signal for plain IntelliJ.
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) = onBuildFileDocumentChanged(event)
            },
            this,
        )
        project.messageBus.connect(this).subscribe(
            ExternalSystemProjectNotificationAware.TOPIC,
            object : ExternalSystemProjectNotificationAware.Listener {
                override fun onNotificationChanged(project: Project) = onSyncNotificationChanged()
            },
        )
        // Sync-SUCCESS signal. Unlike the autoimport TOPIC above (dead in Android Studio), the external-
        // system data-import pipeline is shared with AS sync, so this fires there too.
        project.messageBus.connect(this).subscribe(
            ProjectDataImportListener.TOPIC,
            object : ProjectDataImportListener {
                override fun onImportFinished(projectPath: String?) = onProjectImportFinished(projectPath, success = true)
                override fun onImportFailed(projectPath: String?) = onProjectImportFinished(projectPath, success = false)
            },
        )
        // Catch a needs-sync state already present before this service subscribed.
        onSyncNotificationChanged()
    }

    /**
     * Fired after a Gradle sync's data import completes. Records the success timestamp and refreshes so the
     * toolbar/inlays re-evaluate; deliberately does NOT clear [stale] — only an explicit capture/adopt does
     * that, so a stale baseline stays stale through an unrelated sync until the user re-captures.
     */
    private fun onProjectImportFinished(projectPath: String?, success: Boolean) {
        if (success) {
            lastImportFinishedAt = System.currentTimeMillis()
            // The project is now in sync — pending edits are reconciled. Clears needsSync (so the Capture
            // icon reappears) but NOT stale (the captured reference may still predate this sync).
            needsSync = false
        }
        if (!success) return
        ApplicationManager.getApplication().invokeLater {
            val b = baseline
            if (b == null) {
                // No reference yet — bootstrap it from this first sync so hints + delta work with no
                // manual capture. capture() resolves and stores R (and L), so refreshLive below is a no-op
                // this round (baseline still null until it completes).
                autoBootstrap()
                return@invokeLater
            }
            val file = VirtualFileManager.getInstance().findFileByUrl(b.catalogUrl) ?: return@invokeLater
            refresh(file)
        }
        refreshLive()
    }

    /**
     * First-sync bootstrap of the reference baseline + live snapshot, so the feature works without a manual
     * capture. Resolves the conventional `gradle/libs.versions.toml`; gated on the inlay setting so projects
     * with the feature off (or no catalog at the conventional path) never resolve ambiently. A non-standard
     * or secondary catalog can still be captured explicitly via the editor right-click action.
     */
    private fun autoBootstrap() {
        if (!CatalogLensGlobalSettings.getInstance().state.resolvedInlaysEnabled) return
        val basePath = project.basePath ?: return
        val vFile = LocalFileSystem.getInstance().findFileByPath("$basePath/gradle/libs.versions.toml") ?: return
        val toml = PsiManager.getInstance(project).findFile(vFile) as? TomlFile ?: return
        val text = FileDocumentManager.getInstance().getDocument(vFile)?.text ?: toml.text
        val declared = CatalogEntrySupport.declaredVersions(toml)
        capture(vFile, text, declared, basePath)
    }

    /**
     * Re-resolve the graph after a successful sync and publish it as the [live] snapshot so the hints
     * track reality. Gated on an existing [baseline] (the user opted in by capturing once) — before the
     * first capture nothing ambient resolves. Coalesced: a new sync cancels the prior in-flight resolve.
     */
    private fun refreshLive() {
        val b = baseline ?: return
        if (!CatalogLensGlobalSettings.getInstance().state.resolvedInlaysEnabled) return
        val basePath = project.basePath ?: return
        liveJob?.cancel()
        val cts = GradleConnector.newCancellationTokenSource()
        val req = InsightRequest(basePath, b.projectPath, b.configurationName, "", "live")
        val job = cs.launch {
            try {
                val model = withContext(Dispatchers.IO) {
                    GradleModelResolver.resolve(project, req, ResolvedGraphModel::class.java, cts.token())
                }
                if (model.errorMessage != null) {
                    thisLogger().warn("Resolved-version refresh failed: ${model.errorMessage}")
                    return@launch
                }
                val versions = versionsOf(model)
                withContext(Dispatchers.EDT) {
                    live = LiveSnapshot(scopeOf(model), versions, System.currentTimeMillis())
                    VirtualFileManager.getInstance().findFileByUrl(b.catalogUrl)?.let { refresh(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                thisLogger().warn("Resolved-version refresh failed", e)
            }
        }
        job.invokeOnCompletion { cause -> if (cause != null) cts.cancel() }
        liveJob = job
    }

    private fun onBuildFileDocumentChanged(event: DocumentEvent) {
        if (baseline == null) return
        if (stale && needsSync) return
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
        if (!isTrackedBuildFile(file)) return
        stale = true
        needsSync = true
        val catalog = baseline?.catalogUrl?.let { VirtualFileManager.getInstance().findFileByUrl(it) } ?: return
        refresh(catalog)
    }

    private fun isTrackedBuildFile(file: VirtualFile): Boolean {
        if (file.url == baseline?.catalogUrl) return true
        // Name first: the document listener is global (fires on every keystroke in any file), so reject the
        // overwhelmingly common non-build edit with a couple of cheap string checks before touching paths.
        val name = file.name
        if (!name.endsWith(".gradle.kts") && !name.endsWith(".gradle") && name != "gradle.properties") return false
        val base = project.basePath ?: return false
        // Guard the separator so a sibling project sharing a path prefix (/foo vs /foobar) is not
        // mistaken for a file inside this project.
        return file.path == base || file.path.startsWith("$base/")
    }

    private fun onSyncNotificationChanged() {
        // Always evaluate + mutate on the EDT: the platform query and the daemon/toolbar refresh below
        // are only reliable there, and the topic may be published from a background thread.
        ApplicationManager.getApplication().invokeLater {
            val visible = ExternalSystemProjectNotificationAware.getInstance(project).isNotificationVisible()
            if (baseline != null && visible) stale = true
            val b = baseline ?: return@invokeLater
            val file = VirtualFileManager.getInstance().findFileByUrl(b.catalogUrl) ?: return@invokeLater
            refresh(file)
        }
    }

    fun capture(catalogFile: VirtualFile, catalogText: String, declared: Map<String, String>, gradleProjectPath: String) {
        currentJob?.cancel()
        val cts = GradleConnector.newCancellationTokenSource()
        val req = InsightRequest(gradleProjectPath, "", "", "", "baseline")
        val job = cs.launch {
            try {
                val model = withContext(Dispatchers.IO) {
                    GradleModelResolver.resolve(project, req, ResolvedGraphModel::class.java, cts.token())
                }
                withContext(Dispatchers.EDT) {
                    model.errorMessage?.let {
                        notify("Could not capture baseline: $it", NotificationType.ERROR)
                        return@withContext
                    }
                    store(catalogFile, catalogText, declared, model)
                    notify("Baseline captured: ${model.components.size} artifacts at ${scopeLabel()}", NotificationType.INFORMATION)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                thisLogger().warn("Baseline capture failed", e)
                withContext(Dispatchers.EDT) { notify("Baseline capture failed: ${e.message ?: e.javaClass.simpleName}", NotificationType.ERROR) }
            }
        }
        job.invokeOnCompletion { cause -> if (cause != null) cts.cancel() }
        currentJob = job
    }

    /** Promote an already-resolved graph (from a delta) to the new baseline without re-resolving. */
    fun adopt(catalogFile: VirtualFile, catalogText: String, declared: Map<String, String>, model: ResolvedGraphModel) {
        store(catalogFile, catalogText, declared, model)
        notify("Baseline updated: ${model.components.size} artifacts at ${scopeLabel()}", NotificationType.INFORMATION)
    }

    private fun store(catalogFile: VirtualFile, catalogText: String, declared: Map<String, String>, model: ResolvedGraphModel) {
        val versions = versionsOf(model)
        baseline = Baseline(
            catalogUrl = catalogFile.url,
            projectPath = model.projectPath.orEmpty(),
            configurationName = model.configurationName.orEmpty(),
            capturedAt = System.currentTimeMillis(),
            versions = versions,
            declared = declared,
        )
        // The capture/adopt also defines the live snapshot — R and L coincide at this point.
        live = LiveSnapshot(scopeOf(model), versions, System.currentTimeMillis())
        stale = false
        refresh(catalogFile)
    }

    private fun versionsOf(model: ResolvedGraphModel): Map<String, String> =
        model.components
            .mapNotNull { c ->
                val g = c.group ?: return@mapNotNull null
                val n = c.name ?: return@mapNotNull null
                val v = c.version ?: return@mapNotNull null
                "$g:$n" to v
            }
            .toMap()

    private fun scopeOf(model: ResolvedGraphModel): String =
        "${model.projectPath.orEmpty()}/${model.configurationName.orEmpty()}"

    /**
     * Resolved version for the hints: the live snapshot first (current as of the last sync), falling back
     * to the persisted baseline (covers the window after a reopen before the first sync refreshes [live]).
     * Both are project-scope, so the catalog file is not matched here.
     */
    fun resolvedVersion(catalogFile: VirtualFile, groupName: String): String? =
        live?.versions?.get(groupName) ?: baseline?.versions?.get(groupName)

    /** True once any resolved data exists (live or persisted baseline) — gates whether hints render. */
    fun hasResolvedData(): Boolean = live != null || baseline != null

    fun hasBaselineFor(catalogFile: VirtualFile): Boolean = baseline?.catalogUrl == catalogFile.url

    fun isStale(catalogFile: VirtualFile): Boolean {
        val b = baseline ?: return false
        if (b.catalogUrl != catalogFile.url) return false
        // Read the sticky flag only — never call isNotificationVisible() here. This runs on the action
        // update / inlay-collector threads, and that platform query is only reliable on the EDT (where
        // the TOPIC listener and the initial check set the flag).
        return stale
    }

    /**
     * True when there are no build-file edits awaiting a sync — i.e. the on-disk graph is current, so
     * capturing now snapshots fresh data. Gates the Capture icon's visibility (appears only when synced;
     * hides after a build-file edit until the next successful sync). Not tied to a sync having fired this
     * session: a freshly opened, already-synced project is "finished" with no pending edits.
     */
    fun isSyncFinished(): Boolean = !needsSync

    fun scopeLabel(): String? = baseline?.let { "${it.projectPath}/${it.configurationName}" }

    private fun refresh(catalogFile: VirtualFile) {
        // Declarative inlays cache per file modification stamp, so a plain daemon restart will not
        // recompute them when only the baseline changed (no PSI edit). Bump the factory stamp for
        // the open editors first, then restart — otherwise hints only reappear after a manual edit.
        FileDocumentManager.getInstance().getDocument(catalogFile)?.let { document ->
            EditorFactory.getInstance().getEditors(document, project).forEach { editor ->
                DeclarativeInlayHintsPassFactory.scheduleRecompute(editor, project)
            }
        }
        (PsiManager.getInstance(project).findFile(catalogFile))?.let {
            DaemonCodeAnalyzer.getInstance(project).restart(it)
        }
        EditorNotifications.getInstance(project).updateNotifications(catalogFile)
        // Nudge action toolbars so the floating Compute Delta icon re-evaluates visibility at once.
        ActivityTracker.getInstance().inc()
    }

    private fun notify(content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CatalogLens")
            .createNotification(content, type)
            .notify(project)
    }

    override fun getState(): State {
        val b = baseline ?: return State()
        return State().apply {
            catalogUrl = b.catalogUrl
            projectPath = b.projectPath
            configurationName = b.configurationName
            capturedAt = b.capturedAt
            stale = this@ResolvedBaselineService.stale
            versions = LinkedHashMap(b.versions)
            declared = LinkedHashMap(b.declared)
        }
    }

    override fun loadState(state: State) {
        val url = state.catalogUrl ?: return
        baseline = Baseline(
            catalogUrl = url,
            projectPath = state.projectPath,
            configurationName = state.configurationName,
            capturedAt = state.capturedAt,
            versions = state.versions.toMap(),
            declared = state.declared.toMap(),
        )
        stale = state.stale
    }

    override fun dispose() {}
}
