package dev.everts.adbwifi.ui.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import dev.everts.adbwifi.AdbWifiBundle
import dev.everts.adbwifi.persistence.AdbWifiSettings
import dev.everts.adbwifi.persistence.AdbWifiSettings.Companion.MAX_POLL_SECONDS
import dev.everts.adbwifi.persistence.AdbWifiSettings.Companion.MAX_TIMEOUT_SECONDS
import dev.everts.adbwifi.persistence.AdbWifiSettings.Companion.MIN_POLL_SECONDS
import dev.everts.adbwifi.persistence.AdbWifiSettings.Companion.MIN_TIMEOUT_SECONDS

/** Settings > Tools > ADB Wi-Fi. */
class AdbWifiConfigurable : BoundConfigurable(AdbWifiBundle.message("settings.displayName")) {

    override fun createPanel(): DialogPanel {
        val options = AdbWifiSettings.getInstance().state
        return panel {
            group(AdbWifiBundle.message("settings.group.connection")) {
                row {
                    checkBox(AdbWifiBundle.message("settings.autoReconnect"))
                        .bindSelected(options::autoReconnect)
                        .comment(AdbWifiBundle.message("settings.autoReconnect.comment"))
                }
                row {
                    checkBox(AdbWifiBundle.message("settings.notifications")).bindSelected(options::notifications)
                }
                row(AdbWifiBundle.message("settings.pollInterval")) {
                    intTextField(MIN_POLL_SECONDS..MAX_POLL_SECONDS)
                        .bindIntText(options::pollIntervalSeconds)
                        .columns(SHORT)
                    label(AdbWifiBundle.message("settings.seconds"))
                }
            }
            group(AdbWifiBundle.message("settings.group.timeouts")) {
                row(AdbWifiBundle.message("settings.connectTimeout")) {
                    intTextField(MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS)
                        .bindIntText(options::connectTimeoutSeconds)
                        .columns(SHORT)
                    label(AdbWifiBundle.message("settings.seconds"))
                }
                row(AdbWifiBundle.message("settings.pairTimeout")) {
                    intTextField(MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS)
                        .bindIntText(options::pairTimeoutSeconds)
                        .columns(SHORT)
                    label(AdbWifiBundle.message("settings.seconds"))
                }
            }
        }
    }

    private companion object {
        const val SHORT = 4
    }
}
