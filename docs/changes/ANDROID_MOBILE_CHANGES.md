# Android phone/tablet changes

## 27 October 2026 — local version 2.0.10 rebuild

- Updated the shared version to 2.0.10 / Android code 30, including the current address recovery and deferred verification fixes. Functional results for those changes are recorded in the test report.

## 27 October 2026 — recovery after address changes (local, unreleased)

- If a recovery scan finishes while another TV is sending, retain its verification until the current operation completes. Cancelled scans, destruction and replaced profile credentials invalidate this deferred check; no draft is replayed.
- A certificate/identity mismatch at a stale address now triggers a bounded discovery scan followed by a status request with the original saved certificate and token. Only successful verification restores the pairing and remembers the new address; the draft is never sent automatically.
- Manual refresh can revalidate the selected blocked pairing if the TV was absent during the first recovery attempt. Recovery cancels an older scan, rejects stale/cancelled verification results and does not trust credentials from discovery.
- Added controller scenarios covering address recovery, later manual recovery, untrusted certificates, revoked tokens, cancellation and destruction. Updated the RU/EN recovery message to ask users to check the TV before repeating a send.

## 26 October 2026 — review fixes (local, unreleased)

- Preserve discovered TV advertisements and invalid-pairing state across activity recreation without saving credentials in the activity state. An interrupted scan resumes with its already discovered TVs; a fresh manual scan still clears stale results. Saved pairings continue to suppress unsolicited startup scans.
- Retry `FAILURE_ALREADY_ACTIVE` within the existing five-second NSD scan instead of dropping the device. If the old resolve remains active until the deadline, show a localized message asking the user to refresh again; stop and stale callbacks cannot restart a retry.
- Added catalog/controller and Android instrumentation checks for recreation, interrupted scans, stale callbacks, bounded retries and cancellation.
- The Android 12 background clipboard limitation belongs to the TV receiver and remains documented separately.

## 26 October 2026 — native Android 9 compatibility

- The unchanged 2.0.9 mobile APK passed core/controller/TLS and the complete instrumentation suite on an isolated official AOSP API 28 emulator, including startup, native NSD/Keystore, pairing/sending and UI behavior.
- Results: `build/mobile-test-b963055081ab4fd39a53b8f3c9c8f388`; log `build/api28-compatibility.log`; compatibility report `build/api28-compatibility.json`.
- No application changes or APK rebuild were required. The native result covers the tested AOSP image; physical devices and other Android versions retain their separate test status.

## 26 October 2026 — native Android 12 compatibility

- The unchanged 2.0.9 mobile APK passed JVM/controller/TLS and the complete instrumentation suite on a new isolated official AOSP API 31 emulator.
- Verified native NSD, saved-pairing startup policy, refresh/cancellation, Keystore, TLS, Unicode delivery, draft/selection behavior, rename/revoke/pairing dialogs, RU/EN, themes and recreation. Results: `build/mobile-test-2b55480f4a194805ac17cdaf7c686c03`, log `build/api31-compatibility.log`.
- The subsequent API 28 verification is recorded above. Application sources, release files and Git refs were not changed by this verification.

## 26 October 2026 — local version 2.0.9 rebuild

