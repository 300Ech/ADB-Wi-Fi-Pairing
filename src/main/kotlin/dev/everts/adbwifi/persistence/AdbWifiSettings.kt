package dev.everts.adbwifi.persistence

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.service
import dev.everts.adbwifi.adb.AdbTimeouts
import dev.everts.adbwifi.application.ControllerSettings
import kotlin.time.Duration.Companion.seconds

/** Preferencias globales del plugin (Settings > Tools > ADB Wi-Fi). */
@Service(Service.Level.APP)
@State(name = "AdbWifiSettings", storages = [Storage(value = "adbWifi.xml", roamingType = RoamingType.DISABLED)])
class AdbWifiSettings : SimplePersistentStateComponent<AdbWifiSettings.Options>(Options()) {

    class Options : BaseState() {
        var autoReconnect by property(true)
        var notifications by property(true)
        var pollIntervalSeconds by property(DEFAULT_POLL_SECONDS)
        var connectTimeoutSeconds by property(DEFAULT_CONNECT_TIMEOUT_SECONDS)
        var pairTimeoutSeconds by property(DEFAULT_PAIR_TIMEOUT_SECONDS)
    }

    fun controllerSettings() = ControllerSettings(
        autoReconnect = state.autoReconnect,
        pollInterval = state.pollIntervalSeconds.coerceIn(MIN_POLL_SECONDS, MAX_POLL_SECONDS).seconds,
    )

    fun timeouts() = AdbTimeouts(
        connect = state.connectTimeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS).seconds,
        pair = state.pairTimeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS).seconds,
    )

    companion object {
        const val DEFAULT_POLL_SECONDS = 5
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 20
        const val DEFAULT_PAIR_TIMEOUT_SECONDS = 30
        const val MIN_POLL_SECONDS = 2
        const val MAX_POLL_SECONDS = 60
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 120

        fun getInstance(): AdbWifiSettings = service()
    }
}
