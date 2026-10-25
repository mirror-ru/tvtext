# Compatibility verification

[Русская версия](COMPATIBILITY.ru.md)

Both APKs require Android 9 / API 28 or newer. This is a build requirement, not evidence of testing every firmware. The available isolated emulator uses Android 14 / API 34. Android 9–13 and 15–16 images and physical TV devices are unavailable in the current environment; their runtime results remain **not run**.

| Receiver Android | Input method | Current evidence |
| --- | --- | --- |
| 9–12L / API 28–32 | Accessibility Paste; no whole-field replacement fallback | Legacy bridge exercised on API 34; native API 28–32 runtime not run |
| 13 / API 33 | Accessibility input connection | Native API 33 runtime not run |
| 14 / API 34 | Accessibility input connection | Isolated emulator instrumentation; see latest test report |
| 15–16 / API 35–36 | Accessibility input connection | Runtime not run |
| Physical Google TV / vendor firmware | Depends on Android and target editor | Not run in this environment |

`test-compatibility.ps1` runs mobile and TV suites sequentially on explicitly named isolated emulators and writes a JSON matrix of passed, failed and untested versions. It uses phone and TV screen dimensions, restoring the previous size/density overrides even on failure. It does not download images, operate physical devices, or turn absent tests into passes. The mobile result reports the actual API level. Device tests install test apps and alter test preferences; use disposable AVDs only.

```powershell
.\test-compatibility.ps1 -Device emulator-5580 `
  -Artifacts '.\dist' -Sdk 'C:\Users\YOUR_USER\AppData\Local\Android\Sdk'
```

The UI suites check pairing, local state, theme/language, focus, and sending. TV checks also exercise the legacy bridge at a cursor in a nonempty field, replacement of a selected range, an editor exposing SetText but no Paste, rejected Paste, and clipboard restoration after rapid sends/user copying. Running legacy checks on API 34 does not establish clipboard policy or service behavior on API 28–32.

For each physical TV, record model, firmware/build, Android/API, keyboard, target app/version, sender platform and the exact APK/EXE hashes. Check:

1. Empty field, nonempty field with cursor in the middle, selected text, Unicode/multiline input and a field that rejects insertion; preserve the draft on failure.
2. Gboard and at least one nonstandard editor, such as a WebView or an app's search field. Never assume every editor supports insertion.
3. Pairing/fingerprint confirmation, several clients, switching TVs, revocation from both sides and a failed remote-unpair notification.
4. Sleep/wake, app/service restart, Wi-Fi disconnect/reconnect, DHCP address change, discovery blocked and manual Refresh. No automatic replay of text after a failed send.
5. D-pad navigation and visible focus, RU/EN labels, themes, phone/tablet rotation and large system font.

Record expected/actual behavior, date and pass/fail for each scenario. Until these checks have evidence, describe physical-TV compatibility as unverified.
