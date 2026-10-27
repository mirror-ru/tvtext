param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$Kotlin = 'C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc',
    [ValidateSet('Core','Full')][string]$Tests = 'Full',
    [string[]]$Device = @(),
    [string]$Output = ''
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\Common.ps1')
. (Join-Path $PSScriptRoot 'scripts\Release.ps1')
$version = Read-ProjectVersion
if ($Tests -eq 'Full' -and $Device.Count -eq 0) { throw 'Full verification requires -Device emulator-NNNN. Use -Tests Core to explicitly omit Android UI checks.' }
if ($Tests -eq 'Core' -and $Device.Count -gt 0) { throw '-Device requires -Tests Full.' }
foreach ($id in $Device) { if ($id -notmatch '^emulator-\d+$') { throw 'Only explicitly selected isolated emulators are accepted.' } }
$jvm = Resolve-JvmTools $Jdk $Kotlin
$android = Resolve-AndroidTools $Sdk
$certificate = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'release-signing.json') -Raw | ConvertFrom-Json).apkCertificateSha256
if ($certificate -cnotmatch '^[a-f0-9]{64}$') { throw 'Invalid public certificate digest in release-signing.json.' }
if (!$Output) { $Output = Join-Path $PSScriptRoot ("outputs\TVText-$($version.Name)-candidate-" + [DateTime]::Now.ToString('yyyyMMdd-HHmmss')) }
$Output = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Output)
if (Test-Path -LiteralPath $Output) { throw 'Output already exists. Select a new directory; existing releases are never overwritten.' }
$run = Join-Path $PSScriptRoot ('build\release-' + [guid]::NewGuid().ToString('N'))
$artifacts = Join-Path $run 'artifacts'
New-Item -ItemType Directory -Path $run | Out-Null
$tools = @{ Sdk = $Sdk; Jdk = $Jdk; Kotlin = $Kotlin }
$sourceHash = Get-SourceFingerprint
& (Join-Path $PSScriptRoot 'build.ps1') @tools -Target All -Output $artifacts
Assert-ReleaseSources $sourceHash
$names = @(Get-ReleaseAssetNames $version)
$null = Resolve-TestBuild (Join-Path $artifacts $names[0]) '' 'android-tv'
$null = Resolve-TestBuild (Join-Path $artifacts $names[1]) '' 'android-mobile'
$tv = Assert-ReleaseApk (Join-Path $artifacts $names[0]) 'com.tvtext.app' $version $jvm $android $certificate
$mobile = Assert-ReleaseApk (Join-Path $artifacts $names[1]) 'com.tvtext.mobile.app' $version $jvm $android $certificate
$exe = Get-Item -LiteralPath (Join-Path $artifacts $names[2])
if ($exe.VersionInfo.FileVersion -cne $version.Assembly -or [Reflection.AssemblyName]::GetAssemblyName($exe.FullName).Version.ToString() -cne $version.Assembly) { throw 'Windows version mismatch.' }
[xml]$config = Get-Content -LiteralPath (Join-Path $artifacts $names[3]) -Raw
if (!$config.configuration.startup.supportedRuntime) { throw 'Missing Windows runtime configuration.' }
& (Join-Path $PSScriptRoot 'test.ps1') @tools
if ($Tests -eq 'Full') {
    & (Join-Path $PSScriptRoot 'test-compatibility.ps1') @tools -Device $Device -Artifacts $artifacts -Output (Join-Path $run 'compatibility.json')
} else {
    & (Join-Path $PSScriptRoot 'test-mobile.ps1') @tools
}
Assert-ReleaseSources $sourceHash
$null = Resolve-TestBuild (Join-Path $artifacts $names[0]) '' 'android-tv'
$null = Resolve-TestBuild (Join-Path $artifacts $names[1]) '' 'android-mobile'
# Publish the local candidate folder only after all requested checks pass.
$clean = Join-Path $run 'release'
New-Item -ItemType Directory -Path $clean | Out-Null
foreach ($name in $names) { Copy-Item -LiteralPath (Join-Path $artifacts $name) -Destination $clean }
$report = [ordered]@{
    schema = 1; version = $version.Name; androidCode = $version.AndroidCode
    createdUtc = [DateTime]::UtcNow.ToString('o'); sourceHash = $sourceHash; toolchain = Read-Toolchain
    apk = @($tv, $mobile); windowsAuthenticode = (Get-AuthenticodeSignature -LiteralPath $exe.FullName).Status.ToString()
    tests = [ordered]@{ core = 'passed'; androidUi = $(if ($Tests -eq 'Full') { 'passed' } else { 'not run' }); physicalTv = 'not run' }
}
if ($Tests -eq 'Full') { $report.compatibility = Get-Content -LiteralPath (Join-Path $run 'compatibility.json') -Raw | ConvertFrom-Json }
[IO.File]::WriteAllText((Join-Path $clean 'release-report.json'), ($report | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
Write-ReleaseChecksums $clean $names
Assert-CleanRelease $clean $names
$parent = Split-Path -Parent $Output
New-Item -ItemType Directory -Force -Path $parent | Out-Null
# Copy into a new destination; never move/delete an existing user directory.
New-Item -ItemType Directory -Path $Output | Out-Null
foreach ($file in Get-ChildItem -LiteralPath $clean -File) { Copy-Item -LiteralPath $file.FullName -Destination $Output }
Assert-CleanRelease $Output $names
Write-Output "LOCAL RELEASE CANDIDATE READY: $Output"
