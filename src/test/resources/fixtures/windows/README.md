# Fixtures de Windows

Salida de `src/main/resources/windows/network-status.ps1`, que lee `WindowsNetworkParser`. Son
**reconstruidas** (`*.reconstructed.*`): no hay una máquina Windows donde capturarlas. El formato lo
define el propio script (una línea por dato, separada por tabuladores); los valores son los nombres de
los enums que devuelven los cmdlets, que no se traducen: `NetworkCategory` (`Public`, `Private`,
`DomainAuthenticated`), `Get-NetFirewallProfile.Name` y `.Enabled` (`True`/`False`), y de
`Get-NetFirewallRule`, `Action` (`Allow`/`Block`) y `Profile` (`Any` o `Domain, Private, Public`).
El nombre de la conexión (`InterfaceAlias`) sí depende del idioma. Sustituir por capturas reales en
cuanto aparezcan.

| Caso | Situación |
|------|-----------|
| `network-status-public-blocked` | Wi-Fi marcada como pública; se canceló el aviso del firewall al arrancar adb, que dejó dos reglas de bloqueo (TCP y UDP) para Pública y la de permitir solo para Privada |
| `network-status-private-allowed` | Wi-Fi privada y Ethernet de dominio (nombre en español), firewall apagado en el perfil Público, adb permitido |
