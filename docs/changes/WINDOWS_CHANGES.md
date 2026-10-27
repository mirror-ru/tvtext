# Windows EXE changes

## 27 October 2026 — local version 2.0.10 rebuild

- Updated the shared version to 2.0.10 / Windows 2.0.10.0, including the current address recovery and multi-window profile preservation fixes. Functional results for those changes are recorded in the test report.

## 27 October 2026 — shared profile and release consistency (local, unreleased)

- Selecting a TV now saves only the active ID from the latest on-disk profile list. It cannot overwrite another window's new certificate/key/address or resurrect a removed profile.
- Loaded/saved connections retain an in-memory persistence snapshot. Subsequent writes reject changed or deleted on-disk profiles under the existing cross-process lock, preserving newer pairings and addresses. The stored format is unchanged; explicit new pairing can still replace credentials.
- Release preparation captures its source fingerprint before building, rejects changes immediately after the build, and validates APK/JAR/source build records in both Core and Full modes before and after tests. Added storage-conflict and release-entry regressions.

## 27 October 2026 — recovery after address changes (local, unreleased)

- Manual refresh can authenticate a blocked pairing even when the original TV returns at its saved address. Normal send verification still skips unchanged addresses, so this adds no routine status polling.
- Before sending to a saved TV, check any different address already found by discovery using the saved certificate, device ID and pairing token. This covers switching to a TV whose address changed while another TV was selected, without polling on selection or starting an extra scan for a normal send.
- A certificate/identity mismatch at the old address now triggers bounded discovery and authenticated status verification. Successful recovery keeps the existing pairing and preserves the draft; it never repeats the text automatically. Manual refresh can also restore a pairing previously blocked by the wrong endpoint.
- A revoked token no longer qualifies as successful connection recovery. Updated the recovery message to ask users to check the TV before retrying, and added workflow/TLS/UI regressions.

## 26 October 2026 — shared build verification (local, unreleased)

- Source fingerprints now sort normalized relative paths with ordinal comparison, so identical sources produce the same fingerprint in Windows PowerShell 5.1 and PowerShell 7. This fixes APK/JAR verification across those hosts and applies to all application targets.
- Added a cross-host tooling regression. Existing release binaries are retained; local test builds use fresh build records.

## 26 October 2026 — local version 2.0.9 rebuild

