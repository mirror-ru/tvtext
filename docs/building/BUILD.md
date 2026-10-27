# Building TVText

[Русская версия](BUILD.ru.md)

Run the root PowerShell entry points from Windows. They do not download dependencies.

## Version and tools

`version.json` is the source of the application version and Android version code. Change both fields for the next release. The build passes them to Android packaging and generates Windows assembly attributes and a manifest below `build/`; it does not edit tracked source files. `windows/app.manifest` is a template and must be processed by the build/test entry points.

`toolchain.json` pins JDK 21.0.10, Kotlin `2.2.20-release-333`, Android platform `android-36.1` revision 1, and build-tools 37.0.0. Scripts fail early when the selected tools differ. Install those versions and use `-Jdk`, `-Kotlin`, or `-Sdk` to select their locations. Updating the toolchain is an explicit change to `toolchain.json`, followed by builds and tests. These pins prevent accidental tool upgrades; they do not guarantee byte-for-byte reproducible signed artifacts.

The default JDK and Kotlin paths point to Android Studio. The Android SDK defaults to `%LOCALAPPDATA%\Android\Sdk`. Only the paths are configurable; tool versions are checked against the pins. The Windows compiler comes from the installed .NET Framework and is serviced with Windows.

## Build targets

```powershell
.\build.ps1 -Target Windows
.\build.ps1 -Target TV -Sdk 'C:\Users\YOUR_USER\AppData\Local\Android\Sdk'
.\build.ps1 -Target Mobile -Sdk 'C:\Users\YOUR_USER\AppData\Local\Android\Sdk'
.\build.ps1 -Target All -Sdk 'C:\Users\YOUR_USER\AppData\Local\Android\Sdk'
```

`Windows` requires only the Windows C# compiler and does not inspect Android tools or create APK signing material. Android targets require the pinned Android/JVM tools and create a local signing key on the first build. Restore both `.local/signing.p12` and `.local/signing-password.txt` together to retain an existing signing identity. An incomplete pair is rejected rather than replaced.

Output defaults to `dist/`; override it with `-Output`. Each build uses its own `build/run-*/` directory. Release uploads contain the two APKs and the EXE with its `.exe.config` sidecar.

Mobile sources and their intermediate JAR are under `android-mobile/` and `build/run-*/android-mobile/app.jar`. Mobile test sources are in `tests/android-mobile/`. The Android application ID is still `com.tvtext.mobile.app`.

## Local release preparation

The source fingerprint is captured before building and checked after the build and after tests. Both modes, including `Core`, validate matching APK/JAR/source build records; retain intermediate JAR files until release preparation finishes. Source changes require a fresh build.

Run `prepare-release.ps1 -Device emulator-5580 -Sdk '<SDK path>'` to build all three apps and validate their versions, SDK bounds, alignment and APK certificates, then run Windows/TLS and both Android UI suites. Full checks are the default and require an explicitly selected isolated emulator. `-Tests Core` explicitly omits Android UI checks and records this in the report; it still runs Windows/TLS and mobile JVM tests.

The script creates a new candidate directory under `outputs/` only after the requested checks pass. Use `-Output` for another new path; existing directories are rejected. The directory contains the two APKs, EXE, EXE configuration, `SHA256SUMS.txt` and `release-report.json`. Local APK/JAR build records stay under `build/`. No keys, passwords, test APKs or machine-specific JAR paths enter the candidate folder. Nothing is uploaded or committed.

`release-signing.json` stores the public APK certificate digest expected for updates. It is safe to commit; changing it is an intentional signing-identity change. Windows Authenticode status is recorded, not misreported as signed. The current EXE is unsigned. The report distinguishes emulator checks from physical-TV checks, which remain manual: [compatibility checklist](../testing/COMPATIBILITY.md).

## Matching APKs and device tests

Each APK receives an adjacent `<filename>.apk.build.json` record. It contains the APK SHA-256, the exact `app.jar` path and hash, a fingerprint of application/build sources, the version, and toolchain pins. It contains no signing keys, passwords, or pairing credentials. This is a local consistency check, not a cryptographic attestation. Keep it locally for device tests; it is not a release asset.

Fingerprint paths now use the same order under Windows PowerShell 5.1 and PowerShell 7. After the fingerprint algorithm and sources change, old build records no longer match the current project: rebuild the APK and its record with the existing signing key.

```powershell
.\test-mobile.ps1 -Device emulator-5580 `
  -Sdk 'C:\Users\YOUR_USER\AppData\Local\Android\Sdk' `
  -AppApk '.\dist\TVText-Mobile-2.0.10.apk'
.\test-tv-ui.ps1 -Device emulator-5580 `
  -Sdk 'C:\Users\YOUR_USER\AppData\Local\Android\Sdk' `
  -AppApk '.\dist\TVText-TV-2.0.10.apk'
```

The scripts find `app.jar` through this record. `-AppJar` can point to a moved copy, but its hash must match. They reject missing records, changed APK/JAR contents, a different target/version/toolchain, and application/build source changes before installing anything. Rebuild old APKs that have no record. Rebuild after source changes. Retain `build/run-*/` until the associated tests finish.

Only explicitly named isolated emulators are accepted. JVM-only mobile tests do not need an APK, SDK or signing key. `test.ps1` includes `tests/tooling/BuildTests.ps1`, which checks version validation, Windows dependency isolation and mismatched APK/JAR rejection. To run those build checks separately:

```powershell
.\tests\tooling\BuildTests.ps1
```
