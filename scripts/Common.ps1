# Shared by the root build/test entry points. Importing this file has no side effects.
$script:ProjectRoot = Split-Path -Parent $PSScriptRoot

function Invoke-Checked([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed: $LASTEXITCODE" }
}

function Assert-ProcessSuccess([Diagnostics.Process]$Process, [string]$Description) {
    if (!$Process.HasExited) { throw "$Description has not stopped." }
    if ($null -eq $Process.ExitCode -or $Process.ExitCode -ne 0) {
        throw "$Description failed with exit code $($Process.ExitCode)."
    }
}

function Read-ProjectVersion([string]$Path = (Join-Path $script:ProjectRoot 'version.json')) {
    $value = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    if ($value.name -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$') {
        throw 'version.json: name must be a three-part release version, such as 2.0.7.'
    }
    foreach ($part in $value.name.Split('.')) {
        if ([long]$part -gt 65534) { throw 'version.json: version components must not exceed 65534.' }
    }
    if ($value.androidCode -isnot [int] -and $value.androidCode -isnot [long]) {
        throw 'version.json: androidCode must be an integer.'
    }
    if ($value.androidCode -lt 1 -or $value.androidCode -gt 2100000000) {
        throw 'version.json: androidCode must be between 1 and 2100000000.'
    }
    [pscustomobject]@{ Name = $value.name; AndroidCode = [int]$value.androidCode; Assembly = "$($value.name).0" }
}

function Read-Toolchain {
    Get-Content -LiteralPath (Join-Path $script:ProjectRoot 'toolchain.json') -Raw | ConvertFrom-Json
}

function Require-File([string]$Path, [string]$Description) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { throw "$Description not found: $Path" }
    (Resolve-Path -LiteralPath $Path).Path
}

function Read-Property([string]$Path, [string]$Name) {
    $line = Get-Content -LiteralPath $Path | Where-Object { $_.StartsWith("$Name=") } | Select-Object -First 1
    if ($null -eq $line) { throw "Property $Name not found in $Path" }
    $line.Substring($Name.Length + 1).Trim('"')
}

function Assert-ToolVersion([string]$Name, [string]$Actual, [string]$Expected) {
    if ($Actual -cne $Expected) {
        throw "$Name version mismatch: expected $Expected, found $Actual. Use the pinned version in toolchain.json."
    }
}

function Resolve-JvmTools([string]$Jdk, [string]$Kotlin) {
    $pins = Read-Toolchain
    $java = Require-File (Join-Path $Jdk 'bin\java.exe') 'JDK java.exe (pass -Jdk)'
    $release = Require-File (Join-Path $Jdk 'release') 'JDK release metadata'
    Assert-ToolVersion 'JDK' (Read-Property $release 'JAVA_VERSION') $pins.jdk
    $kotlinBuild = Require-File (Join-Path $Kotlin 'build.txt') 'Kotlin build metadata (pass -Kotlin)'
    Assert-ToolVersion 'Kotlin' ((Get-Content -LiteralPath $kotlinBuild -Raw).Trim()) $pins.kotlin
    [pscustomobject]@{
        Java = $java
        Jar = Require-File (Join-Path $Jdk 'bin\jar.exe') 'JDK jar.exe'
        Keytool = Require-File (Join-Path $Jdk 'bin\keytool.exe') 'JDK keytool.exe'
        Stdlib = Require-File (Join-Path $Kotlin 'lib\kotlin-stdlib.jar') 'Kotlin standard library'
        Annotations = Require-File (Join-Path $Kotlin 'lib\annotations-13.0.jar') 'Kotlin annotations'
    }
}

function Resolve-AndroidBuildTools([string]$Sdk) {
    $pins = Read-Toolchain
    $directory = Join-Path $Sdk "build-tools\$($pins.androidBuildTools)"
    $properties = Require-File (Join-Path $directory 'source.properties') "Android build-tools $($pins.androidBuildTools) metadata"
    Assert-ToolVersion 'Android build-tools' (Read-Property $properties 'Pkg.Revision') $pins.androidBuildTools
    foreach ($file in @('aapt2.exe', 'zipalign.exe', 'lib\d8.jar', 'lib\apksigner.jar')) {
        $null = Require-File (Join-Path $directory $file) 'Android build tool (install the pinned package or pass -Sdk)'
    }
    (Resolve-Path -LiteralPath $directory).Path
}

function Resolve-AndroidTools([string]$Sdk) {
    $pins = Read-Toolchain
    $platform = Join-Path $Sdk "platforms\$($pins.androidPlatform)"
    $properties = Require-File (Join-Path $platform 'source.properties') "Android platform $($pins.androidPlatform) metadata"
    Assert-ToolVersion 'Android platform revision' (Read-Property $properties 'Pkg.Revision') $pins.androidPlatformRevision
    [pscustomobject]@{
        AndroidJar = Require-File (Join-Path $platform 'android.jar') "Android platform $($pins.androidPlatform) (pass -Sdk)"
        BuildTools = Resolve-AndroidBuildTools $Sdk
    }
}

