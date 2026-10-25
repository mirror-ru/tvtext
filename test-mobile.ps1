param(
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$Kotlin = 'C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc',
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Device = '',
    [string]$AppApk = '',
    [string]$AppJar = '',
    [string]$Screenshot = ''
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\Common.ps1')
$version = Read-ProjectVersion
$jvm = Resolve-JvmTools $Jdk $Kotlin
if (!$AppApk) { $AppApk = Join-Path $PSScriptRoot "dist\TVText-Mobile-$($version.Name).apk" }
if ($Device) {
    if ($Device -notmatch '^emulator-\d+$') { throw 'Device tests require an explicitly selected isolated emulator.' }
    $verifiedBuild = Resolve-TestBuild $AppApk $AppJar 'android-mobile'
    $AppApk = $verifiedBuild.Apk
    $AppJar = $verifiedBuild.AppJar
    $android = Resolve-AndroidTools $Sdk
    $adb = Require-File (Join-Path $Sdk 'platform-tools\adb.exe') 'Android adb.exe'
    $null = Require-File (Join-Path $PSScriptRoot '.local\signing.p12') 'APK signing key'
    $null = Require-File (Join-Path $PSScriptRoot '.local\signing-password.txt') 'APK signing password file'
}
$run = Join-Path $PSScriptRoot ('build\mobile-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $run | Out-Null
$java = $jvm.Java
$jar = $jvm.Jar
$stdlib = $jvm.Stdlib
$protocol = Join-Path $PSScriptRoot 'android-tv\src\com\tvtext\app\Protocol.kt'
$sender = Join-Path $PSScriptRoot 'android-mobile\src\com\tvtext\mobile\app\SenderClient.kt'
$fixtureSource = Join-Path $PSScriptRoot 'tests\android\PhoneFixture.kt'
$fixture = Join-Path $run 'fixture.p12'
Invoke-Checked (Join-Path $Jdk 'bin\keytool.exe') @('-J-Dkeystore.pkcs12.legacy=true','-genkeypair','-keystore',$fixture,'-storetype','PKCS12','-storepass','fixture-only','-alias','fixture','-keyalg','RSA','-keysize','2048','-validity','2','-dname','CN=TVText Mobile Test Fixture','-noprompt')
$catalogSource = Join-Path $PSScriptRoot 'android-mobile\src\com\tvtext\mobile\app\ConnectionCatalog.kt'
$catalogTests = Join-Path $PSScriptRoot 'tests\android\ConnectionCatalogTests.kt'
$core = Join-Path $run 'mobile-core-tests.jar'
Invoke-Checked $java @('-cp',(Join-Path $Kotlin 'lib\*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-reflect','-no-stdlib','-jvm-target','1.8','-classpath',$stdlib,'-include-runtime','-kotlin-home',$Kotlin,'-d',$core,$protocol,$sender,$catalogSource,$catalogTests,$fixtureSource,(Join-Path $PSScriptRoot 'tests\android\MobileCoreTests.kt'))
Invoke-Checked $java @('-cp',$core,'local.tvtext.mobiletests.MobileCoreTests',$fixture)
if($Device) {
    $androidJar = $android.AndroidJar
    $tools = Get-Item -LiteralPath $android.BuildTools
    $classes = Join-Path $run 'classes'
    $dex = Join-Path $run 'dex'
    $assets = Join-Path $run 'assets'
    New-Item -ItemType Directory -Path $classes,$dex,$assets | Out-Null
    Copy-Item -LiteralPath $fixture -Destination $assets
    Invoke-Checked $java @('-cp',(Join-Path $Kotlin 'lib\*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-reflect','-no-stdlib','-jvm-target','1.8','-classpath',"$androidJar;$stdlib;$AppJar",'-d',$classes,$protocol,$fixtureSource,(Join-Path $PSScriptRoot 'tests\android\MobileDeviceTests.kt'))
    $testJar = Join-Path $run 'device-tests.jar'
    Invoke-Checked $jar @('cf',$testJar,'-C',$classes,'.')
    Invoke-Checked $java @('-cp',(Join-Path $tools.FullName 'lib\d8.jar'),'com.android.tools.r8.D8','--min-api','28','--lib',$androidJar,'--classpath',$AppJar,'--output',$dex,$testJar,$stdlib)
    $unsigned = Join-Path $run 'unsigned.apk'
    Invoke-Checked (Join-Path $tools.FullName 'aapt2.exe') @('link','--manifest',(Join-Path $PSScriptRoot 'tests\android\AndroidManifest.xml'),'-I',$androidJar,'-A',$assets,'--min-sdk-version','28','--target-sdk-version','34','-o',$unsigned)
    foreach($file in Get-ChildItem -LiteralPath $dex -Filter '*.dex') { Invoke-Checked $jar @('uf',$unsigned,'-C',$dex,$file.Name) }
    $aligned = Join-Path $run 'aligned.apk'
    Invoke-Checked (Join-Path $tools.FullName 'zipalign.exe') @('-f','4',$unsigned,$aligned)
    $testApk = Join-Path $run 'mobile-tests.apk'
    Invoke-Checked $java @('-jar',(Join-Path $tools.FullName 'lib\apksigner.jar'),'sign','--ks',(Join-Path $PSScriptRoot '.local\signing.p12'),'--ks-key-alias','tvtext-local','--ks-pass',('file:'+(Join-Path $PSScriptRoot '.local\signing-password.txt')),'--v4-signing-enabled','false','--out',$testApk,$aligned)
    $adb = Join-Path $Sdk 'platform-tools\adb.exe'
    Invoke-Checked $adb @('-s',$Device,'install','-r',$AppApk)
    Invoke-Checked $adb @('-s',$Device,'install','-r',$testApk)
    # Start with a fresh target process after a previous UI/instrumentation run.
    Invoke-Checked $adb @('-s',$Device,'shell','am','force-stop','com.tvtext.mobile.app')
    # ATD images disable drawing by default. Enable it only in this emulator
    # before the target process starts, so captures contain the real UI.
    $drawing = & $adb -s $Device shell getprop debug.hwui.drawing_enabled
    if($drawing.Trim() -eq '0') { Invoke-Checked $adb @('-s',$Device,'shell','setprop','debug.hwui.drawing_enabled','1') }
    $result = & $adb -s $Device shell am instrument -w -r 'local.tvtext.mobiletests/.MobileDeviceTests'
    $result
    if($LASTEXITCODE -ne 0 -or !($result -match 'PASS: Android API \d+:') -or ($result -match 'FAIL:')) { throw 'Mobile Android instrumentation failed.' }
    if(!$Screenshot) { $Screenshot = Join-Path $run 'TVText-Mobile.png' }
    Invoke-Checked $adb @('-s',$Device,'pull','/sdcard/Android/data/com.tvtext.mobile.app/files/TVText-Mobile.png',$Screenshot)
}
Write-Output "MOBILE TESTS PASSED. Results: $run"
