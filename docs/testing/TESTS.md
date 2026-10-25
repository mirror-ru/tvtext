# TVText test report

## Git ignore audit — 26 October 2026

- Existing build/output/local-key exclusions cover the current generated files. Added explicit rules for `signing-password.txt`, `.env` / `.env.*` and APK `.idsig` sidecars; `.env.example` templates remain eligible.
- Verified positive/negative ignore cases, no already tracked files matching ignore rules, and an unchanged set of eligible new source/test files. Public signing-certificate fingerprints, version/toolchain metadata and the Windows `.exe.config` source remain eligible. No application rebuild or GitHub publication was needed.

## Local version 2.0.9 rebuild — 26 October 2026

- Built all three applications from the current local sources: APK version 2.0.9 / code 29 and Windows assembly/file/product version 2.0.9.0. Artifacts are under `outputs/TVText-2.0.9/`.
- The output directory contains only the TV APK, mobile APK, EXE and EXE configuration. Matching local APK/JAR build records, SHA-256 sums and `verification.json` stay in `build/rebuild-2.0.9/`.
- Verified both package IDs, minSdk 28 / targetSdk 34, alignment, the existing pinned signing certificate, exact APK/JAR/source pairing, Windows versions, runtime configuration and hashes after copying.
- Build tooling checks passed: `build/build-tests-fb0fa9ba51384182950c9e77c0c881e8`. Functional/UI suites were not repeated for this metadata-only version bump; the preceding refactoring and destruction-order checks are recorded below.
- Previous 2.0.8 output remains intact. No Git commit, tag or GitHub publication was created.

| Artifact | SHA-256 |
| --- | --- |
| TV APK | `d9819cd3f46252ea4f25476d42fe1da80a1b36fe7d97538b24322d76f75d76e2` |
| Mobile APK | `0b51c25d2ce17d83beb48bc23e9d7d5189a603be6d72ba3a34cd7eeb04cf3b60` |
| Windows EXE | `914f26c7d55d3b48cc288e456bcd3d0026603a750921dcf0106227364290876d` |
| EXE configuration | `8d77ce46414815bd762d840d78d2339bfffb257a19e31a863081b660226dbf27` |

## Destruction-order regression fixed — 26 October 2026

- The P3 review finding is resolved: cancellation and worker shutdown precede discovery shutdown; repeated destruction does nothing.
- Added a deterministic regression where a worker has taken a request from the queue but resumes only while `Discovery.stop()` is waiting. It confirms no transport call, confirmation or UI notice after destruction. Also checked cleanup before a discovery-stop exception and idempotent destruction.
- The new regression failed on the old code with `Queued transport started while stopping discovery: [3]` (`build/destroy-fix-red.log`). It passed after the fix with all mobile JVM/TLS checks: `build/mobile-test-0cdeb02bdc734e2da7e1321a91373000` / `build/destroy-fix-green.log`.
- Mobile APK compilation/R8/packaging/signature verification passed under `build/destroy-fix/`, with version 2.0.8 / 28. The earlier API 34 UI results remain recorded below; this targeted fix was checked with JVM/TLS regressions and an APK rebuild, without repeating UI instrumentation.
- Published 2.0.8 artifacts and GitHub refs remain unchanged.

## Mobile test rename and local review — 26 October 2026

- Mobile test sources, fixtures and instrumentation manifest are now under `tests/android-mobile/`. Six file hashes were unchanged by the move; all active path references were updated.
- The renamed entry point passed JVM/TLS checks, built/installed the instrumentation APK and passed the complete API 34 mobile touch/UI suite: `build/mobile-test-5d8b708f77e648979ca34472ae54ea3d`.
- The review covered the complete unpushed refactoring, including new workflow/controller/dialog/style classes and the adapted tests.
- The review recorded a P3 opportunity: `SenderController.destroy()` stopped discovery before closing its operation. In an isolated controller test, queued work could reach the transport while discovery shutdown waited (`build/review-destroy-order.kt`, log `build/review-destroy-order.log`). The Activity already stopped discovery in `onStop()`, so its normal close-path regression was not demonstrated. The initial P2 suspicion was downgraded after checking that lifecycle. No high/medium regression was confirmed. The subsequent fix recorded above resolves P3.
- No application version or published release file was changed; no commit or push was performed.

