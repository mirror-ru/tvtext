param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$Kotlin = 'C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc',
    [string]$Output = (Join-Path $PSScriptRoot 'dist'),
    [ValidateSet('All','TV','Mobile','Windows')][string]$Target = 'All'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\Common.ps1')
$version = Read-ProjectVersion
$needsAndroid = $Target -in @('All', 'TV', 'Mobile')
$needsWindows = $Target -in @('All', 'Windows')

# Validate only the tools needed by the requested target, before creating output.
if ($needsWindows) { $csc = Resolve-CSharpCompiler }
if ($needsAndroid) {
    $jvm = Resolve-JvmTools $Jdk $Kotlin
    $android = Resolve-AndroidTools $Sdk
    $java = $jvm.Java
    $jar = $jvm.Jar
    $stdlib = $jvm.Stdlib
    $annotations = $jvm.Annotations
    $androidJar = $android.AndroidJar
    $toolPath = $android.BuildTools
}
$build = Join-Path $PSScriptRoot ('build\run-' + [guid]::NewGuid().ToString('N'))
$Output = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Output)
New-Item -ItemType Directory -Force -Path $build, $Output | Out-Null

if ($needsAndroid) {
    $local = Join-Path $PSScriptRoot '.local'
    $keystore = Join-Path $local 'signing.p12'
    $passwordFile = Join-Path $local 'signing-password.txt'
    if ((Test-Path -LiteralPath $keystore) -ne (Test-Path -LiteralPath $passwordFile)) {
        throw 'APK signing key/password pair is incomplete. Restore the missing file from your private backup.'
    }
    if (!(Test-Path -LiteralPath $keystore)) {
        New-Item -ItemType Directory -Force -Path $local | Out-Null
        $randomBytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
        [IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String($randomBytes), [Text.Encoding]::ASCII)
        Invoke-Checked $jvm.Keytool @('-genkeypair', '-keystore', $keystore, '-storetype', 'PKCS12',
            '-storepass:file', $passwordFile, '-alias', 'tvtext-local', '-keyalg', 'RSA',
            '-keysize', '2048', '-validity', '10000', '-dname', 'CN=TVText Personal Build', '-noprompt')
    }
    $sourceHash = Get-SourceFingerprint
}

function Build-Apk([string]$SourceName, [string]$ApkName) {
    $sourceRoot = Join-Path $PSScriptRoot $SourceName
    $appBuild = Join-Path $build $SourceName
    $classes = Join-Path $appBuild 'classes'
    $dex = Join-Path $appBuild 'dex'
    $generated = Join-Path $appBuild 'generated'
    New-Item -ItemType Directory -Force -Path $classes, $dex, $generated | Out-Null
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $sourceRoot 'src') -Recurse -Filter '*.kt' |
        Sort-Object FullName | ForEach-Object FullName)
    Invoke-Checked $java (@('-cp', (Join-Path $Kotlin 'lib\*'), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-reflect', '-no-stdlib', '-jvm-target', '1.8', '-classpath', "$androidJar;$stdlib", '-d', $classes) + $sources)
    $appJar = Join-Path $appBuild 'app.jar'
    Invoke-Checked $jar @("-J-Djava.io.tmpdir=$appBuild", 'cf', $appJar, '-C', $classes, '.')
    Invoke-Checked $java @('-cp', (Join-Path $toolPath 'lib\d8.jar'), 'com.android.tools.r8.R8',
        '--release', '--min-api', '28', '--lib', $androidJar,
        '--pg-conf', (Join-Path $sourceRoot 'r8-rules.pro'), '--output', $dex, $appJar, $stdlib, $annotations)
    $resources = Join-Path $appBuild 'resources.zip'
    Invoke-Checked (Join-Path $toolPath 'aapt2.exe') @('compile', '--dir', (Join-Path $sourceRoot 'res'), '-o', $resources)
    $unsigned = Join-Path $appBuild 'unsigned.apk'
    Invoke-Checked (Join-Path $toolPath 'aapt2.exe') @('link',
        '--manifest', (Join-Path $sourceRoot 'AndroidManifest.xml'), '-I', $androidJar,
        '-A', (Join-Path $sourceRoot 'assets'), '--min-sdk-version', '28', '--target-sdk-version', '34',
        '--version-code', $version.AndroidCode.ToString(), '--version-name', $version.Name,
        '--java', $generated, '-o', $unsigned, $resources)
    foreach ($dexFile in Get-ChildItem -LiteralPath $dex -Filter '*.dex' | Sort-Object Name) {
        Invoke-Checked $jar @("-J-Djava.io.tmpdir=$appBuild", 'uf', $unsigned, '-C', $dex, $dexFile.Name)
    }
    $aligned = Join-Path $appBuild 'aligned.apk'
    Invoke-Checked (Join-Path $toolPath 'zipalign.exe') @('-f', '4', $unsigned, $aligned)
    $apk = Join-Path $Output $ApkName
    Invoke-Checked $java @('-jar', (Join-Path $toolPath 'lib\apksigner.jar'), 'sign',
        '--ks', $keystore, '--ks-key-alias', 'tvtext-local', '--ks-pass', "file:$passwordFile",
        '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
        '--v4-signing-enabled', 'false', '--out', $apk, $aligned)
    Invoke-Checked $java @('-jar', (Join-Path $toolPath 'lib\apksigner.jar'), 'verify', '--verbose', $apk)
    Write-ApkBuildRecord $apk $appJar $SourceName $version $sourceHash
    Write-Output "Built $SourceName : $apk"
}
if ($Target -in @('All', 'TV')) { Build-Apk 'android-tv' "TVText-TV-$($version.Name).apk" }
if ($Target -in @('All', 'Mobile')) { Build-Apk 'android-mobile' "TVText-Mobile-$($version.Name).apk" }
if ($needsWindows) {
    $metadata = Write-WindowsVersionFiles $build $version
    $exe = Join-Path $Output "TVText-$($version.Name).exe"
    $csharp = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'windows') -Filter '*.cs' |
        Sort-Object Name | ForEach-Object FullName)
    Invoke-Checked $csc (@('/nologo', '/target:winexe', '/platform:anycpu', '/optimize+', '/codepage:65001',
        ('/out:' + $exe), ('/win32manifest:' + $metadata.Manifest),
        ('/win32icon:' + (Join-Path $PSScriptRoot 'windows\TVText.ico')),
        '/r:System.dll', '/r:System.Core.dll', '/r:System.Drawing.dll',
        '/r:System.Windows.Forms.dll', '/r:System.Security.dll', $metadata.AssemblyInfo) + $csharp)
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'windows\TVText.exe.config') -Destination "$exe.config"
}
Write-Output "Built: $Output"
