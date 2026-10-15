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
}

/** Reparaciones que la UI puede ofrecer junto a un resultado. */
enum class Fix {
    OPEN_LOCAL_NETWORK_SETTINGS,
    RESTART_ADB,
    RESTART_ADB_WITH_MDNS,
    PAIR_AGAIN,
    OPEN_SDK_MANAGER,
    RESTART_STUDIO,
}

data class CheckResult(
    val finding: Finding,
    val severity: Severity,
    val args: List<String> = emptyList(),
    val fixes: List<Fix> = emptyList(),
)
