param(
    [Parameter(Mandatory=$true)][string]$Device,
    [Parameter(Mandatory=$true)][string]$AppApk,
    [string]$AppJar = '',
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$Kotlin = 'C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc'
)
$ErrorActionPreference='Stop'
if($Device -notmatch '^emulator-\d+$'){ throw 'TV UI tests may target only an explicitly selected isolated emulator.' }
. (Join-Path $PSScriptRoot 'scripts\Common.ps1')
$jvm = Resolve-JvmTools $Jdk $Kotlin
$android = Resolve-AndroidTools $Sdk
$verifiedBuild = Resolve-TestBuild $AppApk $AppJar 'android-tv'
$AppApk = $verifiedBuild.Apk
$AppJar = $verifiedBuild.AppJar
$adb = Require-File (Join-Path $Sdk 'platform-tools\adb.exe') 'Android adb.exe'
$null = Require-File (Join-Path $PSScriptRoot '.local\signing.p12') 'APK signing key'
$null = Require-File (Join-Path $PSScriptRoot '.local\signing-password.txt') 'APK signing password file'
$run=Join-Path $PSScriptRoot ('build\tv-ui-test-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $run 'classes'
$dex=Join-Path $run 'dex'
New-Item -ItemType Directory -Path $classes,$dex | Out-Null
$androidJar = $android.AndroidJar
$tools = Get-Item -LiteralPath $android.BuildTools
$stdlib = $jvm.Stdlib
$java = $jvm.Java
$jar = $jvm.Jar
Invoke-Checked (Join-Path $Jdk 'bin\javac.exe') @('-source','8','-target','8','-Xlint:-options','-encoding','UTF-8','-classpath',"$androidJar;$stdlib;$AppJar",'-d',$classes,(Join-Path $PSScriptRoot 'tests\android-tv\ui\ThemeDeviceTests.java'),(Join-Path $PSScriptRoot 'tests\android-tv\ui\TestKeyboard.java'),(Join-Path $PSScriptRoot 'tests\android-tv\ui\ExternalFieldChecks.java'))
$testJar=Join-Path $run 'tv-ui-tests.jar'
Invoke-Checked $jar @('cf',$testJar,'-C',$classes,'.')
Invoke-Checked $java @('-cp',(Join-Path $tools.FullName 'lib\d8.jar'),'com.android.tools.r8.D8','--min-api','28','--lib',$androidJar,'--classpath',$AppJar,'--classpath',$stdlib,'--output',$dex,$testJar)
$unsigned=Join-Path $run 'unsigned.apk'
$resources=Join-Path $run 'resources.zip'
Invoke-Checked (Join-Path $tools.FullName 'aapt2.exe') @('compile','--dir',(Join-Path $PSScriptRoot 'tests\android-tv\ui\res'),'-o',$resources)
Invoke-Checked (Join-Path $tools.FullName 'aapt2.exe') @('link','--manifest',(Join-Path $PSScriptRoot 'tests\android-tv\ui\AndroidManifest.xml'),'-I',$androidJar,'--min-sdk-version','28','--target-sdk-version','34','-o',$unsigned,$resources)
foreach($file in Get-ChildItem -LiteralPath $dex -Filter '*.dex'){ Invoke-Checked $jar @('uf',$unsigned,'-C',$dex,$file.Name) }
$aligned=Join-Path $run 'aligned.apk'
Invoke-Checked (Join-Path $tools.FullName 'zipalign.exe') @('-f','4',$unsigned,$aligned)
$testApk=Join-Path $run 'tv-ui-tests.apk'
Invoke-Checked $java @('-jar',(Join-Path $tools.FullName 'lib\apksigner.jar'),'sign','--ks',(Join-Path $PSScriptRoot '.local\signing.p12'),'--ks-key-alias','tvtext-local','--ks-pass',('file:'+(Join-Path $PSScriptRoot '.local\signing-password.txt')),'--v4-signing-enabled','false','--out',$testApk,$aligned)
$fieldClasses=Join-Path $run 'field-classes';$fieldDex=Join-Path $run 'field-dex'
New-Item -ItemType Directory -Path $fieldClasses,$fieldDex | Out-Null
Invoke-Checked (Join-Path $Jdk 'bin\javac.exe') @('-source','8','-target','8','-Xlint:-options','-encoding','UTF-8','-classpath',$androidJar,'-d',$fieldClasses,(Join-Path $PSScriptRoot 'tests\android-tv\fixture\FieldActivity.java'))
$fieldJar=Join-Path $run 'field.jar'
Invoke-Checked $jar @('cf',$fieldJar,'-C',$fieldClasses,'.')
Invoke-Checked $java @('-cp',(Join-Path $tools.FullName 'lib\d8.jar'),'com.android.tools.r8.D8','--min-api','28','--lib',$androidJar,'--output',$fieldDex,$fieldJar)
$fieldUnsigned=Join-Path $run 'field-unsigned.apk'
Invoke-Checked (Join-Path $tools.FullName 'aapt2.exe') @('link','--manifest',(Join-Path $PSScriptRoot 'tests\android-tv\fixture\AndroidManifest.xml'),'-I',$androidJar,'-o',$fieldUnsigned)
foreach($file in Get-ChildItem -LiteralPath $fieldDex -Filter '*.dex'){ Invoke-Checked $jar @('uf',$fieldUnsigned,'-C',$fieldDex,$file.Name) }
$fieldAligned=Join-Path $run 'field-aligned.apk';$fieldApk=Join-Path $run 'field.apk'
Invoke-Checked (Join-Path $tools.FullName 'zipalign.exe') @('-f','4',$fieldUnsigned,$fieldAligned)
Invoke-Checked $java @('-jar',(Join-Path $tools.FullName 'lib\apksigner.jar'),'sign','--ks',(Join-Path $PSScriptRoot '.local\signing.p12'),'--ks-key-alias','tvtext-local','--ks-pass',('file:'+(Join-Path $PSScriptRoot '.local\signing-password.txt')),'--v4-signing-enabled','false','--out',$fieldApk,$fieldAligned)
$adb=Join-Path $Sdk 'platform-tools\adb.exe'
Invoke-Checked $adb @('-s',$Device,'install','-r',$AppApk)
Invoke-Checked $adb @('-s',$Device,'install','-r',$testApk)
Invoke-Checked $adb @('-s',$Device,'install','-r',$fieldApk)
$drawing=& $adb -s $Device shell getprop debug.hwui.drawing_enabled
if($drawing.Trim() -eq '0'){ Invoke-Checked $adb @('-s',$Device,'shell','setprop','debug.hwui.drawing_enabled','1') }
$result=& $adb -s $Device shell am instrument -w -r 'local.tvtext.uitests/.ThemeDeviceTests'
$result
if($LASTEXITCODE -ne 0 -or !($result -match 'PASS: Android TV themes:') -or ($result -match 'FAIL:')){ throw 'TV UI instrumentation failed.' }
foreach($name in @('dark-accessibility-off','light-accessibility-off','dark-pairing','light-pairing','dark-connections','light-connections','dark-connections-many','dark-revoke','dark-keyboard-test','dark-ready','en-ready')){
    Invoke-Checked $adb @('-s',$Device,'pull',('/sdcard/Android/data/com.tvtext.app/files/TVText-TV-'+$name+'.png'),(Join-Path $run ($name+'.png')))
}
Write-Output "TV UI TESTS PASSED. Results: $run"