## Local window/controller/dialog refactoring — 26 October 2026

- Rebuilt all three apps under `build/refactor-final/` with unchanged 2.0.8 / Android code 28 metadata. APK signatures, pinned certificate, package/SDK metadata, alignment, exact APK/JAR pairing and the final source fingerprint were verified. Published files in `outputs/TVText-2.0.8/` retain their previous checksums; no commit, push or tag change was made.
- Direct Windows workflow tests now exercise validated pairing, saved names, explicit write failures, revocation, cancelled work, certificate/identity rejection and one verified address-recovery proposal after an unsuccessful send without replay. Full Windows/TLS/DPI/focus/UI tests passed: `build/test-c87d9cf97ecc491aa862334e8b96342b`.
- Compared all 35 corresponding Windows snapshots with `build/test-76dac2cf484d4c7383b6073bea00aa8c`: every image is pixel-identical.
- Direct mobile controller tests use deterministic worker/UI queues and fake discovery/transport without Android classes. They cover an edited draft, rejected and unconfirmed sends, no replay, endpoint-save failure after confirmed delivery, pin rejection, stale scan callbacks, destruction before background execution and after a response is queued, unpair failure/success, and explicit pairing confirmation.
- Mobile JVM/TLS and actual-touch UI suites passed: `build/mobile-test-85a206b9586d40088bd6818aded8fbe8`. Startup-state and dialog fixtures now read the extracted components instead of removed Activity fields. The first device attempt exposed an outdated test-field lookup; the fixture was corrected and the complete device workflow rerun.
- TV UI passed: `build/tv-ui-test-4646fc0f1cc0400c9b47ee26f1684de7`. Existing dialog/focus/contrast/insertion checks remain, with an added recreation test while the keyboard dialog is open: old dialog dismissed, allowed test target closed, reopened editor empty.
- Android device checks use the isolated API 34 emulator. The matrix at `build/refactor-compatibility-final.json` explicitly marks other API levels and physical devices untested. The test emulator was stopped afterward.

## Final 2.0.8 rebuild after hardening — 25 October 2026

- Replaced the requested local release under `outputs/TVText-2.0.8/`: TV APK (82,895 bytes), mobile APK (74,136 bytes), EXE (109,056 bytes), and EXE configuration. APK version/code: `2.0.8` / `28`; Windows assembly/file version: `2.0.8.0`.
- Full `prepare-release.ps1 -Device emulator-5580` workflow passed. APK package/SDK/version metadata, alignment and the pinned signing certificate were verified; both APKs retain the existing update identity. The final source fingerprint matches their build records. Windows Authenticode remains unsigned.
- Final Windows/TLS/UI: `build/test-76dac2cf484d4c7383b6073bea00aa8c`; mobile JVM/TLS/UI: `build/mobile-test-b7b4a6e43aa94bc6ba66015b154191e4`; TV UI: `build/tv-ui-test-7219ba35c8b64a01a4f9519e6694a70b` — all passed. Android checks ran on API 34; the TV suite passed the new cursor/selection/Paste rejection/clipboard checks.
- Windows PowerShell 5.1 tooling: `build/build-tests-63a6a14306214731a15450801be1ccbf` — passed. The negative signing-certificate check also passed.
- The output folder contains exactly four application assets plus `SHA256SUMS.txt` and `release-report.json`; copied file hashes were rechecked. Local build records remain at `build/release-755150c4ba454e72b9de1d01ad4137a6/artifacts/`, not in the release set. The test emulator was stopped. No commit/upload was made.
- Physical TVs and native runtimes other than Android 14 remain **not tested**; the generated compatibility matrix records this explicitly.

