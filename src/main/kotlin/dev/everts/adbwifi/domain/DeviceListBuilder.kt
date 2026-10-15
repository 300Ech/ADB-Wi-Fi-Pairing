package dev.everts.adbwifi.domain

/**
 * Une dispositivos emparejados, transportes de adb y servicios mDNS en una fila por dispositivo.
 *
 * La identidad común es el serial del dispositivo: viene en el nombre mDNS, en el serial de transporte
 * de las conexiones automáticas y, para conexiones manuales (`host:puerto`), en [DeviceIdentity].
 * Los emuladores se omiten: no usan Wi-Fi.
 */
object DeviceListBuilder {

    fun build(
        paired: List<PairedDevice>,
        devices: List<Device>,
        services: List<MdnsService>,
        identities: Map<String, DeviceIdentity> = emptyMap(),
    ): List<DeviceEntry> {
        val pairedById = paired.associateBy { it.id }
        val devicesByKey = devices
            .filterNot { it.serial.startsWith(EMULATOR_PREFIX) }
            .groupBy { keyOf(it, identities, paired) }
            .mapValues { (_, transports) -> transports.minBy { it.state.ordinal } }
        val servicesByKey = services
            .mapNotNull { service -> keyOf(service)?.let { it to service } }
            .groupBy({ it.first }, { it.second })
        val keys = LinkedHashSet<String>().apply {
            addAll(pairedById.keys)
            addAll(devicesByKey.keys)
            addAll(servicesByKey.keys)
        }
        return keys.map { key ->
            val keyServices = servicesByKey[key].orEmpty()
            entry(
                key = key,
                paired = pairedById[key],
                device = devicesByKey[key],
                identity = devicesByKey[key]?.let { identities[it.serial] },
                connectService = keyServices.firstOrNull { it.type == MdnsServiceType.TLS_CONNECT }
                    ?: keyServices.firstOrNull { it.type == MdnsServiceType.LEGACY },
                pairingService = keyServices.firstOrNull { it.type == MdnsServiceType.PAIRING },
            )
        }.sortedWith(compareBy<DeviceEntry> { it.status.ordinal }.thenBy { it.name.lowercase() })
    }

    @Suppress("LongParameterList")
    private fun entry(
        key: String,
        paired: PairedDevice?,
        device: Device?,
        identity: DeviceIdentity?,
        connectService: MdnsService?,
        pairingService: MdnsService?,
    ) = DeviceEntry(
        key = key,
        name = paired?.name ?: identity?.model ?: device?.model?.replace('_', ' ') ?: key,
        status = status(device, connectService, pairingService),
        endpoint = device?.endpoint ?: connectService?.endpoint ?: pairingService?.endpoint ?: paired?.lastEndpoint,
        paired = paired,
        device = device,
        connectService = connectService,
        pairingService = pairingService,
    )

    private fun status(device: Device?, connectService: MdnsService?, pairingService: MdnsService?): EntryStatus =
        when {
            device != null -> when (device.state) {
                DeviceState.DEVICE -> if (device.isWireless) EntryStatus.CONNECTED else EntryStatus.USB
                DeviceState.UNAUTHORIZED -> EntryStatus.UNAUTHORIZED
                else -> EntryStatus.CONNECTING
            }
            pairingService != null -> EntryStatus.PAIRING
            connectService != null -> EntryStatus.AVAILABLE
            else -> EntryStatus.NOT_SEEN
        }

    /** Serial del dispositivo detrás de un transporte, o el propio serial de transporte si no se sabe. */
    fun keyOf(device: Device, identities: Map<String, DeviceIdentity>, paired: List<PairedDevice>): String =
        AdbInstanceName.serialOf(device.serial)
            ?: identities[device.serial]?.serialNumber
            ?: device.endpoint?.let { endpoint -> paired.firstOrNull { it.lastEndpoint?.host == endpoint.host }?.id }
            ?: device.serial

    /** Los servicios de pairing por QR (`studio-...`) no son de ningún dispositivo conocido y se omiten. */
    private fun keyOf(service: MdnsService): String? =
        service.deviceSerial ?: service.instanceName.takeUnless { service.type == MdnsServiceType.PAIRING }

    private const val EMULATOR_PREFIX = "emulator-"
}
