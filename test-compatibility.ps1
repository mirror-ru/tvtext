param(
    [Parameter(Mandatory=$true)][string[]]$Device,
    [Parameter(Mandatory=$true)][string]$Artifacts,
    [string]$Output = '',
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$Kotlin = 'C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\Common.ps1')
$version = Read-ProjectVersion
$adb = Require-File (Join-Path $Sdk 'platform-tools\adb.exe') 'Android adb.exe'
$tv = Join-Path $Artifacts "TVText-TV-$($version.Name).apk"
$mobile = Join-Path $Artifacts "TVText-Mobile-$($version.Name).apk"
$null = Resolve-TestBuild $tv '' 'android-tv'
$null = Resolve-TestBuild $mobile '' 'android-mobile'
foreach ($id in $Device) { if ($id -notmatch '^emulator-\d+$') { throw 'Compatibility tests accept only isolated emulators.' } }
if (!$Output) { $Output = Join-Path $PSScriptRoot ('build\compatibility-' + [guid]::NewGuid().ToString('N') + '.json') }
$Output = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Output)
if (Test-Path -LiteralPath $Output) { throw 'Compatibility report already exists.' }
$results = @(); $failure = $null
try {
    foreach ($id in ($Device | Select-Object -Unique)) {
        $entry = $null
        $api = (& $adb -s $id shell getprop ro.build.version.sdk).Trim()
        if ($LASTEXITCODE -ne 0 -or $api -notmatch '^\d+$' -or [int]$api -lt 28) { throw "Unsupported or unavailable emulator: $id" }
        $entry = [ordered]@{ api = [int]$api; environment = 'emulator'; mobile = 'not run'; tv = 'not run' }
        $results += $entry
        $size = (& $adb -s $id shell wm size) -join "`n"
        $density = (& $adb -s $id shell wm density) -join "`n"
        $restoreSize = if ($size -match 'Override size:\s*(\d+x\d+)') { $Matches[1] } else { 'reset' }
        $restoreDensity = if ($density -match 'Override density:\s*(\d+)') { $Matches[1] } else { 'reset' }
        try {
            Invoke-Checked $adb @('-s',$id,'shell','wm','size','1080x2400')
            Invoke-Checked $adb @('-s',$id,'shell','wm','density','420')
            & (Join-Path $PSScriptRoot 'test-mobile.ps1') -Sdk $Sdk -Jdk $Jdk -Kotlin $Kotlin -Device $id -AppApk $mobile
            $entry.mobile = 'passed'
            Invoke-Checked $adb @('-s',$id,'shell','wm','size','1920x1080')
            Invoke-Checked $adb @('-s',$id,'shell','wm','density','320')
            & (Join-Path $PSScriptRoot 'test-tv-ui.ps1') -Sdk $Sdk -Jdk $Jdk -Kotlin $Kotlin -Device $id -AppApk $tv
            $entry.tv = 'passed'
        } finally {
            Invoke-Checked $adb @('-s',$id,'shell','wm','size',$restoreSize)
            Invoke-Checked $adb @('-s',$id,'shell','wm','density',$restoreDensity)
        }
    }
} catch { $failure = $_; if ($null -ne $entry) { if ($entry.mobile -eq 'not run') { $entry.mobile = 'failed' } else { $entry.tv = 'failed' } } }
finally {
    $matrix = foreach ($api in @(28,29,30,31,32,33,34,35,36)) {
        $tested = @($results | Where-Object { $_.api -eq $api })
        if ($tested.Count -gt 0) { $tested } else { [ordered]@{ api = $api; environment = 'not selected'; mobile = 'not run'; tv = 'not run' } }
    }
    $report = [ordered]@{ version = $version.Name; createdUtc = [DateTime]::UtcNow.ToString('o'); emulatorRuns = $results; matrix = @($matrix); physicalDevices = 'not tested by this script' }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Output) | Out-Null
    [IO.File]::WriteAllText($Output, ($report | ConvertTo-Json -Depth 6), [Text.UTF8Encoding]::new($false))
}
if ($failure) { throw $failure }
Write-Output "COMPATIBILITY CHECKS PASSED for selected emulators only. Report: $Output"
