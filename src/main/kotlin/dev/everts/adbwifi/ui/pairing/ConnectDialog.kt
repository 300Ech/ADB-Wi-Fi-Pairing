package dev.everts.adbwifi.ui.pairing

import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import dev.everts.adbwifi.AdbWifiBundle
import dev.everts.adbwifi.AdbWifiService
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.ConnectResult
import dev.everts.adbwifi.ui.LocalNetworkBanner
import dev.everts.adbwifi.ui.ResultMessages
import javax.swing.JComponent

/** `adb connect` manual con la IP y el puerto de la pantalla "Depuración inalámbrica". */
class ConnectDialog(project: Project?, endpoint: AdbEndpoint? = null) : AdbOperationDialog<ConnectResult>(project) {

    private var endpointText = endpoint?.toString().orEmpty()

    override val progressText: String get() = AdbWifiBundle.message("connect.progress")

    init {
        title = AdbWifiBundle.message("connect.title")
        setOKButtonText(AdbWifiBundle.message("connect.ok"))
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        val network = AdbWifiService.getInstance().controller.state.value
        if (network.localNetworkBlocked) {
            row { cell(LocalNetworkBanner.create()).align(AlignX.FILL) }
        } else if (network.restartRequired) {
            row { cell(LocalNetworkBanner.createRestart()).align(AlignX.FILL) }
        }
        row {
            text(AdbWifiBundle.message("connect.instructions")).align(AlignX.FILL)
        }
        row(AdbWifiBundle.message("connect.endpoint.label")) {
            textField()
                .bindText(::endpointText)
                .columns(COLUMNS)
                .focused()
                .validationOnApply {
                    validateEndpoint(it.text)
                }
        }
        row { cell(progressLabel) }
    }

    override suspend fun perform(): ConnectResult =
        AdbWifiService.getInstance().controller.connect(requireNotNull(AdbEndpoint.parse(endpointText)))

    override fun isSuccess(result: ConnectResult): Boolean = result is ConnectResult.Connected

    override fun failureText(result: ConnectResult): String =
        ResultMessages.withNetworkHint(ResultMessages.connect(result), AdbEndpoint.parse(endpointText))

    private companion object {
        const val COLUMNS = 22
    }
}
