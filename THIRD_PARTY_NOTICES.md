# Third-party notices

VMess Pro builds and bundles third-party open-source components. Their upstream licenses remain in force.

## Amnezia libXray

- Upstream: `amnezia-vpn/amnezia-libxray`
- Pinned commit: `e8cc06d7427251fa549093e7cc32c28b0f5fbafa`
- License: MIT
- The Android AAR is built from the pinned upstream source in `.github/workflows/android-build.yml`; it is not a placeholder binary.
- libXray provides the Android mobile bindings used here for Xray-core, configuration conversion, real HTTP profile testing, socket protection integration, and tun2socks.

## Amnezia Xray Core

- Upstream: `amnezia-vpn/amnezia-xray-core`
- Version selected by the pinned libXray `go.mod`: `v1.260728.0`
- License: Mozilla Public License 2.0 (MPL-2.0)
- Corresponding source remains available from the upstream repository and is reproducibly selected by the pinned libXray dependency graph.

## Amnezia tun2socks

- Upstream: `amnezia-vpn/amnezia-tun2socks`
- Version selected by the pinned libXray `go.mod`: `v2.5.6`
- It is linked through libXray and routes the Android TUN file descriptor to the local Xray SOCKS inbound.

## Vazirmatn

- Upstream: `rastikerdar/vazirmatn`
- Pinned release: `v33.003`
- License: SIL Open Font License 1.1
- The build downloads the pinned Regular and Bold TTF files and packages them as Android font resources.

No font file is fetched at application runtime; the installed APK contains the font resources produced at build time.
