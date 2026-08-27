# VMessPro Android

A production-oriented Android VPN client project targeting VMess, VLESS, Reality, subscriptions, real Android VpnService routing, split tunneling, and a premium Persian RTL UI.

## Project status

Active development. The project is not considered complete until GitHub Actions is green and an installable APK artifact is produced and the real VPN core is verified on-device.

## Architecture

UI (Jetpack Compose) → ViewModel → Use Cases → Repository → ConnectionManager → CoreAdapter → VPN Core

## Priorities

1. Correct VPN behavior
2. Connection stability
3. Crash/ANR prevention
4. Correct config import
5. Transactional subscription updates
6. Real split tunneling
7. Performance and security
8. Premium RTL UI

## Core strategy

The VPN layer is designed around Android `VpnService` with a pluggable `CoreAdapter`. The primary integration target is sing-box/libbox because it supports the required modern proxy protocols and Android TUN integration. No fake VPN, fake ping, or mock traffic data is accepted.
