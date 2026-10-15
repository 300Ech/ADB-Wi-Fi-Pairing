# ADB Wi-Fi Pairing

Plugin para Android Studio que hace fiable la depuración por Wi-Fi: emparejar sin depender
del descubrimiento mDNS, reconectar automáticamente cuando cambia el puerto y explicar por qué
falla cuando falla.

> Versión 0.1.0. Requiere Android Studio 2026.2.1.

## Uso

Abre el tool window **ADB Wi-Fi** (barra derecha) o **Tools > ADB Wi-Fi**.

- **Emparejar con código**: en el teléfono, *Opciones de desarrollador > Depuración inalámbrica >
  Vincular con código*; escribe IP:puerto y el código. No necesita mDNS. Si el dispositivo no se
  conecta solo después, indica también el puerto de conexión de la pantalla principal.
- **Emparejar con QR**: *Vincular mediante código QR* y escanea el código del diálogo (necesita mDNS).
- **Lista de dispositivos**: emparejados, conectados (Wi-Fi y USB) y anunciados por mDNS. Menú
  contextual para conectar, desconectar, volver a emparejar, pasar un USB a Wi-Fi (`tcpip 5555`),
  renombrar, olvidar y activar o desactivar la reconexión automática.
- **Reconexión automática**: cuando un dispositivo emparejado anuncia un puerto nuevo, o tras
  reiniciarse el servidor adb, el plugin lo vuelve a conectar.
- **Diagnóstico**: explica por qué no aparece o no conecta un dispositivo (adb, mDNS, permiso Red local
  de macOS, subred, VPN, puerto, teléfono que rechaza la clave de este ordenador) y ofrece reparaciones.

Ajustes en *Settings > Tools > ADB Wi-Fi*.

<!-- Plugin description -->
Reliable wireless debugging for Android Studio.

- Pair devices manually with the IP, port and code shown on the phone, without relying on mDNS discovery.
- Assisted pairing with code or QR when mDNS works.
- Automatic reconnection when the wireless debugging port changes or the phone reboots.
- Diagnostics that explain why discovery fails (mDNS backend, macOS Local Network permission, client isolation) and how to fix it.
<!-- Plugin description end -->

## Requisitos

- Android Studio 2026.2.1 (build 262).
- Android 11 o superior en el dispositivo para emparejamiento inalámbrico.

## Desarrollo

Requisitos: JDK 21 (Gradle lo aprovisiona solo con el toolchain si no está instalado).

```bash
./gradlew check        # compila, tests, detekt y cobertura
./gradlew runIde       # abre Android Studio con el plugin cargado (sandbox)
./gradlew buildPlugin  # genera build/distributions/*.zip
./gradlew verifyPlugin # Plugin Verifier contra las IDEs configuradas
```

Prueba opcional contra el adb instalado (solo comandos de lectura):

```bash
ADB_WIFI_REAL_ADB=1 ./gradlew test --tests '*RealAdbTest'
```

Por defecto Gradle descarga Android Studio `platformVersion` (ver `gradle.properties`). Para usar
la instalación local y ahorrar la descarga, crea `local.properties` en la raíz:

```properties
platform.localPath=/Applications/Android Studio.app
```

Instalar el zip en tu Android Studio: Settings > Plugins > ⚙ > Install Plugin from Disk.

## Licencia

Apache-2.0. Ver [LICENSE](LICENSE).
