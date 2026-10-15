<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# ADB Wi-Fi Pairing Changelog

## [Unreleased]

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
