# Build-system regression checks; fixtures are generated below ignored build/.
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
. (Join-Path $project 'scripts\Common.ps1')
. (Join-Path $project 'scripts\Release.ps1')

function Assert-True([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}
function Assert-Rejected([scriptblock]$Action, [string]$Pattern) {
    try { & $Action | Out-Null }
    catch {
        if ($_.Exception.Message -notmatch $Pattern) { throw }
        return
    }
    throw "Expected rejection: $Pattern"
}
function Write-Fixture([string]$Path, [string]$Value) {
    [IO.File]::WriteAllText($Path, $Value, [Text.UTF8Encoding]::new($false))
}

$run = Join-Path $project ('build\build-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $run | Out-Null
$mobileSource = Join-Path $project 'android-mobile\src\com\tvtext\mobile\app'
foreach ($file in @('MainActivity.kt', 'SenderClient.kt', 'ConnectionCatalog.kt', 'ProfileStore.kt')) {
    Assert-True (Test-Path -LiteralPath (Join-Path $mobileSource $file)) "Mobile source missing from android-mobile: $file"
}
Assert-True (!(Test-Path -LiteralPath (Join-Path $project 'android'))) 'Old mobile application directory still exists.'
$fixtureVersion = Join-Path $run 'version.json'
Write-Fixture $fixtureVersion '{"name":"2.3.4","androidCode":28}'
$version = Read-ProjectVersion $fixtureVersion
Assert-True ($version.Name -eq '2.3.4' -and $version.Assembly -eq '2.3.4.0') 'Version derivation failed.'
foreach ($badVersion in @('{"name":"2.3","androidCode":28}', '{"name":"2.3.4","androidCode":0}',
    '{"name":"2.3.4","androidCode":27.5}', '{"name":"65535.0.0","androidCode":28}')) {
    Write-Fixture $fixtureVersion $badVersion
    Assert-Rejected { Read-ProjectVersion $fixtureVersion } 'version.json'
}
Assert-Rejected { Assert-ToolVersion 'Kotlin' 'different' 'pinned' } 'version mismatch'

# Build records must remain valid when build and test use different PowerShell hosts.
$alternateHost = if ($PSVersionTable.PSVersion.Major -ge 6) {
    Join-Path ([Environment]::SystemDirectory) 'WindowsPowerShell\v1.0\powershell.exe'
} else {
    $pwsh = Get-Command 'pwsh.exe' -ErrorAction SilentlyContinue
    if ($pwsh) { $pwsh.Source }
}
if ($alternateHost -and (Test-Path -LiteralPath $alternateHost)) {
    $fingerprintProbe = Join-Path $run 'fingerprint-probe.ps1'
    $commonPath = (Join-Path $project 'scripts\Common.ps1').Replace("'", "''")
    Write-Fixture $fingerprintProbe (". '$commonPath'`nGet-SourceFingerprint`n")
    $alternateHash = (& $alternateHost -NoProfile -ExecutionPolicy Bypass -File $fingerprintProbe | Out-String).Trim()
    Assert-True ($LASTEXITCODE -eq 0 -and $alternateHash -ceq (Get-SourceFingerprint)) 'Source fingerprint differs between PowerShell hosts.'
} else { Write-Output 'SKIP: alternate PowerShell host is not installed.' }

# Prove Windows-only builds do not touch Android tools or create a signing key.
$copy = Join-Path $run 'project'
New-Item -ItemType Directory -Path $copy | Out-Null
foreach ($file in @('version.json', 'toolchain.json', 'build.ps1')) {
    Copy-Item -LiteralPath (Join-Path $project $file) -Destination $copy
}
foreach ($directory in @('scripts', 'windows')) {
    Copy-Item -LiteralPath (Join-Path $project $directory) -Destination $copy -Recurse
}
$output = Join-Path $copy 'dist'
Push-Location $copy
try {
    & (Join-Path $copy 'build.ps1') -Target Windows -Sdk 'Z:\missing-sdk' -Jdk 'Z:\missing-jdk' -Kotlin 'Z:\missing-kotlin' -Output 'dist'
} finally { Pop-Location }
$version = Read-ProjectVersion
$exe = Join-Path $output "TVText-$($version.Name).exe"
Assert-True (Test-Path -LiteralPath $exe) 'Windows output was not created.'
Assert-True (!(Test-Path -LiteralPath (Join-Path $copy '.local'))) 'Windows-only build created signing material.'
Assert-True ((Get-Item -LiteralPath $exe).VersionInfo.FileVersion -eq $version.Assembly) 'EXE version differs from version.json.'

# Build pairing is verified by content, not by file name or modification time.
$apk = Join-Path $run 'fixture.apk'
$jar = Join-Path $run 'app.jar'
$wrongJar = Join-Path $run 'wrong.jar'
Write-Fixture $apk 'APK fixture'
Write-Fixture $jar 'matching classes'
Write-Fixture $wrongJar 'different classes'
$sourceHash = Get-SourceFingerprint
Write-ApkBuildRecord $apk $jar 'android-mobile' $version $sourceHash
$resolved = Resolve-TestBuild $apk '' 'android-mobile'
Assert-True ($resolved.AppJar -eq $jar) 'Recorded app.jar was not resolved.'
Assert-Rejected { Resolve-TestBuild $apk '' 'android-tv' } 'target or version'
Assert-Rejected { Resolve-TestBuild $apk $wrongJar 'android-mobile' } 'app.jar hash differs'
$missingApk = Join-Path $run 'missing-record.apk'
Write-Fixture $missingApk 'APK without metadata'
Assert-Rejected { Resolve-TestBuild $missingApk $jar 'android-mobile' } 'build record'
Write-Fixture $apk 'modified APK'
Assert-Rejected { Resolve-TestBuild $apk $jar 'android-mobile' } 'APK hash differs'
Write-ApkBuildRecord $apk $jar 'android-mobile' $version ('0' * 64)
Assert-Rejected { Resolve-TestBuild $apk $jar 'android-mobile' } 'sources changed'
Write-ApkBuildRecord $apk $jar 'android-mobile' $version $sourceHash
Write-Fixture $jar 'modified classes'
Assert-Rejected { Resolve-TestBuild $apk $jar 'android-mobile' } 'app.jar hash differs'

$release = Join-Path $run 'release'
New-Item -ItemType Directory -Path $release | Out-Null
$assets = @(Get-ReleaseAssetNames $version)
foreach ($name in $assets) { Write-Fixture (Join-Path $release $name) "fixture $name" }
Write-Fixture (Join-Path $release 'release-report.json') '{}'
Write-ReleaseChecksums $release $assets
Assert-CleanRelease $release $assets
Write-Fixture (Join-Path $release 'private-key.p12') 'must not be shipped'
Assert-Rejected { Assert-CleanRelease $release $assets } 'unexpected files'
Remove-Item -LiteralPath (Join-Path $release 'private-key.p12')
Write-Fixture (Join-Path $release $assets[0]) 'tampered artifact'
Assert-Rejected { Assert-CleanRelease $release $assets } 'checksum mismatch'

# A PASS line must never mask a failing process (including a failure after PASS).
foreach ($code in @(0, 7)) {
    $child = Join-Path $run "exit-$code.ps1"
    Write-Fixture $child "Write-Output 'PASS: fixture assertions'; exit $code"
    $process = Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList @('-NoProfile','-File',('"'+$child+'"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run "exit-$code.log")
    $null = $process.Handle
    try {
        Assert-True ($process.WaitForExit(10000)) 'Exit-code fixture timed out.'
        if ($code -eq 0) { Assert-ProcessSuccess $process 'Exit-code fixture' }
        else { Assert-Rejected { Assert-ProcessSuccess $process 'Exit-code fixture' } 'exit code 7' }
    } finally { if (!$process.HasExited) { $process.Kill() }; $process.Dispose() }
}
Write-Output "BUILD TESTS PASSED. Windows isolation, version validation, pinned tools and APK/JAR mismatch rejection: $run"
