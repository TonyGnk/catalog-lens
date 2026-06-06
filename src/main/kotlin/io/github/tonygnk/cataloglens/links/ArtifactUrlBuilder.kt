package io.github.tonygnk.cataloglens.links

import io.github.tonygnk.cataloglens.psi.Coord

enum class ArtifactUrlStyle(val displayName: String) {
    MAVEN_CENTRAL("Maven Central"),
    MVNREPOSITORY("MvnRepository"),
}

object ArtifactUrlBuilder {

    // Groups hosted exclusively on Google Maven (absent from Maven Central).
    // Deliberately narrow: com.google.dagger, com.google.code.gson, com.google.guava,
    // com.google.devtools.ksp etc. are published to Central and must not match.
    private val GOOGLE_MAVEN_GROUP_PREFIXES = listOf(
        "androidx",
        "com.android",
        "com.google.android",
        "com.google.firebase",
        "com.google.gms",
        "com.google.mlkit",
        "com.google.ar",
        "com.google.oboe",
        "com.google.testing.platform",
    )

    fun url(coord: Coord, style: ArtifactUrlStyle = ArtifactUrlStyle.MAVEN_CENTRAL): String =
        when (style) {
            ArtifactUrlStyle.MAVEN_CENTRAL ->
                if (isGoogleMavenHosted(coord.group)) {
                    googleMavenUrl(coord)
                } else {
                    // Land on the Versions tab instead of the default Overview tab.
                    withVersion("https://central.sonatype.com/artifact/${coord.group}/${coord.name}", coord) + "/versions"
                }
            ArtifactUrlStyle.MVNREPOSITORY ->
                withVersion("https://mvnrepository.com/artifact/${coord.group}/${coord.name}", coord)
        }

    // No version in the anchor: the artifact page with the full version list is more
    // useful than pre-selecting one, and a stale version would render an empty detail pane.
    private fun googleMavenUrl(coord: Coord): String =
        "https://maven.google.com/web/index.html#${coord.group}:${coord.name}"

    private fun withVersion(base: String, coord: Coord): String =
        if (coord.version != null) "$base/${coord.version}" else base

    private fun isGoogleMavenHosted(group: String): Boolean =
        GOOGLE_MAVEN_GROUP_PREFIXES.any { group == it || group.startsWith("$it.") }
}
