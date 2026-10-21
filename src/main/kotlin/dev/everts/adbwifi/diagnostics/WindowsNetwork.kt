package dev.everts.adbwifi.diagnostics

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.Base64

/** Perfil del Firewall de Windows. Cada conexión de red tiene uno según su tipo de red. */
enum class FirewallProfile { DOMAIN, PRIVATE, PUBLIC }

/** Una conexión de red de Windows (`Wi-Fi`, `Ethernet`...) y el perfil que le asignó Windows. */
data class NetworkConnection(val name: String, val profile: FirewallProfile)

/** Regla de entrada activa del Firewall de Windows para el adb.exe que usa el plugin. */
data class FirewallRule(val allow: Boolean, val profiles: Set<FirewallProfile>)

/**
 * Lo que Windows dice de la red y del firewall.
 *
 * @param firewallOn perfiles con el Firewall de Windows activado.
 * @param adbRules reglas de entrada de adb.exe; `null` si no se sabe qué adb.exe mirar.
 */
data class WindowsNetworkStatus(
    val connections: List<NetworkConnection>,
    val firewallOn: Set<FirewallProfile>,
    val adbRules: List<FirewallRule>?,
)

/**
 * Las dos causas típicas en Windows de que no se descubra ningún teléfono: la red marcada como
 * "Pública" y el Firewall de Windows bloqueando la entrada a adb.exe (pasa si se canceló su aviso
 * "Permitir acceso"). Las conexiones salientes de `adb pair`/`connect` no las bloquea ninguna de las dos.
 */
fun WindowsNetworkStatus.checks(): List<CheckResult> = buildList {
    val public = connections.filter { it.profile == FirewallProfile.PUBLIC }
    if (public.isNotEmpty()) {
        val fixes = listOf(Fix.OPEN_NETWORK_SETTINGS)
        add(CheckResult(Finding.WINDOWS_PUBLIC_NETWORK, Severity.WARNING, listOf(names(public)), fixes))
    }
    val guarded = connections.filter { it.profile in firewallOn }
    if (adbRules != null && guarded.isNotEmpty()) add(firewallCheck(guarded, adbRules))
}

private fun firewallCheck(guarded: List<NetworkConnection>, rules: List<FirewallRule>): CheckResult {
    // En el Firewall de Windows una regla de bloqueo gana a cualquier regla que permita.
    val blocked = guarded.filter { connection -> rules.any { !it.allow && connection.profile in it.profiles } }
    val notAllowed = guarded.filter { connection -> rules.none { it.allow && connection.profile in it.profiles } }
    val fixes = listOf(Fix.OPEN_FIREWALL_SETTINGS)
    return when {
        blocked.isNotEmpty() ->
            CheckResult(Finding.WINDOWS_FIREWALL_BLOCKS_ADB, Severity.ERROR, listOf(names(blocked)), fixes)
        notAllowed.isNotEmpty() ->
            CheckResult(Finding.WINDOWS_FIREWALL_NO_RULE, Severity.WARNING, listOf(names(notAllowed)), fixes)
        else -> CheckResult(Finding.WINDOWS_FIREWALL_OK, Severity.OK)
    }
}

private fun names(connections: List<NetworkConnection>) = connections.joinToString { it.name }

/**
 * Parser de la salida de `windows/network-status.ps1`: una línea por dato, separada por tabuladores.
 * Los nombres de los enums de PowerShell (`Public`, `Block`...) no se traducen; los de las conexiones sí.
 */
object WindowsNetworkParser {

    fun parse(output: String, withRules: Boolean): WindowsNetworkStatus {
        val lines = output.lineSequence().map { it.trimEnd().split('\t') }.filter { it.size >= FIELDS }.toList()
        fun lines(kind: String) = lines.filter { it[0] == kind }
        val connections = lines("connection").mapNotNull { (_, category, name) ->
            profile(category)?.let { NetworkConnection(name, it) }
        }
        val firewallOn = lines("firewall")
            .filter { (_, _, enabled) -> enabled.equals("True", ignoreCase = true) }
            .mapNotNull { (_, name) -> profile(name) }
            .toSet()
        val rules = lines("rule").map { (_, action, profiles) ->
            FirewallRule(action.equals("Allow", ignoreCase = true), profiles(profiles))
        }
        return WindowsNetworkStatus(connections, firewallOn, rules.takeIf { withRules })
    }

    /** `NetworkCategory` (`Public`, `Private`, `DomainAuthenticated`) o nombre de perfil (`Domain`...). */
    private fun profile(name: String): FirewallProfile? = when (name.trim().lowercase()) {
        "public" -> FirewallProfile.PUBLIC
        "private" -> FirewallProfile.PRIVATE
        "domain", "domainauthenticated" -> FirewallProfile.DOMAIN
        else -> null
    }

    /** `Profile` de una regla: `Any` o una lista como `Private, Public`. */
    private fun profiles(value: String): Set<FirewallProfile> =
        if (value.trim().equals("Any", ignoreCase = true)) {
            FirewallProfile.entries.toSet()
        } else {
            value.split(',').mapNotNull(::profile).toSet()
        }

    /** Tipo de línea y dos valores. */
    private const val FIELDS = 3
}

/**
 * Consulta la red de Windows con `windows/network-status.ps1`, que usa los cmdlets `Get-NetConnectionProfile`,
 * `Get-NetFirewallProfile` y `Get-NetFirewallRule`: devuelven los valores en inglés en cualquier idioma
 * de Windows (`netsh` no). El script va codificado en `-EncodedCommand` para no depender de la
 * política de ejecución ni de cómo se citan los argumentos.
 */
object PowerShellWindowsNetwork {

    private const val TIMEOUT_MS = 15_000
    private const val SCRIPT_RESOURCE = "/windows/network-status.ps1"

    /** Variable de entorno con la ruta de adb.exe que lee el script. */
    private const val ADB_VARIABLE = "ADB_WIFI_ADB"
    private val POWERSHELL =
        "${System.getenv("SystemRoot") ?: "C:\\Windows"}\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"

    /**
     * @param adbPath el adb.exe cuyas reglas de firewall se buscan; `null` si no se encuentra.
     * @return `null` si no se pudo consultar.
     */
    suspend fun status(adbPath: Path?): WindowsNetworkStatus? = withContext(Dispatchers.IO) {
        val encoded = Base64.getEncoder().encodeToString(SCRIPT.toByteArray(Charsets.UTF_16LE))
        val commandLine = GeneralCommandLine(POWERSHELL, "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
            .withCharset(Charsets.UTF_8)
            .withEnvironment(ADB_VARIABLE, adbPath?.toString().orEmpty())
        try {
            val output = ExecUtil.execAndGetOutput(commandLine, TIMEOUT_MS)
            if (output.isTimeout || output.exitCode != 0) {
                thisLogger().info("PowerShell network query failed: ${output.exitCode} ${output.stderr}")
                null
            } else {
                WindowsNetworkParser.parse(output.stdout, withRules = adbPath != null)
            }
        } catch (e: ExecutionException) {
            thisLogger().info("Could not run PowerShell", e)
            null
        }
    }

    private val SCRIPT: String by lazy {
        requireNotNull(javaClass.getResource(SCRIPT_RESOURCE)) { SCRIPT_RESOURCE }.readText()
    }
}
