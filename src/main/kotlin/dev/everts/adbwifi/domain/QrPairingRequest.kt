package dev.everts.adbwifi.domain

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Datos de un emparejamiento por QR, con el mismo formato que usa Android Studio.
 *
 * El teléfono escanea `WIFI:T:ADB;S:<nombre>;P:<contraseña>;;`, abre su puerto de pairing y lo anuncia por
 * mDNS con [serviceName] como nombre de instancia; el host lo reconoce por ese nombre y ejecuta
 * `adb pair IP:PUERTO <contraseña>`.
 */
class QrPairingRequest private constructor(val serviceName: String, private val password: String) {

    /** Texto que codifica el QR. Solo hay caracteres alfanuméricos, así que no hace falta escapar nada. */
    val payload: String get() = "WIFI:T:ADB;S:$serviceName;P:$password;;"

    val secret: PairingCode get() = PairingCode.qrPassword(password)

    override fun toString(): String = "QrPairingRequest($serviceName)"

    companion object {
        const val NAME_PREFIX = "studio-"
        private const val NAME_LENGTH = 10
        private const val PASSWORD_LENGTH = 12
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        fun generate(random: Random = SecureRandom().asKotlinRandom()): QrPairingRequest =
            QrPairingRequest(NAME_PREFIX + random.text(NAME_LENGTH), random.text(PASSWORD_LENGTH))

        private fun Random.text(length: Int) = String(CharArray(length) { ALPHABET[nextInt(ALPHABET.length)] })
    }
}