function Resolve-CSharpCompiler {
    Require-File (Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe') '.NET Framework C# compiler'
}

function Write-WindowsVersionFiles([string]$Directory, $Version) {
    $assemblyInfo = Join-Path $Directory 'VersionInfo.cs'
    $manifest = Join-Path $Directory 'app.manifest'
    $text = 'using System.Reflection;' + "`r`n" +
        '[assembly: AssemblyVersion("' + $Version.Assembly + '")]' + "`r`n" +
        '[assembly: AssemblyFileVersion("' + $Version.Assembly + '")]' + "`r`n"
    [IO.File]::WriteAllText($assemblyInfo, $text, [Text.UTF8Encoding]::new($false))
    $source = Get-Content -LiteralPath (Join-Path $script:ProjectRoot 'windows\app.manifest') -Raw
    if (!$source.Contains('@TVTEXT_ASSEMBLY_VERSION@')) { throw 'Windows manifest version placeholder is missing.' }
    [IO.File]::WriteAllText($manifest, $source.Replace('@TVTEXT_ASSEMBLY_VERSION@', $Version.Assembly), [Text.UTF8Encoding]::new($false))
    [pscustomobject]@{ AssemblyInfo = $assemblyInfo; Manifest = $manifest }
}

function Get-SourceFingerprint {
    # Relative names and content are hashed together; generated files and local secrets are excluded.
    $files = @('version.json', 'toolchain.json', 'build.ps1') | ForEach-Object { Get-Item -LiteralPath (Join-Path $script:ProjectRoot $_) }
    foreach ($directory in @('scripts', 'android-mobile', 'android-tv', 'windows')) {
        $files += @(Get-ChildItem -LiteralPath (Join-Path $script:ProjectRoot $directory) -File -Recurse)
    }
    [string[]]$relativePaths = @($files | ForEach-Object {
        $_.FullName.Substring($script:ProjectRoot.Length + 1).Replace('\', '/')
    })
    # Sort-Object uses host-dependent culture rules. The build record must be
    # identical under Windows PowerShell 5.1 and PowerShell 7.
    [Array]::Sort($relativePaths, [StringComparer]::Ordinal)
    $lines = foreach ($relative in $relativePaths) {
        $relative + ':' + (Get-FileHash -LiteralPath (Join-Path $script:ProjectRoot $relative) -Algorithm SHA256).Hash
    }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines -join "`n"))))).Replace('-', '') }
    finally { $sha.Dispose() }
}

function Write-ApkBuildRecord([string]$Apk, [string]$AppJar, [string]$Target, $Version, [string]$SourceHash) {
    $pins = Read-Toolchain
    $record = [ordered]@{
        schema = 1
        target = $Target
        version = $Version.Name
        androidCode = $Version.AndroidCode
        sourceHash = $SourceHash
        apkSha256 = (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash
        appJar = (Resolve-Path -LiteralPath $AppJar).Path
        appJarSha256 = (Get-FileHash -LiteralPath $AppJar -Algorithm SHA256).Hash
        toolchain = $pins
    }
    [IO.File]::WriteAllText("$Apk.build.json", ($record | ConvertTo-Json -Depth 4), [Text.UTF8Encoding]::new($false))
}

function Resolve-TestBuild([string]$Apk, [string]$AppJar, [string]$Target) {
    $apkPath = Require-File $Apk 'Release APK (run build.ps1 first or pass -AppApk)'
    $recordPath = Require-File "$apkPath.build.json" 'Matching build record (rebuild this APK locally)'
    $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
    $version = Read-ProjectVersion
    if ($record.schema -ne 1 -or $record.target -cne $Target -or
        $record.version -cne $version.Name -or $record.androidCode -ne $version.AndroidCode) {
        throw 'Build record target or version does not match this project. Rebuild the requested target.'
    }
    if ((Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash -cne $record.apkSha256) {
        throw 'APK hash differs from its build record. Rebuild the requested target.'
    }
    if (!$AppJar) { $AppJar = $record.appJar }
    $jarPath = Require-File $AppJar 'Exact app.jar from the recorded build (pass -AppJar if it was moved)'
    if ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash -cne $record.appJarSha256) {
        throw 'app.jar hash differs from the APK build record. Refusing to test a different build.'
    }
    if ($record.sourceHash -cne (Get-SourceFingerprint)) {
        throw 'Application or build sources changed after this APK was built. Rebuild before running device tests.'
    }
    if (($record.toolchain | ConvertTo-Json -Compress) -cne ((Read-Toolchain) | ConvertTo-Json -Compress)) {
        throw 'The build record uses another toolchain. Rebuild with the pinned versions.'
    }
    [pscustomobject]@{ Apk = $apkPath; AppJar = $jarPath }
}
