package dev.everts.adbwifi.domain

/**
 * Secreto de `adb pair`: el código de 6 dígitos que muestra Android en "Vincular con código", o la
 * contraseña que el host genera para el emparejamiento por QR.
 */
@JvmInline
value class PairingCode private constructor(val value: String) {

    override fun toString(): String = "PairingCode(******)"

    companion object {
        private const val LENGTH = 6

        private val QR_PASSWORD = Regex("[A-Za-z0-9]{8,64}")

        /** Contraseña de un QR generado por [QrPairingRequest]. */
        fun qrPassword(password: String): PairingCode {
            require(QR_PASSWORD.matches(password)) { "contraseña de QR no válida" }
            return PairingCode(password)
        }

        /** Acepta el código con espacios o guiones intercalados (p. ej. `123 456`). */
        fun parse(text: String): PairingCode? {
            val digits = text.filterNot { it.isWhitespace() || it == '-' }
            return if (digits.length == LENGTH && digits.all { it in '0'..'9' }) PairingCode(digits) else null
        }
    }
}
