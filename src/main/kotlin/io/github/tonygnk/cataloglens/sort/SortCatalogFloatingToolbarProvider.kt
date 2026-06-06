package io.github.tonygnk.cataloglens.sort

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import io.github.tonygnk.cataloglens.psi.VersionCatalogDetector

class SortCatalogFloatingToolbarProvider :
    AbstractFloatingToolbarProvider("CatalogLens.SortToolbarGroup") {

    override fun isApplicable(dataContext: DataContext): Boolean = runReadAction {
        VersionCatalogDetector.isVersionCatalog(dataContext.getData(CommonDataKeys.PSI_FILE))
    }
}
