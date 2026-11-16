# Test suite map

[Русская версия](README.ru.md)

The tree groups tests by product first, then by what they exercise. Existing entry points stay at the repository root so local and release workflows do not need a new command.

```text
tests/
  android-tv/
    core/               receiver protocol, state, and reliability
    instrumentation/ui/ real Android UI, IME, and external-field checks
    fixtures/           controlled field app used by insertion tests
  android-mobile/
    core/               catalog, sender workflow, and JVM protocol checks
    instrumentation/    on-device instrumentation runner and manifest
    fixtures/           local TLS receiver used by JVM/device tests
  windows/
    core/               profile catalog and persistence checks
    integration/        TLS, discovery, transport, and sender workflows
    ui/                 DPI, main list, pairing, and theme checks
    runner/              shared C# test entry point
  shared/protocol/       wire-format golden vectors consumed by all clients
  tooling/               build, cache, and release-integrity checks
```

## Entry points

- `test.ps1` runs tooling, Windows core/integration/UI, and Android TV core tests, including the local TLS fixture.
- `test-mobile.ps1` runs mobile JVM/core and TLS tests. Pass `-Device emulator-N` to add Android instrumentation.
- `test-tv-ui.ps1` builds and runs Android TV instrumentation on an explicitly selected emulator.
- `test-compatibility.ps1` runs the mobile and TV device suites for each explicitly selected API emulator and writes a matrix report.

All test artifacts, test APKs, keys generated for fixtures, and logs stay under ignored `build/`. The local TLS fixtures bind to loopback; the suites do not use saved TV credentials. Android instrumentation is kept separate from JVM/core tests so it can be skipped intentionally without dropping core coverage.
