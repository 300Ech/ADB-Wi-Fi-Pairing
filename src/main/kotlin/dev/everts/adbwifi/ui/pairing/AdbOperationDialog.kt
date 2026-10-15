package dev.everts.adbwifi.ui.pairing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import dev.everts.adbwifi.AdbWifiService
import dev.everts.adbwifi.adb.AdbException
import dev.everts.adbwifi.adb.AdbNotFoundException
import dev.everts.adbwifi.AdbWifiBundle
import dev.everts.adbwifi.domain.AdbEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.jetbrains.annotations.Nls

/**
 * Diálogo que ejecuta una operación de adb al pulsar OK y se queda abierto si falla, mostrando qué
 * hacer. Así el usuario corrige el puerto o el código sin volver a escribir todo.
 */
abstract class AdbOperationDialog<T : Any>(project: Project?) : DialogWrapper(project) {

    /** Resultado de la operación si terminó bien (el diálogo se cerró con OK). */
    var result: T? = null
        private set

    protected val progressLabel = JBLabel()
    private var job: Job? = null

    @get:Nls
    protected abstract val progressText: String

    /** Se ejecuta fuera del EDT. Los campos ya se aplicaron y validaron. */
    protected abstract suspend fun perform(): T

    protected abstract fun isSuccess(result: T): Boolean

    @Nls
    protected abstract fun failureText(result: T): String

    override fun doOKAction() {
        if (job?.isActive == true) return
        applyFields()
        isOKActionEnabled = false
        setErrorText(null)
        progressLabel.text = progressText
        progressLabel.icon = AnimatedIcon.Default.INSTANCE
        job = AdbWifiService.getInstance().launch {
            val outcome = try {
                Result.success(perform())
            } catch (e: CancellationException) {
                throw e
            } catch (e: AdbException) {
                Result.failure(e)
            }
            ApplicationManager.getApplication().invokeLater({ finish(outcome) }, ModalityState.any())
        }
    }

    private fun finish(outcome: Result<T>) {
        if (isDisposed) return
        progressLabel.text = ""
        progressLabel.icon = null
        isOKActionEnabled = true
        val value = outcome.getOrNull()
        when {
            value != null && isSuccess(value) -> {
                result = value
                close(OK_EXIT_CODE)
            }
            value != null -> setErrorText(html(failureText(value)))
            else -> setErrorText(html(adbProblemText(outcome.exceptionOrNull())))
        }
    }

    @Nls
    private fun html(@Nls text: String): String = "<html>$text</html>"

    /** Validación común de los campos `host:puerto`. */
    protected fun ValidationInfoBuilder.validateEndpoint(text: String): ValidationInfo? =
        if (AdbEndpoint.parse(text) == null) error(AdbWifiBundle.message("validation.endpoint")) else null

    override fun dispose() {
        job?.cancel()
        super.dispose()
    }

    companion object {
        @Nls
        fun adbProblemText(error: Throwable?): String = when (error) {
            is AdbNotFoundException -> AdbWifiBundle.message("problem.adbNotFound")
            else -> AdbWifiBundle.message("problem.adbFailed", error?.message.orEmpty())
        }
    }
}
