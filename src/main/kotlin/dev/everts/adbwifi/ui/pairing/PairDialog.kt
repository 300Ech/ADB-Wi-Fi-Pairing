package dev.everts.adbwifi.ui.pairing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import dev.everts.adbwifi.AdbWifiBundle
import dev.everts.adbwifi.AdbWifiService
import dev.everts.adbwifi.application.AdbWifiState
import dev.everts.adbwifi.application.PairOutcome
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.PairingCode
import dev.everts.adbwifi.domain.PairingResult
import dev.everts.adbwifi.ui.ResultMessages
import javax.swing.JComponent

/**
 * Emparejar con código. Sirve tanto para el modo manual (el usuario escribe IP:puerto) como para el
 * asistido (llega con la dirección del servicio mDNS de pairing ya rellenada).
 *
 * El puerto de conexión es opcional: adb suele conectar solo por mDNS después de emparejar; si mDNS no
 * funciona en la red, con ese puerto el plugin conecta igualmente.
 */
class PairDialog(
    project: Project?,
    pairingEndpoint: AdbEndpoint? = null,
    private val deviceName: String? = null,
) : AdbOperationDialog<PairOutcome>(project) {

    private var endpointText = pairingEndpoint?.toString().orEmpty()
    private var codeText = ""
    private var connectText = ""
    private val prefilled = pairingEndpoint != null
    private lateinit var endpointField: JBTextField

    /** Teléfonos con el diálogo de vinculación abierto, actualizados mientras el diálogo está abierto. */
    private val discovered = CollectionComboBoxModel<Discovered>()
    private val controller = AdbWifiService.getInstance().controller
    private val releaseFastPolling = controller.requestFastPolling()
    private val stateJob = AdbWifiService.getInstance().launch {
        controller.state.collect { state ->
            ApplicationManager.getApplication().invokeLater({ showDiscovered(state) }, ModalityState.any())
        }
    }

    private data class Discovered(val name: String, val endpoint: AdbEndpoint) {
        override fun toString() = "$name ($endpoint)"
    }

    override val progressText: String get() = AdbWifiBundle.message("pair.progress")

    init {
        controller.start()
        title = deviceName?.let { AdbWifiBundle.message("pair.title.device", it) }
            ?: AdbWifiBundle.message("pair.title")
        setOKButtonText(AdbWifiBundle.message("pair.ok"))
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            text(AdbWifiBundle.message("pair.instructions")).align(AlignX.FILL)
        }
        if (!prefilled) {
            row(AdbWifiBundle.message("pair.discovered.label")) {
                comboBox(discovered)
                    .align(AlignX.FILL)
                    .comment(AdbWifiBundle.message("pair.discovered.comment"))
                    .onChanged { combo ->
                        (combo.selectedItem as? Discovered)?.let { endpointField.text = it.endpoint.toString() }
                    }
            }
        }
        row(AdbWifiBundle.message("pair.endpoint.label")) {
            val field = textField()
                .bindText(::endpointText)
                .columns(COLUMNS)
                .comment(AdbWifiBundle.message("pair.endpoint.comment"))
                .validationOnApply {
                    validateEndpoint(it.text)
                }
            endpointField = field.component
            if (!prefilled) field.focused()
        }
        row(AdbWifiBundle.message("pair.code.label")) {
            val field = textField()
                .bindText(::codeText)
                .columns(CODE_COLUMNS)
                .validationOnApply {
                    if (PairingCode.parse(it.text) == null) error(AdbWifiBundle.message("validation.code")) else null
                }
            if (prefilled) field.focused()
        }
        row(AdbWifiBundle.message("pair.connect.label")) {
            textField()
                .bindText(::connectText)
                .columns(COLUMNS)
                .comment(AdbWifiBundle.message("pair.connect.comment"))
                .validationOnApply { if (it.text.isBlank()) null else validateEndpoint(it.text) }
        }
        row { cell(progressLabel) }
    }

    private fun showDiscovered(state: AdbWifiState) {
        if (isDisposed) return
        val items = state.entries.mapNotNull { entry ->
            entry.pairingService?.let { Discovered(entry.name, it.endpoint) }
        }
        if (items == discovered.items) return
        val selected = discovered.selected
        discovered.replaceAll(items)
        discovered.selectedItem = selected?.takeIf { it in items }
        if (items.size == 1 && endpointField.text.isBlank()) endpointField.text = items.single().endpoint.toString()
    }

    override fun dispose() {
        stateJob.cancel()
        releaseFastPolling()
        super.dispose()
    }

    override suspend fun perform(): PairOutcome = AdbWifiService.getInstance().controller.pair(
        endpoint = requireNotNull(AdbEndpoint.parse(endpointText)),
        code = requireNotNull(PairingCode.parse(codeText)),
        connectEndpoint = AdbEndpoint.parse(connectText),
    )

    override fun isSuccess(result: PairOutcome): Boolean = result.pairing is PairingResult.Paired

    override fun failureText(result: PairOutcome): String = ResultMessages.pair(result)

    private companion object {
        const val COLUMNS = 22
        const val CODE_COLUMNS = 8
    }
}
