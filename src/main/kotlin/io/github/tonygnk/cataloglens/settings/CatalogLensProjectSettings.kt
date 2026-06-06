package io.github.tonygnk.cataloglens.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(name = "CatalogLensProjectSettings", storages = [Storage("cataloglens.xml")])
class CatalogLensProjectSettings : PersistentStateComponent<CatalogLensProjectSettings.State> {

    class State {
        var mappings: MutableMap<String, String> = mutableMapOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(project: Project): CatalogLensProjectSettings =
            project.getService(CatalogLensProjectSettings::class.java)
    }
}