| Asset | SHA-256 |
| --- | --- |
| TV APK | `1d06f82c581ec9d418dfadaaaff714796fed135ca202fc67dadf0fa049665dc4` |
| Mobile APK | `50806ab26bc122dbf28cdb123bd50195d1a18b5a71b2ca45381959bfc5178e5d` |
| Windows EXE | `87fd4ab56a580efde135fb4156d4bfd95de482111d7d05e8dffc4e1aaec60af5` |
| EXE configuration | `8d77ce46414815bd762d840d78d2339bfffb257a19e31a863081b660226dbf27` |

## Local hardening checks — 25 October 2026

- Added Windows disk-write failure checks: verified session address and draft remain usable, old disk contents survive, ordinary status clearing cannot hide a save warning, and a later successful save clears it.
- Off-screen focus checks use logical active controls with a bounded event-loop wait; they check native focus when the test window owns it. Tests never force the expected control into focus.
- A child printing PASS and then exiting with code 7 is rejected. The TLS fixture requires both a zero exit code and its final assertion marker. Native test exceptions go to stderr rather than a system exception dialog.
- Mobile catalog checks cover private state, discovery credential isolation, failed pair/forget/rename/address writes, aliases and rediscovery without phantom pairings.
- TV checks cover cursor/selection insertion, missing or rejected Paste without SetText fallback, and restoration of an initially empty/nonempty clipboard after rapid inserts or user copying.
- Release helpers reject unexpected assets, checksum tampering and a different APK signing certificate. Tooling checks also run under Windows PowerShell 5.1.
- `prepare-release.ps1` runs local builds, signature/version checks and selected tests; `test-compatibility.ps1` records actual API levels and unavailable environments. See [building](../building/BUILD.md) and the [compatibility matrix/manual checklist](COMPATIBILITY.md). No physical-TV result is inferred from emulator tests.

## Initial local 2.0.8 build — 25 October 2026 (superseded)

- TV APK: `TVText-TV-2.0.8.apk`, package `com.tvtext.app`, Android version code `28`.
- Mobile APK: `TVText-Mobile-2.0.8.apk`, package `com.tvtext.mobile.app`, Android version code `28`.
- Windows: `TVText-2.0.8.exe` / `2.0.8.0`, with `TVText-2.0.8.exe.config`.
- These hashes describe the initial build before the hardening changes. APK signatures and APK/EXE version metadata were verified at that time; the final rebuild replaces files under `outputs/TVText-2.0.8/`.
- SHA-256: TV APK `16B5933F7EAF7F613B1821CEB3D88BC5C8193F6B4EDA56B94CB74DAB365A0D3F`; mobile APK `9EAA3AE9B3066E144C5C9C45D0B4615B8D27A58FA4E7E3D901DBD06650BEB58D`; Windows EXE `9AFB2007B5A2399B416D3838D9D716C5DA773D3BE140B651F8D410B766349408`.
- The mobile application directory is now `android-mobile/`. Build records use target `android-mobile` and locate the exact classes at `build/run-*/android-mobile/app.jar`. Mobile test sources are in `tests/android-mobile/`.
- Directory rename verification compared all 15 file hashes before and after the move. Build-system checks passed, including source paths, Windows isolation and APK/JAR/source matching.
- Both APK signing certificates match 2.0.7, and zip alignment checks passed. APKs remain compatible with in-place updates using the same application IDs.
- Full Windows/TLS/UI suite: `build/test-59c44d4fa721411da60d486d20b93839` — passed. The first off-screen rename-focus check was rechecked independently and in the complete rerun without changing dialog code.
- Full mobile JVM/TLS/UI suite: `build/mobile-test-dc0392278e794e26882afc09217a0cc9` — passed on the isolated Android 14 emulator.
- Full TV UI suite: `build/tv-ui-test-6f56b92828d04e7f98f122f9889a46f7` — passed, including both insertion bridges, device-list dialogs and remote-control focus.

## Historical release 2.0.7 artifacts

