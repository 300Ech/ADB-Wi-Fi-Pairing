package dev.everts.adbwifi.diagnostics

/** Gravedad de un chequeo, de mejor a peor. */
enum class Severity { OK, INFO, WARNING, ERROR }

/**
 * Lo que encontró un chequeo. Cada valor tiene su texto en el bundle (`diagnostic.<NAME>`), que puede
 * usar los [CheckResult.args] como parámetros.
 */
enum class Finding {
    ADB_OK,
    ADB_MISSING,
    ADB_FAILED,
    ADB_TOO_OLD,
    MDNS_OK,
    MDNS_UNAVAILABLE,
    MDNS_NOTHING_ANNOUNCED,
    ADB_DISCOVERY_BLIND,
    MDNS_ANNOUNCING,
    WIRELESS_DEBUGGING_OFF,
    CERTIFICATE_REJECTED,
    NO_TARGET,
    NO_LOCAL_NETWORK,
    SAME_SUBNET,
    DIFFERENT_SUBNET,
    VPN_ACTIVE,
    PORT_OPEN,
    PORT_REFUSED,
    PORT_TIMEOUT,
    HOST_DOWN,
    LOCAL_NETWORK_BLOCKED,
    STUDIO_NO_LOCAL_NETWORK,
    STUDIO_RESTART_REQUIRED,
    WINDOWS_PUBLIC_NETWORK,
    WINDOWS_FIREWALL_BLOCKS_ADB,
    WINDOWS_FIREWALL_NO_RULE,
    WINDOWS_FIREWALL_OK,
}

/** Reparaciones que la UI puede ofrecer junto a un resultado. */
enum class Fix {
    OPEN_LOCAL_NETWORK_SETTINGS,
    RESTART_ADB,
    RESTART_ADB_WITH_MDNS,
    PAIR_AGAIN,
    OPEN_SDK_MANAGER,
    RESTART_STUDIO,

    /** Solo Windows: Configuración > Red e Internet. */
    OPEN_NETWORK_SETTINGS,

    /** Solo Windows: aplicaciones permitidas del Firewall de Windows. */
    OPEN_FIREWALL_SETTINGS,
}

data class CheckResult(
    val finding: Finding,
    val severity: Severity,
    val args: List<String> = emptyList(),
    val fixes: List<Fix> = emptyList(),
)
