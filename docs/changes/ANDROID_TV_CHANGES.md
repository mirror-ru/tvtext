# Android TV changes

## 25 October 2026 — safe legacy insertion and compatibility checks

- Removed the Android 9–12 `ACTION_SET_TEXT` fallback, which could replace an entire nonempty field. Only Paste is used; missing/rejected Paste reports failure without reading the editor's text.
- Refresh accessibility actions after preparing the temporary clipboard so editors can expose Paste even when the original clipboard was empty. Clipboard restoration still preserves intervening user copies.
- Dispatch legacy accessibility requests on a dedicated bounded worker instead of blocking the target UI thread. Clipboard session state is synchronized with delayed restoration; request deadlines/revocation checks remain in force.
- Added device checks for insertion in the middle of text, replacing only a selection, SetText-only fields and rejected Paste, alongside the existing rapid-insert/clipboard tests.
- Added an explicit emulator compatibility matrix and a physical-device checklist in both documentation languages. Unavailable Android versions and physical TVs are not reported as passed.
- Release metadata remains 2.0.8 / 28; the final verified build replaces the local 2.0.8 artifacts at the user's request.
- Final TV instrumentation passed on API 34: `build/tv-ui-test-7219ba35c8b64a01a4f9519e6694a70b`. APK SHA-256: `1d06f82c581ec9d418dfadaaaff714796fed135ca202fc67dadf0fa049665dc4`.

## 25 October 2026 — version 2.0.8 rebuild

- Rebuilt the TV receiver using the shared `version.json`: version `2.0.8`, Android version code `28`.
- Application ID remains `com.tvtext.app`; output is `outputs/TVText-2.0.8/TVText-TV-2.0.8.apk`.
- The build includes the previously verified receiver-state refactoring.
- APK SHA-256: `16B5933F7EAF7F613B1821CEB3D88BC5C8193F6B4EDA56B94CB74DAB365A0D3F`. The signing certificate matches 2.0.7.
- Full TV UI checks passed: `build/tv-ui-test-6f56b92828d04e7f98f122f9889a46f7`.

## 25 October 2026 — local build and maintainability improvements

- Version and Android version code are injected from `version.json`; the Android/JVM build environment is pinned in `toolchain.json`.
- Added APK/JAR build records and strict matching checks before device tests install an APK.
- Extracted receiver setup/preparation/error/pairing/ready-state selection into `ReceiverStatus`, independent of Android views.
- Added regression checks for state priority and compact-card behavior; the existing layouts, colors and button visibility are retained.
- These local changes follow the published 2.0.7 snapshot; release files are not replaced.
- Verification: final TV UI run passed at `build/tv-ui-test-989680553deb4e0cba4895a77ec03f36`; engine/status checks passed at `build/test-d3b8b02c1bae4ed09161cb54ea516015`.

## 25 October 2026 — version 2.0.7 rebuild

- Rebuilt the TV APK as version `2.0.7`, version code `27`, with package `com.tvtext.app`.
- Release artifact: `outputs/TVText-2.0.7/TVText-TV-2.0.7.apk`.
- APK SHA-256: `EC82FE3CF7D252A9ECC4137212608999503D4F3AE1B46337CF9A426C0038F75A`.
- This is a version-only rebuild after the 2.0.6 review; runtime behavior and the protocol are unchanged.

## 25 October 2026 — review fixes

- Consecutive legacy clipboard inserts share one original clipboard snapshot. Completing the last insert restores that original instead of leaving the preceding TVText message in the clipboard.
- Each temporary clipboard write carries a unique marker. Restoration does not overwrite text copied by the user or another app between inserts.
- Added TV UI checks for rapid consecutive inserts, preservation of an intervening user copy and restoration of an initially empty clipboard.
- Full Android TV UI checks passed: `build/tv-ui-test-5cba03cdecc0460c9dc2f831d7f03663`.

## 25 October 2026 — version 2.0.6 rebuild

- Rebuilt the TV APK as version `2.0.6`, version code `26`, with package `com.tvtext.app`.
- Release artifact: `outputs/TVText-2.0.6/TVText-TV-2.0.6.apk`.
- APK SHA-256: `FFA12122D9F107390D29A4DCDABBD47EF4B0228D2B322B852D3637D7D18524E6`.

## 25 October 2026 — ready state card and connection count

