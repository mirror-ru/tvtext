# Compatibility verification

[Русская версия](COMPATIBILITY.ru.md)

Both APKs require Android 9 / API 28 or newer. This is a build requirement, not evidence of testing every firmware. Isolated Android 9 / API 28, Android 12 / API 31 and Android 14 / API 34 emulators are installed. Other API levels and physical TV firmware remain **not run**. The native API 31 run exposed a background clipboard-restoration limitation; it is not an unconditional TV-suite pass.

| Receiver Android | Input method | Current evidence |
| --- | --- | --- |
| 9 / API 28 | Accessibility Paste; no whole-field replacement fallback | Mobile and TV suites passed, including external insertion and background clipboard restoration |
| 10–11 / API 29–30 | Accessibility Paste | Native runtime not run |
| 12 / API 31 | Accessibility Paste | Mobile suite passed; TV insertion/selection/IME checks passed, strict background clipboard-restoration check failed |
| 12L / API 32 | Accessibility Paste | Native runtime not run |
| 13 / API 33 | Accessibility input connection | Native API 33 runtime not run |
| 14 / API 34 | Accessibility input connection | Isolated emulator instrumentation; see latest test report |
| 15–16 / API 35–36 | Accessibility input connection | Runtime not run |
| Physical Google TV / vendor firmware | Depends on Android and target editor | Not run in this environment |

`test-compatibility.ps1` runs mobile and TV suites sequentially on explicitly named isolated emulators and writes a JSON matrix of passed, failed and untested versions. It uses phone and TV screen dimensions, restoring the previous size/density overrides even on failure. It does not download images, operate physical devices, or turn absent tests into passes. The mobile result reports the actual API level. Device tests install test apps and alter test preferences; use disposable AVDs only.

```powershell
.\test-compatibility.ps1 -Device emulator-5580 `
  -Artifacts '.\dist' -Sdk 'C:\Users\YOUR_USER\AppData\Local\Android\Sdk'
```

The UI suites check pairing, local state, theme/language, focus, and sending. TV checks also exercise the legacy bridge at a cursor in a nonempty field, replacement of a selected range, an editor exposing SetText but no Paste, rejected Paste, and clipboard restoration after rapid sends/user copying. The separate field APK additionally checks the real API-dependent insertion path while TVText is in the background, Unicode/multiline input, selection, empty/nonempty clipboard and preservation of the selected IME.

On native Android 9, both suites passed, including background clipboard restoration. On native Android 12, external-field insertion succeeds, but the system denies background clipboard reads to TVText. Its previous contents cannot reliably be captured/restored and sent text can remain in the clipboard. The strict regression stays failing; foreground restoration checks do not waive it. Android 10 introduced the relevant [clipboard read restrictions](https://developer.android.com/about/versions/10/privacy/changes#clipboard-data); Android 10–11 are not validated by the API 31 result. Android 13+ uses a different insertion API that does not require the clipboard. See the [latest test report](TESTS.md) for actual run results.

For each physical TV, record model, firmware/build, Android/API, keyboard, target app/version, sender platform and the exact APK/EXE hashes. Check:

1. Empty field, nonempty field with cursor in the middle, selected text, Unicode/multiline input and a field that rejects insertion; preserve the draft on failure.
2. Gboard and at least one nonstandard editor, such as a WebView or an app's search field. Never assume every editor supports insertion.
3. Pairing/fingerprint confirmation, several clients, switching TVs, revocation from both sides and a failed remote-unpair notification.
4. Sleep/wake, app/service restart, Wi-Fi disconnect/reconnect, DHCP address change, discovery blocked and manual Refresh. No automatic replay of text after a failed send.
5. D-pad navigation and visible focus, RU/EN labels, themes, phone/tablet rotation and large system font.

Record expected/actual behavior, date and pass/fail for each scenario. Until these checks have evidence, describe physical-TV compatibility as unverified.
