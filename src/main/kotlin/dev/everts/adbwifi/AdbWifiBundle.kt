package dev.everts.adbwifi

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.AdbWifiBundle"

/** Acceso a los textos traducibles del plugin. Ningún texto visible se escribe directamente en el código. */
internal object AdbWifiBundle : DynamicBundle(BUNDLE) {

    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
