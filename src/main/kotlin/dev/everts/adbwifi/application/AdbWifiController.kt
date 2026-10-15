package dev.everts.adbwifi.application

import dev.everts.adbwifi.adb.AdbException
import dev.everts.adbwifi.adb.AdbGateway
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.AdbInstanceName
import dev.everts.adbwifi.domain.ConnectResult
import dev.everts.adbwifi.domain.Device
import dev.everts.adbwifi.domain.DeviceEntry
import dev.everts.adbwifi.domain.DeviceIdentity
import dev.everts.adbwifi.domain.DeviceListBuilder
import dev.everts.adbwifi.domain.DeviceState
import dev.everts.adbwifi.domain.EntryStatus
import dev.everts.adbwifi.domain.MdnsService
import dev.everts.adbwifi.domain.MdnsServiceType
import dev.everts.adbwifi.domain.PairedDevice
import dev.everts.adbwifi.domain.PairedDevices
import dev.everts.adbwifi.domain.PairingCode
import dev.everts.adbwifi.domain.PairingResult
import dev.everts.adbwifi.domain.QrPairingRequest
import dev.everts.adbwifi.domain.ReconnectAttempt
import dev.everts.adbwifi.domain.ReconnectPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Lo que la UI necesita pintar. */
data class AdbWifiState(
    val entries: List<DeviceEntry> = emptyList(),
    /** Servicios mDNS de la última consulta, incluidos los de pairing por QR que no forman fila. */
    val services: List<MdnsService> = emptyList(),
    /** Mensaje técnico cuando adb no se puede usar (no encontrado, no responde). */
    val adbProblem: String? = null,
    /** Filas con una operación en curso. */
    val busyKeys: Set<String> = emptySet(),
    /** macOS no deja a Android Studio usar la red local: hay que darle el permiso (una vez). */
    val localNetworkBlocked: Boolean = false,
    /** Último fallo de conexión por fila; `AUTHENTICATION_FAILED` significa "volver a emparejar". */
    val failures: Map<String, AdbFailureReason> = emptyMap(),
    val loaded: Boolean = false,
)

/** Qué pasó después de emparejar: adb suele conectar solo por mDNS; si no, hace falta el puerto de conexión. */
sealed interface PostPairing {
    data class Connected(val endpoint: AdbEndpoint?) : PostPairing

    data class Failed(val result: ConnectResult.Failed) : PostPairing

    /** No apareció por mDNS: el usuario debe conectar con la IP y el puerto de "Depuración inalámbrica". */
    data object NotFound : PostPairing
}

data class PairOutcome(val pairing: PairingResult, val connection: PostPairing?)

/**
 * Casos de uso del plugin sobre un [AdbGateway]: refrescar el estado, emparejar, conectar,
 * desconectar, olvidar y reconectar automáticamente. No depende de la plataforma, para poder probarlo
 * con un gateway falso; `AdbWifiService` lo crea con el scope de la IDE.
 *
 * @param gatewayProvider devuelve el gateway o lanza [AdbException] si adb no está disponible.
 * @param probes reloj y comprobaciones del entorno, sustituibles en tests.
 */