- Rebuilt Windows with assembly/file/product version 2.0.9.0 generated from `version.json`, including the current sender-workflow refactoring.
- Output: `outputs/TVText-2.0.9/TVText-2.0.9.exe`, 110,080 bytes, plus its `.exe.config` sidecar. EXE SHA-256: `914f26c7d55d3b48cc288e456bcd3d0026603a750921dcf0106227364290876d`.
- Build tooling checks passed: `build/build-tests-fb0fa9ba51384182950c9e77c0c881e8`, including Windows-only dependency isolation and derived-version validation.
- The output directory contains exactly four application assets. APK build records, checksums and verification metadata remain local under `build/rebuild-2.0.9/`.
- The application sources were subsequently pushed to `main` in [65c2643](https://github.com/mirror-ru/tvtext/commit/65c26435066e2288e03f64b13a9c14a550ae3a5a). The 2.0.9 binaries remain local; that push did not create a version tag or GitHub Release. Previous 2.0.8 artifacts remain unchanged.

## 26 October 2026 — sender scenarios separated from the window

- Added `SenderWorkflow.cs` for discovery, certificate probing, pairing validation, sending, verified address recovery, remote revocation and explicit persistence results. Transport/discovery/storage dependencies can be substituted in tests; the workflow does not depend on Windows Forms.
- `MainForm` retains layout, prompts, current selection/lifetime checks and rendering. Recovery returns a proposal which is applied only while the initiating selection is still current. Failed sends never replay text.
- Preserved existing profile names after pairing, including an advertisement without a device ID. Removed the unused legacy saved-profiles dialog, which no longer had callers.
- Added direct workflow tests for confirmation, one bounded recovery, certificate/identity rejection, pairing validation, save failure, revocation and cancellation. The existing Windows/TLS/DPI/focus suites passed.
- Compared 35 Windows UI snapshots with the previous verified build: every compared image is pixel-identical.
- Version metadata remains 2.0.8. Local verification builds are under `build/refactor-final/`; published output files and GitHub refs are unchanged.
- Final Windows/TLS/DPI/focus/UI suite: `build/test-c87d9cf97ecc491aa862334e8b96342b` — passed.

## 25 October 2026 — persistence warnings and reliable release checks

- Selection, pairing and verified address updates use one save path. A failed disk write keeps the connection usable in the session and adds a localized per-TV warning; clearing ordinary status cannot silently erase it. Successful saving or unpairing clears the warning.
- Address recovery distinguishes an address saved to disk from one available only in the current session. Failed sends are never automatically replayed.
- Added a real storage-failure regression checking preserved disk contents, session address, draft and Send availability, warning lifetime, and a subsequent successful write.
- Off-screen focus checks verify the app's logical active control, selection and (when owned) native focus with a bounded wait, without forcing focus from the test.
- Test exceptions are written to stderr with a nonzero exit code. The save/UI regression runs in its own process so it cannot install a WinForms synchronization context before synchronous network checks.
- TLS fixture success now requires both exit code zero and the final assertion marker. Retain the live process handle; a PASS line no longer suppresses a failing process. Exit-code fixtures test PASS followed by exit 7.
- Added `prepare-release.ps1` for local builds, pinned APK-certificate/version/SDK/alignment checks, core/full test policies, a clean four-asset bundle, SHA-256 and an explicit verification report. Existing directories are not overwritten; nothing is uploaded.
- Final verified files retain version 2.0.8.0 as requested.
- Final Windows/TLS/UI suite passed: `build/test-76dac2cf484d4c7383b6073bea00aa8c`; the complete local release-preparation workflow also passed. EXE SHA-256: `87fd4ab56a580efde135fb4156d4bfd95de482111d7d05e8dffc4e1aaec60af5`.

## 25 October 2026 — version 2.0.8 rebuild

- Windows version is now `2.0.8.0`, generated from the shared `version.json`.
- Output files are `outputs/TVText-2.0.8/TVText-2.0.8.exe` and `TVText-2.0.8.exe.config`.
- The build includes the previously verified device-catalog and pairing-dialog refactoring.
- EXE SHA-256: `9AFB2007B5A2399B416D3838D9D716C5DA773D3BE140B651F8D410B766349408`.
- Full Windows/TLS/UI checks passed: `build/test-59c44d4fa721411da60d486d20b93839`. An initial off-screen focus assertion was rechecked successfully in isolation and in the complete rerun; no dialog code changes were needed.

## 25 October 2026 — local build and maintainability improvements

- Windows-only builds now validate only the C# compiler and do not require Android SDK/JDK/Kotlin or create APK signing material.
- Windows version attributes and manifest identity are generated from `version.json` below the ignored build directory.
- Extracted device-list construction, local-name presentation and active-profile lookup into `ConnectionCatalog`.
- Moved the pairing dialog into its own source file; layout and input behavior are retained.
- Added catalog tests for recipient stability, explicit removal and credential isolation, plus PowerShell regression checks for build dependencies and artifact matching.
- These local changes follow the published 2.0.7 snapshot; release files are not replaced.
- Verification: Windows/TLS/UI run passed at `build/test-d3b8b02c1bae4ed09161cb54ea516015`; final build-system checks passed in Windows PowerShell 5.1 at `build/build-tests-376474bedcd344f394e98b1dc5cf4e21`.

## 25 October 2026 — version 2.0.7 rebuild

- Rebuilt the Windows client as version `2.0.7.0`.
- Release files: `outputs/TVText-2.0.7/TVText-2.0.7.exe` and `TVText-2.0.7.exe.config`.
- EXE SHA-256: `B2B96875C0FE31980D437E7DC88AF43FB3C577A9D300ABC93D9479370D1ADBB4`.
- This is a version-only rebuild after the 2.0.6 review; runtime behavior and the protocol are unchanged.

## 25 October 2026 — review fixes

- Synchronized the selected list row and active connection whenever the device list is rebuilt. Removing a selected TV now immediately activates the remaining saved TV; removing the last TV clears the active connection.
- Unified profile removal for the main unpair action and the saved-profile dialog.
- Pairing/send results now use localized RU/EN messages based on protocol result codes. Russian receiver text is no longer shown directly in the English UI, and successful sending remains silent.
- Added regression checks for the remaining TV's send availability after removal and localized messages for all supported response codes.
- Full Windows/TLS checks passed: `build/test-269701adc95b4d3e98de29408c432350`.

## 25 October 2026 — version 2.0.6 rebuild

- Rebuilt the Windows client as version `2.0.6.0`.
- Release files: `outputs/TVText-2.0.6/TVText-2.0.6.exe` and `TVText-2.0.6.exe.config`.
- EXE SHA-256: `9E7122040066396B98509D8000EEFD39DA889C31724F3FCC6B3290089536CE9A`.

## 25 October 2026 — numeric pairing code and stale device removal

- The `Подключим ваш ТВ` / `Connect your TV` code field now accepts digits only. Keyboard input and pasted text are both sanitized, with the existing eight-digit validation retained.
- Fixed the device-list refresh after unpairing: an old selected TV is no longer reinserted as a fallback when it has disappeared from discovery. A TV that is still advertised can reappear as an unpaired device, while an absent TV is removed from the list.
- Windows UI/TLS tests passed: `build/test-699ad287c7364c0bb79876a35e7ed2c1`.
- Rebuilt: `outputs/TVText-2.0.5/TVText.exe` and `TVText.exe.config`.
- EXE SHA-256: `D304442C0A880D2A8FF8E13D35FAFF52856CE1292769E6236054CBB7F0A13267`.

## 24 October 2026 — version 2.0.4 rebuild

- Rebuilt the Windows client as version `2.0.4.0` without changing the already verified UI or protocol behavior.
- Release files: `outputs/TVText-2.0.4/TVText-2.0.4.exe` and `TVText-2.0.4.exe.config`.
- EXE SHA-256: `865350F28E2EE82B1C588782697B4DAD545B48CD3F33A4EC0EC8C279EDF5646A`.

## 24 October 2026 — night mode review and rename dialog

- Moved the theme palette and saved preference into `windows/Theme.cs`.
- Fixed square card corners, light illustration/badge fills in night mode, native foreground colors after switching, and the rounded input's corner and interior fills.
- Separated the readable accent color from the primary button/check background; primary and secondary button text now maintains at least 4.5:1 contrast in both themes, including hover.
- Theme switching updates the Windows title bar and native control theme where supported by Windows. System message boxes retain Windows-managed appearance.
- The theme button now announces the next action and current mode in RU/EN. Its icon scales with DPI, and the centered footer hint fits beside both settings buttons.
- Refined the rename dialog with a TV badge, balanced spacing, a vertically centered single-line input, and aligned Cancel/Save buttons. The helper now reads `Имя будет видно только на этом устройстве` without a final full stop.
- Added theme checks for preference reload/defaults, repeated live switching, preserved draft/text selection/TV selection, focus, contrast, rendered rounded corners, and main/pair/rename layouts in RU/EN at 100–200%, including the release DPI manifest. Tests use an isolated preference file and off-screen forms.
- Verification: the full Windows/TV suite passed (`build/test-c2a1e63c5a38482d89022ec314047fee`); after the final button-color adjustment, theme and layout checks passed again at 96 and 120 DPI. The EXE builds without warnings.

## 24 October 2026 — device-local status messages
- The pairing hint under an unpaired TV now reads `Нажмите на кнопку «Подключить»` (`Click the “Connect” button` in English).
- The `Сопряжён` caption uses the neutral text color; transient hints and errors use the same neutral color.
- Clicking a paired TV now moves focus directly to the text editor; unpaired TVs keep focus on the device row for the Connect action.
- Startup discovery now runs only when there are no saved paired TVs. With at least one saved pairing, the list opens immediately and discovery remains manual through Refresh; endpoint recovery still happens when needed for sending.
- Added a theme button beside RU: day mode is the default, night mode updates the main window and dialogs, and the choice is saved locally for the next launch.
- Removed the F2 rename shortcut. Windows EXE wording now uses «устройство» instead of «ПК/компьютер» in user-facing text.
- The common status line below the TV list is intentionally kept empty and no longer shows connection, pairing, success, rename, unpair or refresh messages.
- TV-specific action and error messages are rendered under the selected TV name: pairing in progress, renewed pairing, send confirmation failures, and TV availability errors.
- An empty discovery result stays silent: the TV list remains empty without an informational message. Discovery errors still include the underlying cause and a Wi-Fi check hint.
- Selecting a saved TV, successful pairing, endpoint recovery, rename, unpair and delete now finish silently; the row itself reflects the resulting state (`Сопряжён` or the pairing hint).

## 24 October 2026 — version 2.0.3 and smoother refresh animation

- The Windows EXE version is now `2.0.3.0`; Android package versions remain unchanged.
- Refresh animation now runs at a 16 ms timer interval and derives its angle from elapsed time, so timer jitter does not change the rotation speed.
- Built artifact: `outputs/TVText-2.0.3/TVText-2.0.3.exe` with `TVText-2.0.3.exe.config` beside it. EXE SHA-256: `730ABCAF036B1396ED5E48A168C8A78DE6BBE2046B6C89C704EB1503FD904481`.
- The source archive was intentionally not rebuilt for this EXE-only version bump.

## 24 October 2026 — refresh feedback

- The refresh button now rotates while mDNS discovery is running.
- The status line no longer shows `Ищем телевизоры в сети…` / `Searching for TVs on the network…`.
- The status line no longer reports `Список телевизоров обновлён` / `TV list updated` after a successful refresh with an active selection.
- A useful result remains visible when needed: an unpaired TV asks the user to connect, and an empty result reports that no TV was found. Discovery errors still show an error message.
- Added a UI test that checks the spinner advances during discovery and stops when the operation is cancelled or finishes.
