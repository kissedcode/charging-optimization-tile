# Charging Optimization Tile

A small, offline Android Quick Settings tile for Google Pixel.

- Tap to switch **80% limit ↔ Off**.
- Long-press to open the system charging settings.
- Shows the system's actual state, including Adaptive and unavailable states.
- No network access, analytics, persistent notification, or foreground service.

Target device: Pixel 9 Pro on stock Android 15 or later. Other devices and ROMs are not supported yet.

## Install

Install a distributed APK and grant the switching permission once:

```sh
adb shell pm grant com.kissedcode.chargingtile android.permission.WRITE_SECURE_SETTINGS
```

Open the app and tap **Add tile**. Arrange it using Android's Quick Settings editor.

Without permission the tile opens setup. External Adaptive is shown accurately; tapping selects 80%. Off disables the limit and Adaptive, not thermal or other safety protections. Unknown or unsupported configurations are never written.

## Build

JDK 17 and Android SDK 36:

```sh
./gradlew :app:lintInternal :app:testInternalUnitTest :app:assembleInternal
./scripts/package-apk.sh
```

The `internal` build is non-debuggable, R8-minified, resource-shrunk and debug-signed. The `-debug` suffix does not change applicationId.

Set `ANDROID_DEBUG_KEYSTORE_PATH` to a private persistent debug keystore for update-compatible builds. Otherwise local checks use the machine's default debug key and are not distributed.

## Automation

Pull requests run checks without cloud credentials. Main runs the delivery workflow with the persistent debug key and Google OIDC, then uploads to Firebase App Distribution.

See [deployment setup](docs/DEPLOYMENT.md) and the Russian [specification](SPEC.md). Firebase is a delivery service only; no Firebase SDK is in the APK. SPEC records real cloud/device verification status separately from CI.
