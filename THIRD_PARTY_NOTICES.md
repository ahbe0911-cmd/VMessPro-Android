# Third-party notices

VMess Pro builds and bundles third-party open-source components. Their upstream licenses remain in force.

## sing-box / libbox

- Upstream: `SagerNet/sing-box`
- Pinned version: `v1.13.19`
- License: GNU General Public License v3.0 or later (GPL-3.0-or-later)
- The Android AAR is built from upstream source in `.github/workflows/android-build.yml`; it is not a placeholder binary.

Because the distributed APK contains libbox, redistribution must comply with the GPL, including providing the corresponding source and license notices as required by the license.

## Vazirmatn

- Upstream: `rastikerdar/vazirmatn`
- Pinned release: `v33.003`
- License: SIL Open Font License 1.1
- The build downloads the pinned Regular, Medium, Bold and ExtraBold TTF files and packages them as Android font resources.

No font file is fetched at application runtime; the installed APK contains the font resources produced at build time.
