package io.github.tonygnk.cataloglens.settings

import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import javax.swing.JPanel

internal class MappingRow(var key: String = "", var urls: String = "")

internal class MappingsTablePanel {

    private val model = ListTableModel<MappingRow>(KeyColumn, UrlsColumn)
    private val table = TableView(model)

    val component: JPanel = ToolbarDecorator.createDecorator(table)
        .setAddAction { model.addRow(MappingRow()) }
        .setRemoveAction {
            table.selectedRows.sortedDescending().forEach { model.removeRow(it) }
        }
        .disableUpDownActions()
        .createPanel()

    fun reset(mappings: Map<String, String>) {
        model.items = mappings.entries.map { MappingRow(it.key, it.value) }.toMutableList()
    }

    fun commitEdits() {
        if (table.isEditing) table.cellEditor.stopCellEditing()
    }

    fun current(): Map<String, String> {
        return model.items
            .filter { it.key.isNotBlank() && it.urls.isNotBlank() }
            .associate { it.key.trim() to it.urls.trim() }
    }

    private object KeyColumn : ColumnInfo<MappingRow, String>("Key (group:artifact, plugin ID, or group prefix)") {
        override fun valueOf(item: MappingRow): String = item.key
        override fun setValue(item: MappingRow, value: String) { item.key = value }
        override fun isCellEditable(item: MappingRow): Boolean = true
    }

    private object UrlsColumn : ColumnInfo<MappingRow, String>("URLs (comma or space separated)") {
        override fun valueOf(item: MappingRow): String = item.urls
        override fun setValue(item: MappingRow, value: String) { item.urls = value }
        override fun isCellEditable(item: MappingRow): Boolean = true
    }
}
