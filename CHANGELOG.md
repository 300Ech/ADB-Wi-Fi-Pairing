<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# ADB Wi-Fi Pairing Changelog

## [Unreleased]

## [0.1.3] - 2026-10-20

### Added

- Windows: Diagnostics warn when Windows treats the network as Public, which blocks discovery, and
  when Windows Defender Firewall blocks adb or has no rule that allows it, with buttons that open the
  matching Windows settings. Not yet tried on a real Windows machine.

### Fixed

- Windows: connection errors from adb.exe, which come in the language of Windows, are now explained
  (refused, timed out, host or network unreachable) instead of showing "adb could not complete the
  operation".
- "Network is unreachable" is reported as an unreachable phone instead of an unknown error.

## [0.1.2] - 2026-10-15

### Fixed

- After you grant the macOS Local Network permission, adb only gets it once Android Studio restarts.
  The plugin now checks that adb itself reaches the network and, if it does not, asks for that restart
  with a "Restart Android Studio" button instead of leaving devices silently disconnected.

## [0.1.1] - 2026-10-15

### Fixed

- On macOS, when Android Studio does not have the Local Network permission (so neither it nor adb can
  see or reach phones over Wi-Fi), the plugin now says so up front with a one-click way to open the
  right settings, instead of failing later with timeouts. Once the permission is on, it restarts adb
  by itself.
- When the address typed in the pairing or connection dialog is not on this computer's network (for
  example because a VPN on the phone makes Android show the VPN address), the error says so.
- Labels showed "IP address  port" instead of "IP address and port".

## [0.1.0] - 2026-10-15

First release.

### Added

- "ADB Wi-Fi" tool window: paired, connected (Wi-Fi and USB) and discovered devices in one list,
  with status, address and context actions.
- Pairing with IP, port and six-digit code, without relying on mDNS discovery.
- Pairing with a QR code (same format as Android Studio's dialog), with a fallback to the code.
- Assisted pairing: phones with "Pair device with pairing code" open appear as "Ready to pair" and
  in a notification; choosing one fills in the address.
- Devices are discovered through macOS Bonjour as well as adb, so they show up even when adb's own
  mDNS discovery stops seeing them.
- Automatic reconnection when a paired device announces a new port or after the adb server
  restarts, with a per-device switch and notifications.
- Detects phones that no longer accept this computer (revoked or expired authorization) and asks
  to pair again.
- Diagnostics tab: adb, mDNS discovery, macOS Local Network permission, subnet, VPN and port
  reachability, with fix buttons.
- Pairing and connection errors explain the real cause and what to do; when Android Studio can
  reach the phone but the adb server cannot, the plugin restarts adb and retries once.
- Connect, disconnect, rename and forget devices; restart the adb server; switch a USB device to
  Wi-Fi (`adb tcpip 5555`).
- Settings > Tools > ADB Wi-Fi: automatic reconnection, notifications, polling interval, timeouts.
- English and Spanish.

[Unreleased]: https://github.com/OWNER/myadbwifi/compare/0.1.3...HEAD
[0.1.3]: https://github.com/OWNER/myadbwifi/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/OWNER/myadbwifi/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/OWNER/myadbwifi/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/OWNER/myadbwifi/commits/0.1.0
