# Windows EXE changes

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