- The post-accessibility ready state now uses the same two-line hierarchy as the TV-name card: `Состояние` / `Status` as the caption and `Готово к подключению устройств` / `Ready to connect devices` as the value.
- Renamed the connection action to `Список устройств` / `Device list`. Its thin outlined count badge updates when the number of saved connections changes and is hidden while the action opens accessibility settings.
- The preparing state after accessibility is enabled now keeps the same two-line card hierarchy with a yellow icon and border: `Состояние` above `Выполняется подготовка ТВ к работе`.
- The accessibility setup state now uses the same yellow icon and outline instead of red.
- The ready state uses `Состояние` above `Готово к подключению устройств` consistently with both zero and one-or-more saved devices.
- Rebuilt the TV APK with the full application ID `com.tvtext.app` and verified the signed artifact metadata.
- APK: `outputs/TVText-2.0.5/TVText-2.0.5.apk`; SHA-256: `BA437C719355878C31ADFE14F08DEC8B0B0E52248CBAFD1DB11A9C6F2D9E95D8`.
- The project’s local protocol, TLS and Windows UI suite also passed: `build/test-b6d3a054c2ed47cfaf364a31cfc297fd`.

## 25 October 2026 — Android package name

- Changed the TV APK application ID from `local.tvtext` to `com.tvtext.app`.
- Renamed the Kotlin implementation package and source path to `com.tvtext.app`, including the activity, accessibility service, protocol and input bridge classes.
- Updated the accessibility-service component, R8 rules, UI test imports and screenshot output path.

## 25 October 2026 — connection wording and test dialog

- Changed the ready state to `Готов к подключению устройств` / `Ready to connect devices` and removed its secondary hint.
- Updated the pairing instruction to: `Выберите этот ТВ в TVText на другом устройстве и нажмите на кнопку «Подключить»`.
- Removed the `Очистить` / `Clear` action from the keyboard test dialog; it now contains only the input and Close action.
- The keyboard test action is also hidden while the new-device pairing code is open.
- The deletion dialog now opens with `Удалить подключение` / `Delete connection` focused for direct remote confirmation.

## 25 October 2026 — accessibility-off wording

- Changed the Russian title to `Специальные возможности отключены`.
- Updated the first-step explanation to name the TVText accessibility service directly.
- Hid `Тест клавиатуры` / `Keyboard test` until the accessibility service is enabled; it appears after TVText can receive input.

## 25 October 2026 — information cards and keyboard test

- Unified accessibility-disabled, service-starting, connection preparation, pairing and ready instructions into one rounded information card with an icon, optional heading and naturally wrapping text in RU/EN.
- The same pairing instructions are used for the first and every additional device. The code, expiry and fingerprint now share a compact card.
- Added `Тест клавиатуры` / `Keyboard test` at the left of the footer. Its dialog opens the system keyboard, accepts text from connected devices, offers Close and returns focus to the footer on closing. Test text is not stored.
- Allowed input into the dedicated test-field ID through both the API 33+ and Android 9–12 input bridges; other TVText fields remain excluded. Accessibility reports view IDs so the legacy bridge can identify this one field.
- Replaced the deletion confirmation with a padded dialog, the selected device and short ID, equal-sized Cancel/Delete actions and visible D-pad focus. Cancel is focused initially; canceling or confirming returns to the updated connected-device list.
- Extended UI checks to cover remote test-field insertion through both bridges, clearing, closing, lack of retained text, matching pairing hints, cancellation and deletion of exactly one connection.
- Final Android 14 emulator checks passed: `build/tv-ui-test-4d29d7b0eb58446db491676fbedf468f`. A minimal IME exists only in the test APK because the ATD image has no keyboard; production keeps the user's selected keyboard.
- Rebuilt TV APK `2.0.5` / `25` without compiler warnings. Artifact: `outputs/TVText-2.0.5/TVText-2.0.5.apk`; SHA-256: `B41E5E319BF112124478C6E873623C925B32739EF1DD932A3432DE26E7AE1AAF`.

## 24 October 2026 — compact connection actions

- Renamed the pairing action to `Подключить устройство` / `Connect device`.
- Widened the main TV card and kept both connection actions on one line so the button labels no longer wrap.
- Simplified the TV-name caption to `Имя телевизора в приложении` / `TV name in the app`.
- Removed the redundant ready-state title; the working screen now shows only the concise keyboard-and-sending instruction.
- Styled the working instruction as a separate rounded card with a subtle border and balanced inner spacing.
- Added a tinted information icon beside the instruction text.
- Left-aligned the instruction text and updated its RU/EN wording without a trailing period.
- Restored the compact TV card width; the instruction now wraps naturally into two lines at its regular font size.

