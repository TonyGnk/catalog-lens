package io.github.tonygnk.cataloglens.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import io.github.tonygnk.cataloglens.links.ArtifactUrlStyle

@State(name = "CatalogLensGlobalSettings", storages = [Storage("cataloglens.xml")])
class CatalogLensGlobalSettings : PersistentStateComponent<CatalogLensGlobalSettings.State> {

    class State {
        var useBundledMap: Boolean = true
        var artifactUrlStyle: ArtifactUrlStyle = ArtifactUrlStyle.MAVEN_CENTRAL
        var mappings: MutableMap<String, String> = mutableMapOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): CatalogLensGlobalSettings =
            ApplicationManager.getApplication().getService(CatalogLensGlobalSettings::class.java)
    }
}
