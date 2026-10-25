# Local release validation. No uploads, credentials, or network requests.
function Get-ReleaseAssetNames($Version) {
    @("TVText-TV-$($Version.Name).apk", "TVText-Mobile-$($Version.Name).apk",
        "TVText-$($Version.Name).exe", "TVText-$($Version.Name).exe.config")
}

function Write-ReleaseChecksums([string]$Directory, [string[]]$Names) {
    $lines = foreach ($name in ($Names | Sort-Object)) {
        if ([IO.Path]::GetFileName($name) -cne $name) { throw 'Release asset names must not contain paths.' }
        $file = Require-File (Join-Path $Directory $name) 'Release asset'
        (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $name
    }
    [IO.File]::WriteAllText((Join-Path $Directory 'SHA256SUMS.txt'), ($lines -join "`n") + "`n", [Text.UTF8Encoding]::new($false))
}

function Assert-ReleaseChecksums([string]$Directory, [string[]]$Names) {
    $lines = @(Get-Content -LiteralPath (Join-Path $Directory 'SHA256SUMS.txt'))
    if ($lines.Count -ne $Names.Count) { throw 'Unexpected number of release checksums.' }
    $seen = @{}
    foreach ($line in $lines) {
        if ($line -notmatch '^([a-f0-9]{64})  ([^/\\]+)$') { throw 'Malformed release checksum.' }
        $hash = $Matches[1]; $name = $Matches[2]
        if ($Names -cnotcontains $name -or $seen.ContainsKey($name)) { throw 'Unexpected or duplicate checksum asset.' }
        $seen[$name] = $true
        if ((Get-FileHash -LiteralPath (Join-Path $Directory $name) -Algorithm SHA256).Hash.ToLowerInvariant() -cne $hash) {
            throw "Release checksum mismatch: $name"
        }
    }
}

function Assert-CleanRelease([string]$Directory, [string[]]$Names) {
    $expected = @($Names) + @('SHA256SUMS.txt', 'release-report.json')
    $found = @(Get-ChildItem -LiteralPath $Directory -Force)
    if ($found.Count -ne $expected.Count -or @($found | Where-Object { $_.PSIsContainer -or $expected -cnotcontains $_.Name }).Count -gt 0) {
        throw 'Release directory contains missing or unexpected files.'
    }
    Assert-ReleaseChecksums $Directory $Names
}

function Assert-ReleaseApk([string]$Apk, [string]$Package, $Version, $Jvm, $Android, [string]$ExpectedCertificate) {
    $metadata = & (Join-Path $Android.BuildTools 'aapt2.exe') dump badging $Apk
    if ($LASTEXITCODE -ne 0) { throw 'Could not read APK metadata.' }
    $packageLine = $metadata | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
    if ($packageLine -notmatch ("name='" + [regex]::Escape($Package) + "' versionCode='" + $Version.AndroidCode + "' versionName='" + [regex]::Escape($Version.Name) + "'")) {
        throw "APK package/version mismatch: $Apk"
    }
    if ($metadata -notcontains "minSdkVersion:'28'" -or $metadata -notcontains "targetSdkVersion:'34'") { throw 'APK SDK bounds differ from the supported release configuration.' }
    Invoke-Checked (Join-Path $Android.BuildTools 'zipalign.exe') @('-c','4',$Apk)
    $signature = & $Jvm.Java -jar (Join-Path $Android.BuildTools 'lib\apksigner.jar') verify --verbose --print-certs $Apk
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $Apk" }
    $certificates = @($signature | ForEach-Object {
        if ($_ -match 'certificate SHA-256 digest:\s*([a-fA-F0-9]{64})') { $Matches[1].ToLowerInvariant() }
    } | Sort-Object -Unique)
    if ($certificates.Count -ne 1 -or $certificates[0] -cne $ExpectedCertificate) { throw "Unexpected APK signing certificate: $Apk" }
    [pscustomobject]@{ package = $Package; certificateSha256 = $certificates[0]; minSdk = 28; targetSdk = 34 }
}
