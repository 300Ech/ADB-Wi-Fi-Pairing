# Fixtures de adb

Salida de `adb` 37.0.1-15733141 (platform-tools, macOS arm64) capturada el 2026-10-15.
Cada caso guarda `stdout` y `stderr` por separado (`<caso>.stdout.txt`, `<caso>.stderr.txt`); si
un archivo no existe, ese flujo salió vacío. Las líneas `* daemon not running...` del arranque del
servidor se quitaron de `stderr`.

## Capturados de verdad

| Caso | Comando | Exit | Origen |
|------|---------|------|--------|
| `devices-empty` | `adb devices -l` | 0 | sin dispositivos |
| `devices-short` | `adb devices` | 0 | emulador |
| `devices-usb-emulator` | `adb devices -l` | 0 | emulador |
| `devices-mixed` | `adb devices -l` | 0 | emulador por `adb connect` + emulador offline |
| `devices-offline` | `adb devices -l` | 0 | emulador offline tras `tcpip` |
| `mdns-check` | `adb mdns check` | 0 | backend por defecto (`adb discovery`) |
| `mdns-check-disabled` | `ADB_MDNS=0 adb mdns check` | 0 | el error sale por `stdout` |
| `mdns-services` | `adb mdns services` | 0 | Pixel 10 real en la red local (separador: tabulador) |
| `mdns-track-services-proto-text` | `adb mdns track-services --proto-text` | — | mismo Pixel 10; aún no se parsea |
| `pair-ok` | `adb pair 127.0.0.1:7791 <código>` | 0 | emulador con puerto de pairing redirigido |
| `pair-wrong-code` | `adb pair 127.0.0.1:7791 000000` | 1 | código incorrecto: adb 37 responde `protocol fault` |
| `pair-closed-port` | `adb pair 127.0.0.1:5 123456` | 1 | mismo mensaje que el código incorrecto |
| `connect-ok` | `adb connect 127.0.0.1:7790` | 0 | emulador tras `tcpip` |
| `connect-already` | repetir `connect-ok` | 0 | |
| `connect-refused` | `adb connect 127.0.0.1:5` | 0 | los fallos de `connect` salen con exit 0 |
| `connect-unresolved` | `adb connect nonexistent.invalid:5555` | 0 | |
| `connect-timeout` | `adb connect 192.168.0.253:41234` | 0 | IP libre de la LAN; tarda 75 s |
| `connect-auth-failed` | `adb connect 192.168.0.16:40073` | 0 | Pixel 10 que rechaza el certificado TLS del host (`SSLV3_ALERT_CERTIFICATE_UNKNOWN` en el log del servidor) |
| `disconnect-ok` | `adb disconnect 127.0.0.1:7790` | 0 | |
| `disconnect-unknown` | `adb disconnect 127.0.0.1:5` | 1 | |
| `tcpip` | `adb -s emulator-5580 tcpip 5556` | 0 | |
| `tcpip-no-device` | `adb -s nonexistent tcpip 5555` | 1 | |

## Reconstruidos (`*.reconstructed.*`)

No se pudieron reproducir en esta máquina. Se escribieron a partir de las cadenas de formato que
contiene el binario de adb 37.0.1 (`strings adb`) y de `strerror` de macOS. Sustituir por capturas
reales en cuanto aparezcan.

| Caso | Cadena de formato |
|------|-------------------|
| `connect-no-route` | `failed to connect to '%s:%d': %s` + `No route to host` (EHOSTUNREACH; permiso Red local) |
| `connect-authenticate` | `failed to authenticate to %s` |
| `pair-wrong-password` | `Failed: Wrong password or connection was dropped.` |
| `pair-client-failed` | `Failed: Unable to start pairing client.` |
| `devices-wireless` | `%-22s %s` + `product:` `model:` `device:` `transport_id:`; estados `unauthorized`, `authorizing` y `no permissions (...)` |

## Windows (`*-windows-<idioma>.reconstructed.*`)

No hay una máquina Windows donde capturarlos. En Windows adb no usa `strerror`: `network_connect`
(`sysdeps_win32.cpp`) escribe `cannot connect to %s:%s: %s` con `SystemErrorCodeToString`, que devuelve
el texto de `FormatMessage` en el idioma de Windows seguido del código Winsock: `%s (%d)`. `adb connect`
lo envuelve en `failed to connect to '%s': %s`. Los textos son los de Windows en cada idioma (tal como
aparecen en informes de usuarios); el clasificador solo se fía del código entre paréntesis.

| Caso | Código | Causa |
|------|--------|-------|
| `connect-refused-windows-en`, `connect-refused-windows-es` | 10061 | `WSAECONNREFUSED` |
| `connect-timeout-windows-en`, `connect-timeout-windows-es` | 10060 | `WSAETIMEDOUT` (en inglés no dice "timed out") |
| `connect-no-route-windows-de` | 10065 | `WSAEHOSTUNREACH` |
| `connect-network-unreachable-windows-en` | 10051 | `WSAENETUNREACH` |

## Capturas del emulador (mismo adb, emulador `Medium_Phone_API_36.1`)

| Caso | Comando | Exit |
|------|---------|------|
| `version` | `adb version` | 0 |
| `start-server` | `adb -P 5043 start-server` (servidor parado) | 0 |
| `getprop-serialno` | `adb -s emulator-5580 shell getprop ro.serialno` | 0 |
| `getprop-model` | `adb -s emulator-5580 shell getprop ro.product.model` | 0 |
| `getprop-missing` | `adb -s emulator-5580 shell getprop ro.nonexistent.prop` (línea vacía) | 0 |
| `ip-route` | `adb -s emulator-5580 shell ip route` | 0 |
| `shell-no-device` | `adb -s nonexistent shell getprop ro.serialno` | 1 |

## Log del servidor

`server-log-certificate-rejected.txt`: extracto real de `$TMPDIR/adb.501.log` con los dos teléfonos de
la LAN rechazando el certificado del host (`SSLV3_ALERT_CERTIFICATE_UNKNOWN`) mientras adb reintenta
la conexión automática por mDNS.

## Fallos de `adb pair` en el log del servidor

El cliente solo dice `protocol fault`; el motivo está en el log del servidor.

- `server-log-pairing-no-route.txt`: emparejamiento por QR con un Pixel 10 real; el servidor adb
  (arrancado horas antes, sin acceso a la red local) no llega al teléfono: `No route to host`.
- `server-log-pairing-wrong-code.txt`: código incorrecto contra el emulador (`Failed to decrypt`).

## Teléfono real conectado (2026-10-15 14:56)

`devices-wifi-mdns`: `adb devices -l` con el Pixel 10 conectado por Wi-Fi tras emparejarlo por QR desde
el plugin (transporte que adb abre solo por mDNS).