- TV APK: `TVText-TV-2.0.7.apk`, package `com.tvtext.app`, version code `27`.
- Mobile APK: `TVText-Mobile-2.0.7.apk`, package `com.tvtext.mobile.app`, version code `27`.
- Windows EXE: `TVText-2.0.7.exe`, version `2.0.7.0`.
- APK signatures and APK/EXE version metadata were verified during the 2.0.7 build.
- SHA-256: TV APK `EC82FE3CF7D252A9ECC4137212608999503D4F3AE1B46337CF9A426C0038F75A`; mobile APK `9611B084DF4840F804D578A33A3999E69F8E377083345D9C34B0263451C26209`; Windows EXE `B2B96875C0FE31980D437E7DC88AF43FB3C577A9D300ABC93D9479370D1ADBB4`.

Review regression checks cover actual mobile touch events (including cancellation and dragging outside controls), migration of encrypted profiles/local names, removal of absent renamed TVs, Windows active-profile synchronization and localized response codes, and legacy clipboard restoration after rapid TV inserts or an intervening user copy.

The last full review suite passed before the 2.0.7 version-only rebuild: Windows/TLS `build/test-269701adc95b4d3e98de29408c432350`, mobile JVM/TLS/UI `build/mobile-test-cd3b2bcdebf141a58de58b492c965e6d`, and TV UI `build/tv-ui-test-5cba03cdecc0460c9dc2f831d7f03663`. The APK UI checks ran on the isolated Android 14 emulator. Version 2.0.7 was not retested because it changes release metadata only; no protocol or runtime source changes were introduced.

The detailed 2.0.4 verification history remains below for historical reference.

Local build/refactoring checks after this release are recorded separately below. They do not change the release hashes above. The build workflow and APK/JAR matching requirements are documented in the [build guide](../building/BUILD.md).

## Local build and refactoring verification — 25 October 2026

- All three applications were rebuilt under `build/refactor-2.0.7-final/`. Version metadata remains `2.0.7` / Android code `27`; the published release files in `outputs/TVText-2.0.7/` were preserved.
- Build-system checks passed in PowerShell 7 and Windows PowerShell 5.1, including Windows-only builds with deliberately missing Android paths, relative output paths, version validation, missing records and mismatched APK/JAR/source hashes. Final Windows PowerShell run: `build/build-tests-376474bedcd344f394e98b1dc5cf4e21`.
- Full Windows/TLS/UI checks and TV engine/status regression tests passed: `build/test-d3b8b02c1bae4ed09161cb54ea516015`.
- Mobile JVM/catalog/TLS and real Android UI checks passed on the final build: `build/mobile-test-4ecee4aadab442dd889871ae36ac18e8`. The scan/theme state check now runs synchronously and seeks an animation frame explicitly, avoiding a race with the five-second scan timeout; native pointer checks remain for other interactions.
- TV UI checks passed on the final build: `build/tv-ui-test-989680553deb4e0cba4895a77ec03f36`, including both insertion bridges and clipboard restoration.

## Historical 2.0.4 verification

The 2.0.4 release artifacts were checked for:

- EXE `AssemblyVersion`, `FileVersion`, and `ProductVersion`: `2.0.4.0`;
- both APK `versionName`: `2.0.4`, `versionCode`: `24`;
- APK metadata, v3 signatures, zip alignment, and matching release certificate;
- the Windows sidecar name `TVText-2.0.4.exe.config`;
- a source tree with tests grouped under `tests/android-tv/`, `tests/android-mobile/`, and `tests/windows/`.

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
  -AppApk '.\dist\TVText-Mobile-2.0.8.apk'
```

`test.ps1` checks the build helpers, compiles the C# Windows client and Kotlin TV fixture, runs local TLS/DNS-SD tests, and renders Windows Forms checks off-screen. `test-mobile.ps1` first runs JVM catalog/protocol checks; with `-Device` it additionally builds an instrumentation APK, installs the matching application APK, and runs tests inside the selected emulator. `test-tv-ui.ps1` runs the TV instrumentation suite. Both device scripts validate the adjacent APK build record and exact JAR/source hashes before installing anything.

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
