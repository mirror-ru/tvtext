# Android input fixture

`FieldActivity.java` and `AndroidManifest.xml` define a separate test application with an EditText, requiring API 28 or newer. `test-tv-ui.ps1` builds and installs it alongside the TV instrumentation APK in the explicitly selected isolated emulator. It checks insertion while TVText is in the background, using the receiver's actual API-dependent input path, and preservation of the selected IME.

The script compiles it with javac, converts classes with D8, packages with aapt2, aligns and signs it with the local build key. It has a separate package ID, needs no permissions and stays under `build/`. Do not install it on a user's TV or ship it as a release asset.

`ExternalFieldChecks` opens `local.tvtext.testfield/.FieldActivity` with known initial text, cursor/selection and clipboard contents. It sends authenticated insert requests through the production receiver engine, checks Unicode/multiline text and selection replacement, observes the clipboard from the foreground fixture, and compares `Settings.Secure.DEFAULT_INPUT_METHOD` before/after. The fixture exposes its test clipboard in a labeled TextView; production TVText does not read the target field. Local TLS/protocol transport is checked separately by the core suites.

Android shell uiautomator dump can temporarily suspend accessibility services. Wait for TVText to rebind before testing another request. A force-stopped accessibility service may need to be enabled again in the emulator settings. Use only a dedicated emulator and keep ADB/forwarded ports isolated from real devices.
