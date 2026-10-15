package dev.everts.adbwifi.domain

/** Reglas puras para mantener al día la lista de dispositivos emparejados. */
object PairedDevices {

    /** Inserta o reemplaza por [PairedDevice.id], conservando el nombre y la preferencia del existente. */
    fun upsert(paired: List<PairedDevice>, device: PairedDevice): List<PairedDevice> {
        val existing = paired.firstOrNull { it.id == device.id }
        val merged = existing?.copy(lastEndpoint = device.lastEndpoint ?: existing.lastEndpoint) ?: device
        return paired.filterNot { it.id == device.id } + merged
    }

    /**
     * Actualiza lo emparejado con lo que se ve conectado:
     * - un emparejado guardado con la IP como id (adb no dio el serial) pasa a usar el serial real;
     * - el nombre provisional (igual al id o a la IP) se sustituye por el modelo;
     * - la última dirección de conexión se actualiza;
     * - un dispositivo conectado por Wi-Fi que no estaba en la lista se añade: si conectó, está emparejado.
     */
    fun reconcile(
        paired: List<PairedDevice>,
        devices: List<Device>,
        identities: Map<String, DeviceIdentity>,
        connectEndpoints: Map<String, AdbEndpoint> = emptyMap(),
    ): List<PairedDevice> {
        var result = paired
        for (device in devices.filter { it.state == DeviceState.DEVICE && it.isWireless }) {
            val identity = identities[device.serial]
            val serial = AdbInstanceName.serialOf(device.serial) ?: identity?.serialNumber ?: continue
            result = reconcileOne(result, serial, identity?.model, device.endpoint ?: connectEndpoints[serial])
        }
        return result
    }

    private fun reconcileOne(
        paired: List<PairedDevice>,
        serial: String,
        model: String?,
        endpoint: AdbEndpoint?,
    ): List<PairedDevice> {
        val previous = paired.firstOrNull { it.id == serial }
            ?: endpoint?.let { e -> paired.firstOrNull { it.id == e.host } }
        val updated = previous?.copy(
            id = serial,
            name = if (model != null && previous.name in setOf(previous.id, serial)) model else previous.name,
            lastEndpoint = endpoint ?: previous.lastEndpoint,
        ) ?: PairedDevice(serial, model ?: serial, endpoint)
        if (updated == previous) return paired
        return paired.filterNot { it.id == previous?.id || it.id == serial } + updated
    }
}