- Updated the shared version to 2.0.9 / Android code 29 and rebuilt the current mobile sources, including the controller/dialog separation and P3 destruction-order fix.
- Verified package `com.tvtext.mobile.app`, minSdk 28 / targetSdk 34, APK alignment, the existing signing certificate and exact APK/JAR/source pairing. This was a metadata-only version bump after the recorded functional checks.
- Output: `outputs/TVText-2.0.9/TVText-Mobile-2.0.9.apk`, 78,229 bytes; SHA-256 `0b51c25d2ce17d83beb48bc23e9d7d5189a603be6d72ba3a34cd7eeb04cf3b60`.
- Source guides and current download examples now reference 2.0.9. The application sources were subsequently pushed to `main` in [65c2643](https://github.com/mirror-ru/tvtext/commit/65c26435066e2288e03f64b13a9c14a550ae3a5a). The 2.0.9 binaries remain local; that push did not create a version tag or GitHub Release. Existing 2.0.8 files remain unchanged.

## 26 October 2026 — P3 destruction-order finding resolved

- `SenderController.destroy()` now marks the controller destroyed, cancels the current operation and shuts down queued work before stopping discovery. Discovery shutdown runs in `finally`; late callbacks remain ignored.
- Repeated destruction is a no-op. A failure/delay in discovery shutdown cannot leave the request uncancelled or postpone worker shutdown.
- Added regression cases for a task already removed from the executor queue resuming during discovery shutdown, a discovery-stop exception, and repeated destruction. The new test failed on the old order (`build/destroy-fix-red.log`) and passed after the fix.
- Mobile JVM/controller/TLS suite passed: `build/mobile-test-0cdeb02bdc734e2da7e1321a91373000`. Mobile APK rebuilt under `build/destroy-fix/`; version remains 2.0.8 / 28. Published files and GitHub refs were not changed.

## 26 October 2026 — mobile test directory and local review

- Renamed the six-file mobile test directory to `tests/android-mobile/`. File hashes were compared before/after moving; package names and the instrumentation target remain unchanged.
- Updated all active test-script, README, build-guide and test-report paths. The test directory now matches the application source directory name.
- After the rename, mobile JVM/TLS, test-APK packaging and API 34 touch/UI instrumentation passed: `build/mobile-test-5d8b708f77e648979ca34472ae54ea3d`.
- Review found one low-priority hardening opportunity in `SenderController.destroy()`: close the operation before stopping discovery. An isolated controller reproduction (`build/review-destroy-order.kt`) lets queued work run while discovery shutdown waits. The current Activity already stops discovery in `onStop()`, so a regression in its normal close path was not demonstrated; the initial P2 suspicion was downgraded to P3 after checking that lifecycle. Runtime sources were not changed by that rename/review task; the subsequent fix above resolves the finding.

## 26 October 2026 — controller and dialogs separated from the activity

- Added `SenderController.kt` for search, probing/pairing, send/unpair/rename operations, persistence failures and request lifetime. It uses the existing catalog and transport with injectable worker, UI dispatcher and discovery/transport adapters.
- Search generations reject callbacks from stopped/replaced scans. Destroying the controller closes its operation, stops discovery, shuts down its worker and ignores already queued response callbacks.
- Added `SenderDialogs.kt` for pairing-code/fingerprint/rename/unpair dialogs, retaining their layouts, validation, styles and focus. `EditorStyle.kt` keeps native editor colors/caret styling shared with the main text field.
- `MainActivity` now binds UI actions to the controller and renders state. A confirmed send clears only the unchanged original draft; a storage warning remains visible after delivery when saving the endpoint fails.
- Added direct JVM scenario checks for changed drafts, rejected/uncertain sends, no replay, pin rejection, address/save failure, stale discovery, destruction before/after background work, unpair and confirmed pairing.
- Version metadata remains 2.0.8; verification artifacts stay in `build/refactor-final/`. No release files or GitHub refs were replaced.
- Final JVM/TLS and touch/UI instrumentation passed on API 34: `build/mobile-test-85a206b9586d40088bd6818aded8fbe8`.

## 25 October 2026 — encapsulated connection state

- Made catalog maps/sets private. Activity now uses explicit restore/discovery/pair/forget/invalidate/endpoint/rename operations rather than mutating shared collections.
- Discovery advertisements cannot introduce pairing credentials or local aliases. Discovery overlays keep each saved device's own credentials, and catalog snapshots do not expose its mutable storage.
- Failed pair/forget/rename/endpoint writes leave the catalog unchanged. A send confirmed by the TV clears its original draft but no longer hides a subsequent endpoint-save error.
- Expanded catalog regression checks for failed writes, rediscovery after unpair, aliases, state isolation and endpoint persistence. UI fixtures now use catalog operations.
- Device test output reports the actual Android API; the compatibility runner distinguishes tested and unavailable versions.
- Release metadata remains 2.0.8 / 28; final verified local artifacts are rebuilt as requested.
- Final mobile JVM/TLS/UI checks passed on API 34: `build/mobile-test-b7b4a6e43aa94bc6ba66015b154191e4`. APK SHA-256: `50806ab26bc122dbf28cdb123bd50195d1a18b5a71b2ca45381959bfc5178e5d`.

## 25 October 2026 — version 2.0.8 and android-mobile directory

- Renamed the mobile application source directory from `android/` to `android-mobile/`, preserving all source/resource files.
- Updated mobile build/test paths, source fingerprints and build-record target names to `android-mobile`.
- Kept mobile test sources in `tests/android-mobile/`; the application ID remains `com.tvtext.mobile.app`, so the folder rename does not require reinstalling the app.
- Application version is `2.0.8`, Android version code `28`; output is `outputs/TVText-2.0.8/TVText-Mobile-2.0.8.apk`.
- APK SHA-256: `9EAA3AE9B3066E144C5C9C45D0B4615B8D27A58FA4E7E3D901DBD06650BEB58D`. The signing certificate matches 2.0.7.
- Full mobile JVM/TLS/UI checks passed from the renamed source tree: `build/mobile-test-dc0392278e794e26882afc09217a0cc9`.

## 25 October 2026 — local build and maintainability improvements

- Moved application version/code to `version.json` and pinned Android/JVM tools in `toolchain.json`.
- Device tests now require a matching APK build record, verify APK/JAR/source hashes, and never guess classes from the newest build directory.
- Extracted device merging, pairing flags, local names and persistence commit rules into `ConnectionCatalog`. Failed writes leave the in-memory state unchanged.
- Expanded lifecycle/discovery/background-operation code for readability; view layout and focus behavior are retained.
- Added JVM regression tests for credential preservation after discovery, name retention after unpairing and failed-write isolation.
- Made the scan/theme state-transition check independent of Instrumentation idle delays; native pointer checks remain for user interaction.
- These local changes follow the published 2.0.7 snapshot; release files are not replaced.
- Verification: final mobile JVM/TLS/UI run passed at `build/mobile-test-4ecee4aadab442dd889871ae36ac18e8`; APK/JAR/source records match.

## 25 October 2026 — version 2.0.7 rebuild

- Rebuilt the mobile APK as version `2.0.7`, version code `27`, with package `com.tvtext.mobile.app`.
- Release artifact: `outputs/TVText-2.0.7/TVText-Mobile-2.0.7.apk`.
- APK SHA-256: `9611B084DF4840F804D578A33A3999E69F8E377083345D9C34B0263451C26209`.
- This is a version-only rebuild after the 2.0.6 review; runtime behavior and the protocol are unchanged.

## 25 October 2026 — review fixes

- Restored native Android touch handling for text buttons, icon buttons and TV rows. Controls remain keyboard-focusable, while a touch activates them on the first completed tap without consuming it for focus.
- TV selection no longer changes on touch-down, cancelled gestures or list scrolling. Moving a finger outside a button cancels its action and preserves native pressed/ripple feedback.
- Kept keyboard dismissal on TV selection; selecting a TV does not focus the text editor.
- Split encrypted saved state into paired profiles and local names. Unpairing removes the profile even when it has a custom name; the name is reapplied if the TV is discovered again.
- Added automatic reading/migration of the previous encrypted storage format, including name-only unpaired records.
- Mobile UI tests now send actual pointer down/up events. Added cancelled-row, drag-outside, absent renamed TV, rediscovered name and legacy-state migration checks.
- Full mobile JVM/TLS and Android UI checks passed, including the first tap while the editor has focus: `build/mobile-test-cd3b2bcdebf141a58de58b492c965e6d`.

## 25 October 2026 — version 2.0.6 rebuild

- Rebuilt the mobile APK as version `2.0.6`, version code `26`, with package `com.tvtext.mobile.app`.
- Release artifact: `outputs/TVText-2.0.6/TVText-Mobile-2.0.6.apk`.
- APK SHA-256: `2F47F28C45F94F7508A2709B1DE531A0AB6CAC8DACEA6635A121D10CDD1EA73B`.

## 25 October 2026 — new mobile application ID and device selection

- Moved the mobile sender package to `com.tvtext.mobile.app`; this build is intended as a new installation and does not preserve the old application ID.
- Selecting a TV now applies on the first click and keeps focus on the selected row.
- Selecting a TV no longer requests or opens the on-screen keyboard; the keyboard is shown only when the user focuses the text field directly.
- Each mDNS refresh now replaces the previous discovered-device snapshot, so an absent unpaired TV is removed instead of lingering in the list.
- Touch selection is applied on the first touch-down when the editor previously had focus, avoiding the observed two-tap switch.
- Rebuilt artifact: `outputs/TVText-Mobile-2.0.5/TVText-Mobile-2.0.5.apk`.
- APK SHA-256: `20D3F91169380485BAEAFD65FAE736FD54D2BFDBE44B75F74C1D75CB18577756`.
- Mobile JVM/TLS checks passed: `build/mobile-test-419e09298db5405dbb58ae678464a3a2`.

## 24 October 2026 — version 2.0.4 rebuild

- Rebuilt the phone/tablet APK as version `2.0.4`, version code `24`; no behavior changes were introduced.
- Release artifact: `outputs/TVText-2.0.4/TVText-Mobile-2.0.4.apk` (70,040 bytes).
- APK SHA-256: `16C17B1B0B8A339CED29A5A36A4DD79B9B5479822557150E5B2B8423ED211B07`.
- The v3 signing certificate remains the same as the 2.0.3 build, so the APK supports an in-place update.

## 24 October 2026 — Windows 2.0.3 changes adapted to mobile

- Mobile version: `2.0.3`, version code `23`.
- Added a day/night button beside RU/EN. Day mode is the default; the preference is saved locally. Main content, TV rows, artwork, inputs, dialogs and system bars use the selected palette.
- Theme/language changes preserve the draft, text selection, selected TV and scroll position. Settings retain focus after switching.
- Refresh rotates only its icon using the Android animation clock. It stops on completion or when the screen stops, and survives a UI rebuild without leaving an old animation running. Removed searching/list-updated messages.
- Startup discovery runs once only if there are no saved paired TVs. Saved pairings open immediately without a scan; Refresh and send-error endpoint recovery remain available. Discovery is bounded to five seconds.
- Removed the common status line and empty-list instructions. A normal empty result stays blank. Discovery/storage errors without a TV appear inside the list area; other operation messages appear below the relevant TV name. Search failures include the cause and a Wi-Fi hint.
- Successful pairing, sending, rename and unpair complete silently. Paired rows show the neutral `Сопряжён`; unpaired rows show `Нажмите на кнопку «Подключить»` / `Tap the “Connect” button`.
- Tapping a paired TV focuses the text input and requests the on-screen keyboard. No F2 shortcut is added on Android.
- Reserve space for three TV rows; longer lists and input beyond three lines scroll with fading scrollbars. Font scaling is accounted for in row height.
- Updated sender wording to «устройство» / “device”. The TV receiver APK is not changed in this task.
- Refined the rename dialog with a TV badge, rounded themed input, validated Save button, IME Done support and a helper without a final full stop: `Имя будет видно только на этом устройстве`.
- Extended emulator tests for startup policy, refresh animation, TV-local errors, preserved draft/selection, theme persistence, recreation and themed rename. All device tests target the isolated `emulator-5580` only.

### Verification

- Mobile JVM/TLS tests passed, followed by Android 14 instrumentation on a 1080×2400 phone at 420 dpi and a 1600×2560 tablet at 240 dpi. Final run: `build/mobile-test-3f9e6c6fe94e4b53a7fb772e3dcd3aee` (the same installed test APK was then rerun at the tablet size).
- Checks include more than three TVs, unpaired-row wording and focus, actual background/foreground discovery cancellation, paired-row input focus, localized device errors, theme/language switching, saved theme after recreation, and send/pair/unpair behavior.
- Captured and visually checked real emulator screenshots in both themes on phone and tablet.
- APK metadata: `local.tvtext.mobile`, version `2.0.3` / `23`, minSdk `28` (Android 9), targetSdk `34`; small/normal/large/xlarge screens supported. Android 9 compatibility is compiled; runtime checks in this run use Android 14.
- APK signature and zip alignment verified. The signing certificate matches the previous mobile 2.0.2 release, allowing an in-place update.
- Artifact: `outputs/TVText-2.0.3/TVText-Mobile-2.0.3.apk` (70,040 bytes).
- APK SHA-256: `FC83EA1187DEB62C0EDBD61A4C153C7128440A7F980735C093DD5AAD0CCAB283`.
- Phone screenshots: `TVText-Mobile-2.0.3-light.png` and `TVText-Mobile-2.0.3-dark.png`; tablet screenshots: `TVText-Tablet-2.0.3-light.png` and `TVText-Tablet-2.0.3-dark.png`, in the same output directory.
