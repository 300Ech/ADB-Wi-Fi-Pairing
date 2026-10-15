package dev.everts.adbwifi.domain

/**
 * Causa de un fallo de `adb pair` o `adb connect`, deducida del mensaje de adb.
 * La UI y el diagnóstico deciden qué explicar al usuario a partir de aquí.
 */
enum class AdbFailureReason {
    /** El puerto no acepta conexiones: depuración inalámbrica apagada o puerto antiguo. */
    CONNECTION_REFUSED,

    /** `No route to host`: en macOS suele ser el permiso "Red local" o otra subred. */
    NO_ROUTE_TO_HOST,

    /** `Host is down`: nadie responde en esa IP de la red local. */
    HOST_DOWN,

    /** La conexión TCP no se completó a tiempo (adb o el plugin agotaron la espera). */
    TIMED_OUT,

    /** No se pudo resolver el nombre de host. */
    HOST_NOT_RESOLVED,

    /**
     * El servidor adb cortó la conversación sin respuesta. Con adb 37 es lo que devuelve `adb pair`
     * tanto con un código incorrecto como con un puerto de emparejamiento cerrado o inalcanzable.
     */
    PROTOCOL_FAULT,

    /** Código incorrecto o el teléfono cerró el diálogo de vinculación (mensaje de versiones de adb anteriores). */
    WRONG_PAIRING_CODE,

    /** adb no pudo iniciar el cliente de emparejamiento. */
    PAIRING_CLIENT_FAILED,

    /**
     * La conexión TCP se abrió pero el teléfono rechazó el certificado TLS de esta máquina: no está
     * emparejado o revocó la autorización. adb lo informa como `failed to connect to HOST:PUERTO`
     * (sin causa) o `failed to authenticate to HOST:PUERTO`.
     */
    AUTHENTICATION_FAILED,

    /**
     * El teléfono es accesible desde Android Studio pero no desde el servidor adb (`No route to host`):
     * en macOS, el proceso que arrancó el servidor no tiene el permiso "Red local". Se arregla
     * reiniciando el servidor desde Android Studio, que el plugin intenta una vez.
     */
    ADB_SERVER_NO_NETWORK,

    /** Modo `tcpip`: el dispositivo USB no tiene IP en una Wi-Fi. */
    NO_WIFI_ADDRESS,

    /** Mensaje que todavía no sabemos clasificar. */
    UNKNOWN,
}
