package io.github.tonygnk.cataloglens.links

import io.github.tonygnk.cataloglens.psi.Coord

enum class ArtifactUrlStyle(val displayName: String) {
    MAVEN_CENTRAL("Maven Central"),
    MVNREPOSITORY("MvnRepository"),
}

object ArtifactUrlBuilder {

    fun url(coord: Coord, style: ArtifactUrlStyle = ArtifactUrlStyle.MAVEN_CENTRAL): String {
        val base = when (style) {
            ArtifactUrlStyle.MAVEN_CENTRAL ->
                "https://central.sonatype.com/artifact/${coord.group}/${coord.name}"
            ArtifactUrlStyle.MVNREPOSITORY ->
                "https://mvnrepository.com/artifact/${coord.group}/${coord.name}"
        }
        return if (coord.version != null) "$base/${coord.version}" else base
    }
}
