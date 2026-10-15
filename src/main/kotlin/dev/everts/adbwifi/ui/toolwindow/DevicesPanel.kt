package dev.everts.adbwifi.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.components.BorderLayoutPanel
import dev.everts.adbwifi.AdbWifiBundle
import dev.everts.adbwifi.AdbWifiService
import dev.everts.adbwifi.application.AdbWifiState
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.DeviceEntry
import dev.everts.adbwifi.domain.EntryStatus
import dev.everts.adbwifi.ui.AdbWifiDataKeys
import dev.everts.adbwifi.ui.LocalNetworkBanner
import dev.everts.adbwifi.ui.ResultMessages
import dev.everts.adbwifi.ui.actions.AdbWifiActionIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.event.MouseEvent
import javax.swing.JTable
import javax.swing.ListSelectionModel

/** Lista de dispositivos del tool window. Solo pinta [AdbWifiState]; las acciones van por el controlador. */
class DevicesPanel : SimpleToolWindowPanel(true, true), UiDataProvider, Disposable {

    private val model = ListTableModel<DeviceEntry>(NameColumn, AddressColumn(), StatusColumn())
    private val table = TableView(model)
    private val statusLabel = JBLabel().apply { border = JBUI.Borders.empty(STATUS_PADDING_V, STATUS_PADDING_H) }
    private var busyKeys: Set<String> = emptySet()
    private var rejectedKeys: Set<String> = emptySet()

    /** Paso de configuración de macOS, visible solo mientras falta el permiso de red local. */
    private val networkBanner = LocalNetworkBanner.create(onCheckAgain = {
        val service = AdbWifiService.getInstance()
        service.launch {
            service.controller.checkLocalNetwork(force = true)
            service.controller.refresh()
        }
    }).apply { isVisible = false }

    init {
        table.setShowGrid(false)
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.emptyText.text = AdbWifiBundle.message("devices.empty")
        table.emptyText.appendSecondaryText(
            AdbWifiBundle.message("devices.empty.pair"),
            SimpleTextAttributes.LINK_ATTRIBUTES,
        ) { invokeAction(AdbWifiActionIds.PAIR_MANUALLY) }
        table.emptyText.appendSecondaryText("  ·  ", SimpleTextAttributes.GRAYED_ATTRIBUTES, null)
        table.emptyText.appendSecondaryText(
            AdbWifiBundle.message("devices.empty.qr"),
            SimpleTextAttributes.LINK_ATTRIBUTES,
        ) { invokeAction(AdbWifiActionIds.PAIR_WITH_QR) }
        PopupHandler.installPopupMenu(table, AdbWifiActionIds.DEVICE_POPUP, ActionPlaces.TOOLWINDOW_POPUP)
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val action = when (table.selectedObject?.status) {
                    EntryStatus.PAIRING -> AdbWifiActionIds.PAIR_DISCOVERED
                    EntryStatus.AVAILABLE, EntryStatus.NOT_SEEN -> AdbWifiActionIds.CONNECT
                    else -> null
                }
                action?.let(::invokeAction)
                return action != null
            }
        }.installOn(table)

        val toolbarGroup = ActionManager.getInstance().getAction(AdbWifiActionIds.TOOLBAR) as ActionGroup
        val toolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, toolbarGroup, true)
        toolbar.targetComponent = this
        setToolbar(toolbar.component)
        setContent(
            BorderLayoutPanel()
                .addToTop(networkBanner)
                .addToCenter(ScrollPaneFactory.createScrollPane(table, true))
                .addToBottom(statusLabel),
        )

        val service = AdbWifiService.getInstance()
        service.controller.start()
        val job = service.launch {
            service.controller.state.collect { state -> withContext(Dispatchers.EDT) { render(state) } }
        }
        Disposer.register(this) { job.cancel() }
    }

    private fun render(state: AdbWifiState) {
        busyKeys = state.busyKeys
        rejectedKeys = state.failures.filterValues { it == AdbFailureReason.AUTHENTICATION_FAILED }.keys
        val selectedKey = table.selectedObject?.key
        model.items = state.entries
        val selectedRow = state.entries.indexOfFirst { it.key == selectedKey }
        if (selectedRow >= 0) table.selectionModel.setSelectionInterval(selectedRow, selectedRow)
        statusLabel.text = when {
            state.adbProblem != null -> AdbWifiBundle.message("devices.adbProblem", state.adbProblem)
            !state.loaded -> AdbWifiBundle.message("devices.loading")
            else -> AdbWifiBundle.message("devices.summary", state.entries.count { it.status == EntryStatus.CONNECTED })
        }
        networkBanner.isVisible = state.localNetworkBlocked
    }

    private fun invokeAction(id: String) {
        val action = ActionManager.getInstance().getAction(id) ?: return
        ActionManager.getInstance().tryToExecute(action, null, table, ActionPlaces.TOOLWINDOW_CONTENT, true)
    }

    override fun uiDataSnapshot(sink: DataSink) {
        sink[AdbWifiDataKeys.SELECTED_ENTRY] = table.selectedObject
    }

    override fun dispose() = Unit

    private object NameColumn : ColumnInfo<DeviceEntry, String>(AdbWifiBundle.message("devices.column.name")) {
        override fun valueOf(item: DeviceEntry): String = item.name
    }

    private class AddressColumn : ColumnInfo<DeviceEntry, String>(AdbWifiBundle.message("devices.column.address")) {
        override fun valueOf(item: DeviceEntry): String = item.endpoint?.toString() ?: item.device?.serial.orEmpty()
    }

    private inner class StatusColumn : ColumnInfo<DeviceEntry, String>(AdbWifiBundle.message("devices.column.status")) {
        override fun valueOf(item: DeviceEntry): String = when {
            item.key in busyKeys -> AdbWifiBundle.message("status.busy")
            item.key in rejectedKeys && !item.isConnected -> AdbWifiBundle.message("status.rejected")
            else -> ResultMessages.status(item.status)
        }

        override fun getRenderer(item: DeviceEntry) = object : ColoredTableCellRenderer() {
            override fun customizeCellRenderer(
                table: JTable,
                value: Any?,
                selected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int,
            ) {
                val rejected = item.key in rejectedKeys && !item.isConnected
                val style = if (rejected) SimpleTextAttributes.ERROR_ATTRIBUTES else attributes(item.status)
                append(value?.toString().orEmpty(), style)
                if (item.paired?.autoReconnect == false) {
                    append(
                        "  " + AdbWifiBundle.message("devices.autoReconnectOff"),
                        SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES,
                    )
                }
            }
        }
    }

    private companion object {
        const val STATUS_PADDING_V = 4
        const val STATUS_PADDING_H = 8
    }

    private fun attributes(status: EntryStatus): SimpleTextAttributes = when (status) {
        EntryStatus.CONNECTED, EntryStatus.USB -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        EntryStatus.UNAUTHORIZED -> SimpleTextAttributes.ERROR_ATTRIBUTES
        EntryStatus.NOT_SEEN -> SimpleTextAttributes.GRAYED_ATTRIBUTES
        else -> SimpleTextAttributes.REGULAR_ATTRIBUTES
    }
}