@Suppress("TooManyFunctions")
class AdbWifiController(
    private val scope: CoroutineScope,
    private val gatewayProvider: () -> AdbGateway,
    private val store: PairedDevicesStore,
    private val settings: () -> ControllerSettings,
    private val notifier: AdbWifiNotifier,
    private val probes: ControllerProbes = ControllerProbes(),
) {
    private val _state = MutableStateFlow(AdbWifiState())
    val state: StateFlow<AdbWifiState> = _state.asStateFlow()

    private val started = AtomicBoolean(false)
    private val fastPollingRequests = AtomicInteger(0)
    private val refreshLock = Mutex()
    private val identities = ConcurrentHashMap<String, DeviceIdentity>()
    private val attempts = ConcurrentHashMap<String, ReconnectAttempt>()

    /** Filas que el usuario desconectó a mano: no se reconectan hasta que conecte o cambie de sesión. */
    private val suppressed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val failures = ConcurrentHashMap<String, AdbFailureReason>()
    private val availableSince = ConcurrentHashMap<String, Duration>()
    private val networkLock = Mutex()

    @Volatile
    private var localNetworkBlocked = false
    private var lastNetworkCheck: Duration? = null

    /** Arranca el ciclo de refresco y reconexión. Llamarlo varias veces no hace nada más. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            while (isActive) {
                try {
                    checkLocalNetwork()
                    refresh()
                    if (settings().autoReconnect) reconnectOnce()
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                    // Un fallo inesperado de un ciclo no debe parar la reconexión de los siguientes.
                    _state.update { it.copy(adbProblem = e.message ?: e.javaClass.simpleName) }
                }
                delay(if (fastPollingRequests.get() > 0) FAST_POLL_INTERVAL else settings().pollInterval)
            }
        }
    }

    /**
     * Sondea cada segundo mientras haya un diálogo de emparejamiento abierto: el puerto de pairing del
     * teléfono solo existe mientras su diálogo está abierto. Llamar a la función devuelta para terminar.
     */
    fun requestFastPolling(): () -> Unit {
        fastPollingRequests.incrementAndGet()
        val released = AtomicBoolean(false)
        return { if (released.compareAndSet(false, true)) fastPollingRequests.decrementAndGet() }
    }

    /**
     * Vuelve a leer `devices` y `mdns services` y recalcula las filas.
     * Los errores quedan en [AdbWifiState.adbProblem].
     */
    suspend fun refresh() {
        refreshLock.withLock {
            try {
                val gateway = gatewayProvider()
                val devices = gateway.devices()
                val services = gateway.mdnsServices()
                learnIdentities(gateway, devices)
                val connectEndpoints = services
                    .filter { it.type == MdnsServiceType.TLS_CONNECT }
                    .mapNotNull { service -> service.deviceSerial?.let { it to service.endpoint } }
                    .toMap()
                store.update { PairedDevices.reconcile(it, devices, identities, connectEndpoints) }
                val entries = DeviceListBuilder.build(store.devices, devices, services, identities)
                reportLost(_state.value.entries, entries)
                reportPairingRequests(_state.value, entries)
                updateFailures(entries)
                updateAvailability(entries)
                _state.update {
                    it.copy(
                        entries = entries,
                        services = services,
                        failures = failures.toMap(),
                        adbProblem = null,
                        loaded = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AdbException) {
                _state.update { it.copy(adbProblem = e.message, loaded = true) }
            }
        }
    }

    /** Empareja y espera a que adb conecte solo; si se indica [connectEndpoint], conecta ahí. */
    suspend fun pair(endpoint: AdbEndpoint, code: PairingCode, connectEndpoint: AdbEndpoint? = null): PairOutcome {
        val pairing = pairRecovering(endpoint, code)
        if (pairing !is PairingResult.Paired) return PairOutcome(pairing, null)
        val gateway = gatewayProvider()

        val id = pairing.guid?.let(AdbInstanceName::serialOf) ?: endpoint.host
        store.update { PairedDevices.upsert(it, PairedDevice(id, id, connectEndpoint)) }
        suppressed -= id
        val connection = if (connectEndpoint != null) {
            when (val result = connect(connectEndpoint)) {
                is ConnectResult.Connected -> PostPairing.Connected(result.endpoint)
                is ConnectResult.Failed -> PostPairing.Failed(result)
            }
        } else {
            awaitConnection(gateway, id)
        }
        refresh()
        return PairOutcome(pairing, connection)
    }

    /**
     * Emparejamiento por QR: espera a que el teléfono que escaneó [request] anuncie su puerto de pairing
     * con ese nombre y empareja. Devuelve `null` si no aparece en [timeout] (mDNS bloqueado o QR sin escanear).
     *
     * @param onFound se llama al encontrar el teléfono, antes del intercambio de claves (unos segundos).
     */
    suspend fun pairWithQr(
        request: QrPairingRequest,
        timeout: Duration = QR_TIMEOUT,
        onFound: (AdbEndpoint) -> Unit = {},
    ): PairOutcome? {
        val release = requestFastPolling()
        try {
            val gateway = gatewayProvider()
            val service = withTimeoutOrNull(timeout) { awaitPairingService(gateway, request.serviceName) }
                ?: return null
            onFound(service.endpoint)
            return pair(service.endpoint, request.secret)
        } finally {
            release()
        }
    }

    private suspend fun awaitPairingService(gateway: AdbGateway, name: String): MdnsService {
        while (true) {
            val service = gateway.mdnsServices().firstOrNull {
                it.type == MdnsServiceType.PAIRING && it.instanceName == name
            }
            if (service != null) return service
            delay(FAST_POLL_INTERVAL)
        }
    }

    /**
     * `adb pair` con la causa real del fallo y, si el servidor adb se quedó sin red pero Android Studio
     * sí llega al teléfono, reinicia adb y reintenta una vez.
     */
    private suspend fun pairRecovering(endpoint: AdbEndpoint, code: PairingCode): PairingResult {
        val first = pairExplained(endpoint, code)
        if (first !is PairingResult.Failed || !adbServerCutOff(first.reason, endpoint)) return first
        restartAdb()
        val retry = pairExplained(endpoint, code)
        val stillCutOff = retry is PairingResult.Failed && adbServerCutOff(retry.reason, endpoint)
        return if (stillCutOff) {
            retry.copy(reason = AdbFailureReason.ADB_SERVER_NO_NETWORK)
        } else {
            retry
        }
    }

    private suspend fun pairExplained(endpoint: AdbEndpoint, code: PairingCode): PairingResult {
        val since = probes.wallClock()
        val result = gatewayProvider().pair(endpoint, code)
        val actual = (result as? PairingResult.Failed)
            ?.takeIf { it.reason in VAGUE_PAIRING_FAILURES }
            ?.let { runCatching { probes.pairingFailure(endpoint, since) }.getOrNull() }
        return if (actual != null) result.copy(reason = actual) else result
    }

    /** `No route to host` desde adb mientras Android Studio sí llega al teléfono. */
    private suspend fun adbServerCutOff(reason: AdbFailureReason, endpoint: AdbEndpoint): Boolean =
        reason == AdbFailureReason.NO_ROUTE_TO_HOST && probes.reachable(endpoint)

    /**
     * Comprueba si macOS deja a Android Studio usar la red local: una vez por minuto, o cada pocos
     * segundos mientras está bloqueado para notar enseguida que el usuario dio el permiso. Al pasar de
     * bloqueado a permitido reinicia adb: el servidor arrancado sin permiso sigue ciego aunque luego se dé.
     *
     * @param force comprueba ya, sin esperar al intervalo (botón "Comprobar de nuevo").
     */
    suspend fun checkLocalNetwork(force: Boolean = false) {
        val wasBlocked = networkLock.withLock {
            val now = probes.now()
            val interval = if (localNetworkBlocked) BLOCKED_RECHECK_INTERVAL else NETWORK_CHECK_INTERVAL
            if (!force && lastNetworkCheck?.let { now - it < interval } == true) return
            lastNetworkCheck = now
            val previous = localNetworkBlocked
            localNetworkBlocked = probes.localNetworkBlocked() == true
            _state.update { it.copy(localNetworkBlocked = localNetworkBlocked) }
            previous
        }
        if (wasBlocked && !localNetworkBlocked) restartAdb()
    }

    /**
     * `adb connect` y refresco. Si conecta, la fila vuelve a ser candidata a reconexión automática.
     * Si el servidor adb se quedó sin red pero Android Studio llega al teléfono, reinicia adb y reintenta.
     */
    suspend fun connect(endpoint: AdbEndpoint): ConnectResult {
        var result = gatewayProvider().connect(endpoint)
        if (result is ConnectResult.Failed && adbServerCutOff(result.reason, endpoint)) {
            restartAdb()
            result = gatewayProvider().connect(endpoint)
            if (result is ConnectResult.Failed && adbServerCutOff(result.reason, endpoint)) {
                result = result.copy(reason = AdbFailureReason.ADB_SERVER_NO_NETWORK)
            }
        }
        val keys = _state.value.entries
            .filter { it.endpoint == endpoint || it.connectService?.endpoint == endpoint }
            .map { it.key }
        when (result) {
            is ConnectResult.Connected -> keys.forEach { suppressed -= it; failures -= it }
            is ConnectResult.Failed -> keys.forEach { failures[it] = result.reason }
        }
        refresh()
        return result
    }

    /** Conecta una fila usando el puerto que anuncia por mDNS o, si no, su última dirección. */
    suspend fun connect(entry: DeviceEntry): ConnectResult? {
        val endpoint = entry.connectService?.endpoint ?: entry.paired?.lastEndpoint ?: return null
        return busy(entry.key) { connect(endpoint) }
    }

    /** Cierra la conexión y evita que la reconexión automática la reabra enseguida. */
    suspend fun disconnect(entry: DeviceEntry): Boolean {
        suppressed += entry.key
        val endpoint = entry.device?.endpoint ?: return false
        return busy(entry.key) { gatewayProvider().disconnect(endpoint) }.also { refresh() }
    }

    /** Desconecta y borra el dispositivo de la lista de emparejados del plugin. */
    suspend fun forget(entry: DeviceEntry) {
        if (entry.isConnected) disconnect(entry)
        suppressed += entry.key
        store.update { devices -> devices.filterNot { it.id == entry.key } }
        refresh()
    }

    fun setAutoReconnect(entry: DeviceEntry, enabled: Boolean) {
        val paired = entry.paired ?: PairedDevice(entry.key, entry.name, entry.endpoint)
        store.update { devices -> devices.filterNot { it.id == paired.id } + paired.copy(autoReconnect = enabled) }
        scope.launch { refresh() }
    }

    fun rename(entry: DeviceEntry, name: String) {
        val paired = entry.paired ?: PairedDevice(entry.key, name, entry.endpoint)
        store.update { devices -> devices.filterNot { it.id == paired.id } + paired.copy(name = name.trim()) }
        scope.launch { refresh() }
    }

    /** Reinicia el servidor adb (con [environment] opcional) y refresca. Cierra todas las conexiones. */
    suspend fun restartAdb(environment: Map<String, String> = emptyMap()) {
        gatewayProvider().restartServer(environment)
        identities.clear()
        refresh()
    }

    /** Modo clásico: `tcpip 5555` sobre un dispositivo USB y `connect` a su IP de la Wi-Fi. */
    suspend fun enableTcpip(entry: DeviceEntry, port: Int = DEFAULT_TCPIP_PORT): ConnectResult? {
        val device = entry.device ?: return null
        return busy(entry.key) {
            val gateway = gatewayProvider()
            val address = gateway.wifiAddress(device.serial) ?: return@busy null
            gateway.tcpip(device.serial, port)
            delay(TCPIP_RESTART_DELAY)
            connect(AdbEndpoint(address, port))
        }
    }

    /** Un ciclo de reconexión automática. Público para los tests; el bucle de [start] lo llama. */
    suspend fun reconnectOnce() {
        val entries = _state.value.entries
        entries.filter { it.status == EntryStatus.NOT_SEEN }.forEach { suppressed -= it.key }
        val candidates = entries.filterNot { it.key in suppressed }
        val targets = ReconnectPlanner.plan(candidates, attempts, probes.now(), availableSince)
        for (target in targets) {
            val entry = entries.first { it.key == target.key }
            val result = try {
                if (entry.status == EntryStatus.AVAILABLE || probes.reachable(target.endpoint)) {
                    busy(target.key) { gatewayProvider().connect(target.endpoint) }
                } else {
                    ConnectResult.Failed(AdbFailureReason.TIMED_OUT, "")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AdbException) {
                _state.update { it.copy(adbProblem = e.message) }
                return
            }
            recordAttempt(entry, target.endpoint, result)
        }
        if (targets.isNotEmpty()) refresh()
    }

    private fun recordAttempt(entry: DeviceEntry, endpoint: AdbEndpoint, result: ConnectResult) {
        val previous = attempts[entry.key]?.takeIf { it.endpoint == endpoint }
        when (result) {
            is ConnectResult.Connected -> {
                attempts[entry.key] = ReconnectAttempt(endpoint, probes.now(), failures = 0, reason = null)
                failures -= entry.key
                notifier.notify(AdbWifiEvent.Reconnected(entry.name, endpoint))
            }
            is ConnectResult.Failed -> {
                val count = (previous?.failures ?: 0) + 1
                attempts[entry.key] = ReconnectAttempt(endpoint, probes.now(), count, result.reason)
                failures[entry.key] = result.reason
                val newlyRejected = previous?.reason != AdbFailureReason.AUTHENTICATION_FAILED
                if (result.reason == AdbFailureReason.AUTHENTICATION_FAILED && newlyRejected) {
                    notifier.notify(AdbWifiEvent.NeedsRepair(entry.name, endpoint))
                }
            }
        }
    }

    /** Tras emparejar, espera a que adb conecte solo o conecta al puerto que anuncie el dispositivo. */
    private suspend fun awaitConnection(gateway: AdbGateway, id: String): PostPairing {
        var outcome: PostPairing = PostPairing.NotFound
        repeat(AUTO_CONNECT_POLLS) {
            outcome = checkConnection(gateway, id) ?: outcome
            if (outcome is PostPairing.Connected) return outcome
            delay(AUTO_CONNECT_INTERVAL)
        }
        return outcome
    }

    private suspend fun checkConnection(gateway: AdbGateway, id: String): PostPairing? {
        val connected = gateway.devices().any {
            it.state == DeviceState.DEVICE && AdbInstanceName.serialOf(it.serial) == id
        }
        val service = gateway.mdnsServices().firstOrNull {
            it.type == MdnsServiceType.TLS_CONNECT && it.deviceSerial == id
        }
        return when {
            connected -> PostPairing.Connected(service?.endpoint)
            service == null -> null
            else -> when (val result = gateway.connect(service.endpoint)) {
                is ConnectResult.Connected -> PostPairing.Connected(result.endpoint)
                is ConnectResult.Failed -> PostPairing.Failed(result)
            }
        }
    }

    private suspend fun learnIdentities(gateway: AdbGateway, devices: List<Device>) {
        identities.keys.retainAll(devices.map { it.serial }.toSet())
        for (device in devices.filter { it.state == DeviceState.DEVICE && !identities.containsKey(it.serial) }) {
            try {
                identities[device.serial] = DeviceIdentity(
                    serialNumber = gateway.getProp(device.serial, "ro.serialno"),
                    model = gateway.getProp(device.serial, "ro.product.model"),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: AdbException) {
                // El dispositivo puede desconectarse entre `devices` y `getprop`; se reintenta en el próximo ciclo.
            }
        }
    }

    private fun updateAvailability(entries: List<DeviceEntry>) {
        val available = entries.filter { it.status == EntryStatus.AVAILABLE }.map { it.key }.toSet()
        availableSince.keys.retainAll(available)
        available.forEach { availableSince.putIfAbsent(it, probes.now()) }
    }

    /** Los fallos solo importan mientras el dispositivo no esté conectado; se suman los rechazos del log. */
    private fun updateFailures(entries: List<DeviceEntry>) {
        val connected = entries.filter { it.isConnected }.map { it.key }.toSet()
        failures.keys.removeAll(connected)
        val rejected = runCatching(probes.rejectedSerials).getOrDefault(emptySet())
        entries.filter { it.key in rejected && it.key !in connected }
            .forEach { failures[it.key] = AdbFailureReason.AUTHENTICATION_FAILED }
    }

    /** Avisa de teléfonos que abren "Vincular con código", salvo si el usuario ya está en un diálogo de pairing. */
    private fun reportPairingRequests(before: AdbWifiState, after: List<DeviceEntry>) {
        if (!before.loaded || fastPollingRequests.get() > 0) return
        val known = before.entries.mapNotNull { it.pairingService?.instanceName }.toSet()
        after.filter { it.pairingService != null && it.pairingService.instanceName !in known }
            .forEach { notifier.notify(AdbWifiEvent.PairingAvailable(it.name, it.pairingService!!.endpoint)) }
    }

    private fun reportLost(before: List<DeviceEntry>, after: List<DeviceEntry>) {
        val nowByKey = after.associateBy { it.key }
        before.filter { it.status == EntryStatus.CONNECTED && it.paired != null && it.key !in suppressed }
            .filter { nowByKey[it.key]?.status == EntryStatus.NOT_SEEN }
            .forEach { notifier.notify(AdbWifiEvent.Lost(it.name)) }
    }

    private suspend fun <T> busy(key: String, block: suspend () -> T): T {
        _state.update { it.copy(busyKeys = it.busyKeys + key) }
        try {
            return block()
        } finally {
            _state.update { it.copy(busyKeys = it.busyKeys - key) }
        }
    }

    companion object {
        const val DEFAULT_TCPIP_PORT = 5555
        private const val AUTO_CONNECT_POLLS = 10
        private val AUTO_CONNECT_INTERVAL = 1.seconds
        private val TCPIP_RESTART_DELAY = 2.seconds
        private val FAST_POLL_INTERVAL = 1.seconds
        private val QR_TIMEOUT = 3.minutes
        private val NETWORK_CHECK_INTERVAL = 1.minutes
        private val BLOCKED_RECHECK_INTERVAL = 5.seconds
        private val VAGUE_PAIRING_FAILURES = setOf(AdbFailureReason.PROTOCOL_FAULT, AdbFailureReason.UNKNOWN)
    }
}
