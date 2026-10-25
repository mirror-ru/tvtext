# Android input fixture

FieldActivity.java and AndroidManifest.xml build a separate minimal API 34 activity with an EditText. It is used only in an isolated Android TV emulator to check real AccessibilityInputConnection insertion and preservation of the selected IME.

Compile with javac against android.jar, convert classes with D8, package with aapt2, zipalign and sign using a temporary test key. Do not install this fixture on the user's TV. It needs no permissions and must not be shipped as the receiver APK.

Start local.tvtext.testfield/.FieldActivity after enabling the TVText accessibility service and pairing the Windows client. Send Unicode through the normal TVText TLS protocol, inspect the visible EditText, and compare the complete text with the submitted value. Compare Settings.Secure.DEFAULT_INPUT_METHOD before/after; no IME switch is needed.

Android shell uiautomator dump can temporarily suspend accessibility services. Wait for TVText to rebind before testing another request. A force-stopped accessibility service may need to be enabled again in the emulator settings. Use only a dedicated emulator and keep ADB/forwarded ports isolated from real devices.
