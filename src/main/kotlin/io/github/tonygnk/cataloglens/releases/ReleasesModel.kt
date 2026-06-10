package io.github.tonygnk.cataloglens.releases

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.vfs.VirtualFile

/**
 * Where to write back a chosen version. Carried from the [versions] gutter marker through the
 * dispatcher to the panel so the viewer can mark the current entry and offer "use this version".
 */
data class VersionEditContext(
    val file: VirtualFile,
    val refName: String,
    val currentVersion: String?,
)

sealed interface ContentTarget {
    val url: String
    val title: String
    val edit: VersionEditContext?

    fun withEdit(edit: VersionEditContext?): ContentTarget

    data class GithubReleases(
        val owner: String,
        val repo: String,
        override val url: String,
        override val edit: VersionEditContext? = null,
    ) : ContentTarget {
        val slug: String get() = "$owner/$repo"
        override val title: String get() = slug
        override fun withEdit(edit: VersionEditContext?) = copy(edit = edit)
    }

    data class DevsiteArticle(
        val label: String,
        override val url: String,
        override val edit: VersionEditContext? = null,
    ) : ContentTarget {
        override val title: String get() = label
        override fun withEdit(edit: VersionEditContext?) = copy(edit = edit)
    }
}

data class GithubRelease(
    val tagName: String,
    val name: String?,
    val publishedAt: String?,
    val body: String?,
    val htmlUrl: String?,
    val prerelease: Boolean,
)

internal class GithubReleaseDto {
    @SerializedName("tag_name") var tagName: String? = null
    var name: String? = null
    @SerializedName("published_at") var publishedAt: String? = null
    var body: String? = null
    @SerializedName("html_url") var htmlUrl: String? = null
    var prerelease: Boolean = false

    fun toDomain(): GithubRelease = GithubRelease(
        tagName = tagName ?: name ?: "(untitled)",
        name = name,
        publishedAt = publishedAt,
        body = body,
        htmlUrl = htmlUrl,
        prerelease = prerelease,
    )
}

/** One version block of a devsite changelog. [version] is the pinnable token, null for preamble. */
data class ChangelogSection(val version: String?, val header: String, val markdown: String)

enum class FailureKind { RATE_LIMIT, NETWORK }

sealed interface ContentState {
    val target: ContentTarget

    data class Loading(override val target: ContentTarget) : ContentState
    data class Releases(override val target: ContentTarget, val releases: List<GithubRelease>) : ContentState
    data class Article(override val target: ContentTarget, val sections: List<ChangelogSection>) : ContentState
    data class Failed(override val target: ContentTarget, val kind: FailureKind) : ContentState
}

interface ReleasesView {
    fun render(state: ContentState)
}
