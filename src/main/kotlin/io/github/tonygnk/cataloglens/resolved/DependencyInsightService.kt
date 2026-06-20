package io.github.tonygnk.cataloglens.resolved

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import io.github.tonygnk.cataloglens.resolved.model.DependencyInsightModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gradle.tooling.GradleConnector

@Service(Service.Level.PROJECT)
class DependencyInsightService(private val project: Project, private val cs: CoroutineScope) {

    @Volatile
    private var view: InsightView? = null

    @Volatile
    private var lastState: InsightState? = null

    /** Last non-[InsightState.Resolving] state, restored on cancel so the panel does not stick on the spinner. */
    @Volatile
    private var settledState: InsightState = InsightState.NoProject

    @Volatile
    private var currentJob: Job? = null

    fun attachView(view: InsightView) {
        this.view = view
        lastState?.let { view.render(it) }
    }

    fun detachView(view: InsightView) {
        if (this.view === view) this.view = null
    }

    fun cancel() {
        val job = currentJob ?: return
        if (!job.isActive) return
        job.cancel()
        // Cancellation rethrows in the resolve coroutine, so no terminal state is posted there — restore
        // the last settled state here, otherwise the panel stays on "Resolving…" forever.
        cs.launch { postState(settledState) }
    }

    fun resolve(req: InsightRequest) {
        currentJob?.cancel()
        val cts = GradleConnector.newCancellationTokenSource()
        val job = cs.launch {
            postState(InsightState.Resolving(req))
            try {
                postState(runResolve(req, cts.token()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                thisLogger().warn("Dependency insight resolve failed for ${req.coordinate}", e)
                postState(InsightState.Failed(req, e.messageOrType()))
            }
        }
        // Coroutine cancellation cannot interrupt the blocking Tooling API call, so cancel the
        // daemon operation explicitly when the job ends abnormally (superseded or user-cancelled).
        job.invokeOnCompletion { cause -> if (cause != null) cts.cancel() }
        currentJob = job
    }

    private suspend fun runResolve(req: InsightRequest, token: org.gradle.tooling.CancellationToken): InsightState =
        withContext(Dispatchers.IO) {
            InsightState.Ready(req, GradleModelResolver.resolve(project, req, DependencyInsightModel::class.java, token))
        }

    private suspend fun postState(state: InsightState) {
        withContext(Dispatchers.EDT) {
            lastState = state
            if (state !is InsightState.Resolving) settledState = state
            view?.render(state)
        }
    }

    private fun Throwable.messageOrType(): String = message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
}
