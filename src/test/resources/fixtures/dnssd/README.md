# Fixtures de dns-sd (macOS)

Capturados el 2026-10-15 con `/usr/bin/dns-sd` (macOS 27) mientras un Pixel 10 de la LAN tenía abierta
"Vincular mediante código QR" con el QR del plugin. En ese momento `adb mdns services` (adb 37.0.1) no
veía ninguno de los dos servicios: por eso el plugin consulta también Bonjour.

| Archivo | Comando |
|---------|---------|
| `browse-pairing.txt` | `dns-sd -B _adb-tls-pairing._tcp local.` |
| `browse-connect.txt` | `dns-sd -B _adb-tls-connect._tcp local.` |
| `lookup-pairing.txt` | `dns-sd -L studio-VISAKqPGtD _adb-tls-pairing._tcp local.` |
| `lookup-connect.txt` | `dns-sd -L adb-58181FDCR0065F-XeVEcV _adb-tls-connect._tcp local.` |

`dns-sd` no termina solo: se capturó 3 s y se cortó.
