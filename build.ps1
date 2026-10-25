param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$Kotlin = 'C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc',
    [string]$Output = (Join-Path $PSScriptRoot 'dist'),
    [ValidateSet('All','TV','Mobile','Windows')][string]$Target = 'All'
)
$ErrorActionPreference = 'Stop'
function Run-Checked([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed: $LASTEXITCODE" }
}
$platform = Get-ChildItem -LiteralPath (Join-Path $Sdk 'platforms') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'android.jar') } |
    Where-Object { $_.Name -match '^android-\d+(\.\d+)?$' -and [int]($_.Name -replace '^android-(\d+).*','$1') -ge 34 } |
    Sort-Object { [version](($_.Name -replace '^android-','') + '.0') } -Descending | Select-Object -First 1
if (!$platform) { throw 'Install an Android SDK platform (API 34 or newer).' }
$tools = Get-ChildItem -LiteralPath (Join-Path $Sdk 'build-tools') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'lib\d8.jar') } |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (!$tools) { throw 'Install Android SDK build-tools.' }
$androidJar = Join-Path $platform.FullName 'android.jar'
$toolPath = $tools.FullName
$stdlib = Join-Path $Kotlin 'lib\kotlin-stdlib.jar'
$annotations = Join-Path $Kotlin 'lib\annotations-13.0.jar'
$java = Join-Path $Jdk 'bin\java.exe'
$jar = Join-Path $Jdk 'bin\jar.exe'
$build = Join-Path $PSScriptRoot ('build\run-' + [guid]::NewGuid().ToString('N'))
$local = Join-Path $PSScriptRoot '.local'
New-Item -ItemType Directory -Force -Path $build,$Output,$local | Out-Null
$keystore = Join-Path $local 'signing.p12'
$passwordFile = Join-Path $local 'signing-password.txt'
if (!(Test-Path -LiteralPath $keystore)) {
    $randomBytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($randomBytes); $rng.Dispose()
    [IO.File]::WriteAllText($passwordFile,[Convert]::ToBase64String($randomBytes),[Text.Encoding]::ASCII)
    Run-Checked (Join-Path $Jdk 'bin\keytool.exe') @('-genkeypair','-keystore',$keystore,'-storetype','PKCS12','-storepass:file',$passwordFile,'-alias','tvtext-local','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=TVText Personal Build','-noprompt')
}
function Build-Apk([string]$SourceName, [string]$ApkName) {
    $sourceRoot = Join-Path $PSScriptRoot $SourceName
    $appBuild = Join-Path $build $SourceName
    $classes = Join-Path $appBuild 'classes'
    $dex = Join-Path $appBuild 'dex'
    $generated = Join-Path $appBuild 'generated'
    New-Item -ItemType Directory -Force -Path $classes,$dex,$generated | Out-Null
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $sourceRoot 'src') -Recurse -Filter '*.kt' | ForEach-Object FullName)
    Run-Checked $java (@('-cp',(Join-Path $Kotlin 'lib\*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-reflect','-no-stdlib','-jvm-target','1.8','-classpath',"$androidJar;$stdlib",'-d',$classes) + $sources)
    $appJar = Join-Path $appBuild 'app.jar'
    Run-Checked $jar @("-J-Djava.io.tmpdir=$appBuild",'cf',$appJar,'-C',$classes,'.')
    Run-Checked $java @('-cp',(Join-Path $toolPath 'lib\d8.jar'),'com.android.tools.r8.R8','--release','--min-api','28','--lib',$androidJar,'--pg-conf',(Join-Path $sourceRoot 'r8-rules.pro'),'--output',$dex,$appJar,$stdlib,$annotations)
    $resources = Join-Path $appBuild 'resources.zip'
    Run-Checked (Join-Path $toolPath 'aapt2.exe') @('compile','--dir',(Join-Path $sourceRoot 'res'),'-o',$resources)
    $unsigned = Join-Path $appBuild 'unsigned.apk'
    Run-Checked (Join-Path $toolPath 'aapt2.exe') @('link','--manifest',(Join-Path $sourceRoot 'AndroidManifest.xml'),'-I',$androidJar,'-A',(Join-Path $sourceRoot 'assets'),'--min-sdk-version','28','--target-sdk-version','34','--java',$generated,'-o',$unsigned,$resources)
    foreach ($dexFile in Get-ChildItem -LiteralPath $dex -Filter '*.dex') { Run-Checked $jar @("-J-Djava.io.tmpdir=$appBuild",'uf',$unsigned,'-C',$dex,$dexFile.Name) }
    $aligned = Join-Path $appBuild 'aligned.apk'
    Run-Checked (Join-Path $toolPath 'zipalign.exe') @('-f','4',$unsigned,$aligned)
    $apk = Join-Path $Output $ApkName
    Run-Checked $java @('-jar',(Join-Path $toolPath 'lib\apksigner.jar'),'sign','--ks',$keystore,'--ks-key-alias','tvtext-local','--ks-pass',"file:$passwordFile",'--v1-signing-enabled','false','--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false','--out',$apk,$aligned)
    Run-Checked $java @('-jar',(Join-Path $toolPath 'lib\apksigner.jar'),'verify','--verbose',$apk)
    Write-Output "Built $SourceName`: $apk"
}
if($Target -in @('All','TV')) { Build-Apk 'android-tv' 'TVText-TV-2.0.7.apk' }
if($Target -in @('All','Mobile')) { Build-Apk 'android' 'TVText-Mobile-2.0.7.apk' }
if($Target -in @('All','Windows')) {
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$exe = Join-Path $Output 'TVText-2.0.7.exe'
$csharp = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'windows') -Filter '*.cs' | ForEach-Object FullName)
Run-Checked $csc (@('/nologo','/target:winexe','/platform:anycpu','/optimize+','/codepage:65001',('/out:'+$exe),('/win32manifest:'+(Join-Path $PSScriptRoot 'windows\app.manifest')),('/win32icon:'+(Join-Path $PSScriptRoot 'windows\TVText.ico')),'/r:System.dll','/r:System.Core.dll','/r:System.Drawing.dll','/r:System.Windows.Forms.dll','/r:System.Security.dll') + $csharp)
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'windows\TVText.exe.config') -Destination (Join-Path $Output 'TVText-2.0.7.exe.config')
}
Write-Output "Built: $Output"
