package io.github.tonygnk.cataloglens.releases

import com.google.gson.Gson
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class GithubReleasesService(private val cs: CoroutineScope) {

    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    @Volatile
    private var view: ReleasesView? = null

    @Volatile
    private var lastState: ContentState? = null

    /** Fetched content, keyed by URL only — the per-click [VersionEditContext] lives on the target. */
    private sealed interface Payload {
        data class Releases(val list: List<GithubRelease>) : Payload
        data class Article(val sections: List<ChangelogSection>) : Payload
    }

    private data class CacheEntry(val payload: Payload, val fetchedAt: Long)

    fun attachView(view: ReleasesView) {
        this.view = view
        lastState?.let { view.render(it) }
    }

    fun detachView(view: ReleasesView) {
        if (this.view === view) this.view = null
    }

    fun show(target: ContentTarget) {
        cs.launch {
            postState(ContentState.Loading(target))
            postState(resolve(target))
        }
    }

    fun refresh() {
        val target = lastState?.target ?: return
        cache.remove(target.url)
        show(target)
    }

    private suspend fun resolve(target: ContentTarget): ContentState =
        withContext(Dispatchers.IO) {
            cache[target.url]?.let {
                if (System.currentTimeMillis() - it.fetchedAt < TTL_MS) return@withContext wrap(target, it.payload)
            }
            val state = fetch(target)
            payloadOf(state)?.let { cache[target.url] = CacheEntry(it, System.currentTimeMillis()) }
            state
        }

    private fun wrap(target: ContentTarget, payload: Payload): ContentState = when (payload) {
        is Payload.Releases -> ContentState.Releases(target, payload.list)
        is Payload.Article -> ContentState.Article(target, payload.sections)
    }

    private fun payloadOf(state: ContentState): Payload? = when (state) {
        is ContentState.Releases -> Payload.Releases(state.releases)
        is ContentState.Article -> Payload.Article(state.sections)
        else -> null
    }

    private fun fetch(target: ContentTarget): ContentState = when (target) {
        is ContentTarget.GithubReleases -> fetchReleases(target)
        is ContentTarget.DevsiteArticle -> fetchArticle(target)
    }

    private fun fetchReleases(target: ContentTarget.GithubReleases): ContentState =
        try {
            val api = "https://api.github.com/repos/${target.owner}/${target.repo}/releases?per_page=20"
            val json = HttpRequests.request(api)
                .accept("application/vnd.github+json")
                .tuner { it.setRequestProperty("X-GitHub-Api-Version", "2022-11-28") }
                .productNameAsUserAgent()
                .readString()
            val dtos = gson.fromJson(json, Array<GithubReleaseDto>::class.java) ?: emptyArray()
            ContentState.Releases(target, dtos.map { it.toDomain() })
        } catch (e: HttpRequests.HttpStatusException) {
            if (e.statusCode == 403 || e.statusCode == 429) {
                ContentState.Failed(target, FailureKind.RATE_LIMIT)
            } else {
                ContentState.Failed(target, FailureKind.NETWORK)
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to fetch GitHub releases for ${target.slug}", e)
            ContentState.Failed(target, FailureKind.NETWORK)
        }

    private fun fetchArticle(target: ContentTarget.DevsiteArticle): ContentState =
        try {
            val html = HttpRequests.request(target.url)
                .productNameAsUserAgent()
                .readString()
            val sections = DevsiteChangelogConverter.toSections(html, target.url)
            if (sections.isEmpty()) {
                ContentState.Failed(target, FailureKind.NETWORK)
            } else {
                ContentState.Article(target, sections)
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to fetch devsite changelog for ${target.url}", e)
            ContentState.Failed(target, FailureKind.NETWORK)
        }

    private suspend fun postState(state: ContentState) {
        withContext(Dispatchers.EDT) {
            lastState = state
            view?.render(state)
        }
    }

    private companion object {
        const val TTL_MS = 30 * 60 * 1000L
    }
}