## 24 October 2026 — IPv4-only receiver

- Removed the IPv6 checkbox from the TV screen.
- The TV receiver now always binds, advertises and accepts IPv4 only. The generic TLS IPv6 mode remains available only for protocol tests.
- The footer keeps only the language and day/night controls; D-pad navigation and focus tests were updated accordingly.

## 24 October 2026 — neutral device wording

- Replaced user-facing PC/computer wording with device wording in the TV accessibility label, pairing flow, connected-device list, deletion dialog, fingerprint hint and protocol messages.
- Kept the transport and pairing behavior unchanged; only localized presentation text was updated.

## 24 October 2026 — version 2.0.4 rebuild

- Rebuilt the TV receiver APK as version `2.0.4`, version code `24`; the build includes neutral device wording and the IPv4-only receiver.
- Release artifact: `outputs/TVText-2.0.4/TVText-2.0.4.apk` (74,706 bytes).
- APK SHA-256: `8325FE9D434437663BBA4B51BA61C907C5E15CF50D458F5A6AE2C891325ED255`.
- The v3 signing certificate remains the same as the 2.0.3 build, so the APK supports an in-place update.

### IPv4-only verification

- TV UI instrumentation passed on the isolated Android 14 emulator: `build/tv-ui-test-f4ea6832e217410890d3a72f4f53693e`.
- The tested footer contains only the RU/EN and day/night controls; no IPv6 option is exposed.
- The APK was copied locally to the release output path; no APK or source changes were pushed to GitHub.

## 24 October 2026 — connected PCs window

- Replaced the native title/list/button layout with a single padded dialog card, computer icon, connection-count badge and an aligned Close button.
- Each connection has a computer badge, name, short identifier and a red icon-only delete action. D-pad focus outlines the entire connection row as well as the delete action.
- Long lists scroll within the dialog, with space for up to three connections and a height limit for smaller TV screens. Row height accounts for text scaling.
- Empty lists show a centered icon and short message. Both RU/EN labels and both themes use the same layout.
- D-pad Up/Down explicitly follows delete actions and reaches Close after the final connection; Up from Close returns to the last connection.
- Extended the emulator checks for empty/single/long lists, scrolling to the last connection, Close navigation and cancellation of deletion.
- Final UI checks passed in both themes with RU/EN recreation; Russian screenshots were captured in `build/tv-ui-test-d3b9c3ad70d24723b26dbe60a44bba5c`. The build completes without warnings, and APK signature/alignment verification passed.

## 24 October 2026 — default dark theme and day/night switch

- TV receiver version: `2.0.3`, version code `23`.
- Dark mode is the default, including the initial window background. A compact sun/moon button beside RU/EN toggles day/night mode and saves the preference locally.
- The main card, device-name panel, status indicators, pairing code, buttons and connection/revoke dialogs use the selected palette. Focused, pressed and disabled states remain distinct for D-pad navigation.
- Primary button fill is separate from the accent text color to keep button labels readable in both themes.
- Recreating the screen for a theme or language change preserves the receiver runtime, current pairing code, saved connections and TLS identity. Focus returns to the settings button used to make the change.
- Initial focus is restored after the window gains focus so the scroll container cannot take it from the accessibility/settings action.
- Added `test-tv-ui.ps1` and isolated emulator checks under `tests/android-tv/ui/`. The Java test package uses the APK's Kotlin runtime to avoid conflicts with R8-optimized Kotlin classes.

### Verification

- The release APK passed UI instrumentation on the isolated Android 14 emulator at 1920×1080 / 320 dpi: `build/tv-ui-test-b1190a87a1834925b62e0572e1ac86e6`.
- Verified dark default, saved day/night preference, focus restoration, RU/EN recreation, D-pad navigation to the theme switch, accessibility-disabled/ready/pairing screens, unchanged pairing PIN and TLS identity, connection/revoke dialogs, and text contrast. Screenshots of both themes were visually checked.
- These checks use an Android emulator configured with TV screen dimensions; physical Google TV firmware remains a separate manual check.
- Earlier 2.0.3 metadata is retained below for historical reference; current TV package metadata is documented in the 2.0.5 entries above.
- Final APK: `outputs/TVText-2.0.3/TVText-2.0.3.apk`, 74,633 bytes.
- SHA-256: `E739C0BF58C4EC8EDE741270A98AAA108FF0E7BED68AB59D71E5402FEA792E02`.
