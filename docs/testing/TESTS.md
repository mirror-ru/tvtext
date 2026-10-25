# TVText test report

## Current 2.0.7 artifacts

- TV APK: `TVText-TV-2.0.7.apk`, package `com.tvtext.app`, version code `27`.
- Mobile APK: `TVText-Mobile-2.0.7.apk`, package `com.tvtext.mobile.app`, version code `27`.
- Windows EXE: `TVText-2.0.7.exe`, version `2.0.7.0`.
- APK signatures and APK/EXE version metadata were verified during the 2.0.7 build.
- SHA-256: TV APK `EC82FE3CF7D252A9ECC4137212608999503D4F3AE1B46337CF9A426C0038F75A`; mobile APK `9611B084DF4840F804D578A33A3999E69F8E377083345D9C34B0263451C26209`; Windows EXE `B2B96875C0FE31980D437E7DC88AF43FB3C577A9D300ABC93D9479370D1ADBB4`.

Review regression checks cover actual mobile touch events (including cancellation and dragging outside controls), migration of encrypted profiles/local names, removal of absent renamed TVs, Windows active-profile synchronization and localized response codes, and legacy clipboard restoration after rapid TV inserts or an intervening user copy.

The last full review suite passed before the 2.0.7 version-only rebuild: Windows/TLS `build/test-269701adc95b4d3e98de29408c432350`, mobile JVM/TLS/UI `build/mobile-test-cd3b2bcdebf141a58de58b492c965e6d`, and TV UI `build/tv-ui-test-5cba03cdecc0460c9dc2f831d7f03663`. The APK UI checks ran on the isolated Android 14 emulator. Version 2.0.7 was not retested because it changes release metadata only; no protocol or runtime source changes were introduced.

The detailed 2.0.4 verification history remains below for historical reference.

## Historical 2.0.4 verification

The 2.0.4 release artifacts were checked for:

- EXE `AssemblyVersion`, `FileVersion`, and `ProductVersion`: `2.0.4.0`;
- both APK `versionName`: `2.0.4`, `versionCode`: `24`;
- APK metadata, v3 signatures, zip alignment, and matching release certificate;
- the Windows sidecar name `TVText-2.0.4.exe.config`;
- a source tree with tests grouped under `tests/android-tv/`, `tests/android/`, and `tests/windows/`.

After the test-tree reorganization, the complete Windows/TV suite and the mobile JVM core suite passed on the release code. The 2.0.4 build is a version-only rebuild; no protocol or UI behavior changed.

## Running the checks

From the project root:

```powershell
.\test.ps1 -Sdk 'C:\Users\YOUR_USER\AppData\Local\Android\Sdk'
.\test.ps1 -Shrink -Sdk 'C:\Users\YOUR_USER\AppData\Local\Android\Sdk'
.\test-mobile.ps1 -Sdk 'C:\Users\YOUR_USER\AppData\Local\Android\Sdk'
```

The device suite is restricted to an explicitly named emulator:

```powershell
.\test-mobile.ps1 -Device emulator-5580 `
  -Sdk 'C:\Users\YOUR_USER\AppData\Local\Android\Sdk' `
  -AppApk '.\dist\TVText-Mobile-2.0.7.apk'
```

`test.ps1` compiles the C# Windows client and Kotlin TV fixture, runs local TLS/DNS-SD tests, and renders Windows Forms checks off-screen. `test-mobile.ps1` first runs a JVM protocol suite; with `-Device` it additionally builds an instrumentation APK, installs the release APK, and runs tests inside the selected emulator.

## Windows and TV coverage

The tests cover:

- frame lengths, UTF-8 validation, text-size limits, malformed replies, and stable TV identity;
- TLS 1.2, certificate pinning, pairing expiry, ten-attempt lockout, token rotation, revocation, and IPv4-only/dual-stack behavior;
- insertion idempotency, duplicate suppression, queue cancellation, absolute deadlines, paused reception, and no replay after an ambiguous result;
- encrypted multi-TV profiles, atomic writes, corrupt-file preservation, concurrent profile writes, endpoint recovery after an IP change, and per-TV isolation;
- Windows DNS-SD browse/resolve, cancellation, native-handle cleanup, and stable installation IDs;
- TV selection, keyboard navigation, accessibility, pairing/re-pairing, rename, RU/EN switching, Enter/Shift+Enter, draft preservation, send-state routing, and no idle status polling;
- Windows layout at 96 and 120 DPI plus 100–200% programmatic scaling, 0/1/2/3/9 TVs, long names, the three-row TV list, and the three-line editor.

The TV accessibility fixture is under `tests/android-tv/fixture/`. It is a controlled Android field used to check insertion behavior; it is not a third-party TV application.

## Mobile coverage

The JVM suite cross-checks the phone codec against the production TV protocol and verifies strict UTF-8/frame handling, TLS pinning, pairing, UUID identity, independent TVs, revocation, cancellation, and the absence of automatic text replay.

Instrumentation runs against the real release mobile APK on an Android 14 AOSP ATD emulator. It covers Android NSD discovery, Android Keystore AES-GCM storage, damaged-profile handling, Unicode send routing, success clearing, error preservation, edits during an in-flight send, local rename, unpair/re-pair dialogs, RU/EN switching, rotation, and screenshot capture. Phone and tablet layouts were checked in portrait and landscape sizes.

## Environment and limits

The emulator run used API 34, minSdk 28 was compiled and exercised through the production code paths, and device tests never target a physical phone or TV. Local TLS fixtures listen only on loopback ports 45831–45833; no real TV credentials or external network are used.

Physical Google TV firmware, Gboard, vendor accessibility behavior, sleep/wake, and third-party input filters still require a manual Wi-Fi check. An application can transform or reject text after TVText hands it to the active Android field. If no editable field is active, the request is rejected and is not replayed.

The `-Shrink` option checks R8-shrunk JVM fixture code with the application keep rules. It does not replace an Android DEX/device run. Test outputs, fixture keys, screenshots, and temporary profiles are written below `build/` and are excluded by `.gitignore`.
