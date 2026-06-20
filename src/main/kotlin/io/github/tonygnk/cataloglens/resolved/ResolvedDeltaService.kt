package io.github.tonygnk.cataloglens.resolved

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.tonygnk.cataloglens.resolved.model.ResolvedGraphModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gradle.tooling.GradleConnector

/**
 * Computes the A→B delta: re-resolves the baseline's scope, diffs against the stored baseline, and
 * classifies every change into "You changed" (traces to a declared edit), "Transitive ripple"
 * (everything else the user did not type), and "Rejected & excluded". On success it auto-promotes the
 * freshly resolved graph to the new baseline, so each Compare both shows and consumes the change.
 */
@Service(Service.Level.PROJECT)
class ResolvedDeltaService(private val project: Project, private val cs: CoroutineScope) {

    @Volatile
    private var view: DeltaView? = null

    @Volatile
    private var lastState: DeltaState = DeltaState.Empty

    @Volatile
    private var currentJob: Job? = null

    fun attachView(v: DeltaView) {
        view = v
        v.render(lastState)
    }

    fun detachView(v: DeltaView) {
        if (view === v) view = null
    }

    fun computeDelta(
        catalogFile: VirtualFile,
        catalogText: String,
        currentDeclared: Map<String, String>,
        gradleProjectPath: String,
    ) {
        val baseline = project.service<ResolvedBaselineService>().baseline
        if (baseline == null || baseline.catalogUrl != catalogFile.url) {
            applyState(DeltaState.Failed("No baseline for this catalog. Capture one first."))
            return
        }
        currentJob?.cancel()
        val cts = GradleConnector.newCancellationTokenSource()
        val scope = "${baseline.projectPath}/${baseline.configurationName}"
        val req = InsightRequest(gradleProjectPath, baseline.projectPath, baseline.configurationName, "", "delta")
        val job = cs.launch {
            post(DeltaState.Computing(scope))
            try {
                val model = withContext(Dispatchers.IO) {
                    GradleModelResolver.resolve(project, req, ResolvedGraphModel::class.java, cts.token())
                }
                val errorMessage = model.errorMessage
                if (errorMessage != null) {
                    post(DeltaState.Failed(errorMessage))
                } else {
                    val delta = DeltaComputer.compute(baseline, model, currentDeclared, scope)
                    withContext(Dispatchers.EDT) {
                        // Clicking Compare consumes the change: promote the freshly resolved graph to the
                        // new baseline so the next Compare diffs against this point. This replaces the
                        // manual "Set as baseline" button.
                        project.service<ResolvedBaselineService>()
                            .adopt(catalogFile, catalogText, currentDeclared, model)
                        applyState(DeltaState.Ready(delta))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                thisLogger().warn("Delta computation failed", e)
                post(DeltaState.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
        job.invokeOnCompletion { cause -> if (cause != null) cts.cancel() }
        currentJob = job
    }

    private suspend fun post(state: DeltaState) = withContext(Dispatchers.EDT) { applyState(state) }

    private fun applyState(state: DeltaState) {
        lastState = state
        view?.render(state)
    }
}
