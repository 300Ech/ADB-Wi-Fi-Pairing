package dev.everts.adbwifi.ui.qr

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import dev.everts.adbwifi.AdbWifiBundle
import dev.everts.adbwifi.AdbWifiService
import dev.everts.adbwifi.adb.AdbException
import dev.everts.adbwifi.application.PairOutcome
import dev.everts.adbwifi.domain.PairingResult
import dev.everts.adbwifi.domain.QrPairingRequest
import dev.everts.adbwifi.ui.LocalNetworkBanner
import dev.everts.adbwifi.ui.ResultMessages
import dev.everts.adbwifi.ui.pairing.AdbOperationDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.jetbrains.annotations.Nls
import javax.swing.Action
import javax.swing.ImageIcon
import javax.swing.JComponent

/**
 * Emparejar por QR: muestra el código, espera a que el teléfono lo escanee y anuncie su puerto por mDNS,
 * y empareja solo. Sin mDNS el teléfono nunca aparece; por eso el diálogo ofrece el camino con código.
 */
class QrPairingDialog(private val project: Project?) : DialogWrapper(project) {

    /** Resultado si el emparejamiento terminó (bien o mal tras encontrar el teléfono). */
    var outcome: PairOutcome? = null
        private set

    /** `true` si el usuario prefirió emparejar con código. */
    var switchToCode = false
        private set

    private val request = QrPairingRequest.generate()
    private val statusLabel = JBLabel(AdbWifiBundle.message("qr.waiting"), AnimatedIcon.Default.INSTANCE, JBLabel.LEFT)
    private var job: Job? = null

    init {
        title = AdbWifiBundle.message("qr.title")
        setCancelButtonText(AdbWifiBundle.message("qr.close"))
        init()
        start()
    }

    override fun createActions(): Array<Action> = arrayOf(cancelAction)

    override fun createLeftSideActions(): Array<Action> = arrayOf(
        object : DialogWrapperAction(AdbWifiBundle.message("qr.useCode")) {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                switchToCode = true
                close(CANCEL_EXIT_CODE)
            }
        },
    )

    override fun createCenterPanel(): JComponent = panel {
        val network = AdbWifiService.getInstance().controller.state.value
        if (network.localNetworkBlocked) {
            row { cell(LocalNetworkBanner.create()).align(AlignX.FILL) }
        } else if (network.restartRequired) {
            row { cell(LocalNetworkBanner.createRestart()).align(AlignX.FILL) }
        }
        row { text(AdbWifiBundle.message("qr.instructions")).align(AlignX.FILL) }
        row {
            val image = QrCodeImage.render(request.payload, JBUI.scale(QR_SIZE))
            icon(ImageIcon(image)).align(AlignX.CENTER)
        }
        row { cell(statusLabel) }
        row { comment(AdbWifiBundle.message("qr.mdnsHint")) }
    }

    private fun start() {
        val controller = AdbWifiService.getInstance().controller
        job = AdbWifiService.getInstance().launch {
            val result = try {
                controller.pairWithQr(request, onFound = { status(AdbWifiBundle.message("qr.pairing"), busy = true) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: AdbException) {
                status(AdbOperationDialog.adbProblemText(e), busy = false)
                return@launch
            }
            onUi { finish(result) }
        }
    }

    private fun finish(result: PairOutcome?) {
        if (isDisposed) return
        when {
            result == null -> status(AdbWifiBundle.message("qr.timeout"), busy = false)
            result.pairing is PairingResult.Paired -> {
                outcome = result
                close(OK_EXIT_CODE)
            }
            else -> status(ResultMessages.pair(result), busy = false)
        }
    }

    private fun status(@Nls text: String, busy: Boolean) = onUi {
        statusLabel.text = "<html>$text</html>"
        statusLabel.icon = if (busy) AnimatedIcon.Default.INSTANCE else null
    }

    private fun onUi(block: () -> Unit) =
        ApplicationManager.getApplication().invokeLater({ if (!isDisposed) block() }, ModalityState.any())

    override fun dispose() {
        job?.cancel()
        super.dispose()
    }

    private companion object {
        const val QR_SIZE = 220
    }
}
