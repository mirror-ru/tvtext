param(
    [Parameter(Mandatory=$true)][string]$Device,
    [Parameter(Mandatory=$true)][string]$AppApk,
    [Parameter(Mandatory=$true)][string]$AppJar,
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$Kotlin = 'C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc'
)
$ErrorActionPreference='Stop'
if($Device -notmatch '^emulator-\d+$'){ throw 'TV UI tests may target only an explicitly selected isolated emulator.' }
function Checked([string]$Exe,[string[]]$Arguments){
    & $Exe @Arguments
    if($LASTEXITCODE -ne 0){ throw "$Exe failed: $LASTEXITCODE" }
}
$run=Join-Path $PSScriptRoot ('build\tv-ui-test-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $run 'classes'
$dex=Join-Path $run 'dex'
New-Item -ItemType Directory -Path $classes,$dex | Out-Null
$platform=Get-ChildItem -LiteralPath (Join-Path $Sdk 'platforms') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'android.jar') } |
    Sort-Object { [version](($_.Name -replace '^android-','')+'.0') } -Descending | Select-Object -First 1
$tools=Get-ChildItem -LiteralPath (Join-Path $Sdk 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$androidJar=Join-Path $platform.FullName 'android.jar'
$stdlib=Join-Path $Kotlin 'lib\kotlin-stdlib.jar'
$java=Join-Path $Jdk 'bin\java.exe'
$jar=Join-Path $Jdk 'bin\jar.exe'
Checked (Join-Path $Jdk 'bin\javac.exe') @('-source','8','-target','8','-Xlint:-options','-encoding','UTF-8','-classpath',"$androidJar;$stdlib;$AppJar",'-d',$classes,(Join-Path $PSScriptRoot 'tests\android-tv\ui\ThemeDeviceTests.java'),(Join-Path $PSScriptRoot 'tests\android-tv\ui\TestKeyboard.java'))
$testJar=Join-Path $run 'tv-ui-tests.jar'
Checked $jar @('cf',$testJar,'-C',$classes,'.')
Checked $java @('-cp',(Join-Path $tools.FullName 'lib\d8.jar'),'com.android.tools.r8.D8','--min-api','28','--lib',$androidJar,'--classpath',$AppJar,'--classpath',$stdlib,'--output',$dex,$testJar)
$unsigned=Join-Path $run 'unsigned.apk'
$resources=Join-Path $run 'resources.zip'
Checked (Join-Path $tools.FullName 'aapt2.exe') @('compile','--dir',(Join-Path $PSScriptRoot 'tests\android-tv\ui\res'),'-o',$resources)
Checked (Join-Path $tools.FullName 'aapt2.exe') @('link','--manifest',(Join-Path $PSScriptRoot 'tests\android-tv\ui\AndroidManifest.xml'),'-I',$androidJar,'--min-sdk-version','28','--target-sdk-version','34','-o',$unsigned,$resources)
foreach($file in Get-ChildItem -LiteralPath $dex -Filter '*.dex'){ Checked $jar @('uf',$unsigned,'-C',$dex,$file.Name) }
$aligned=Join-Path $run 'aligned.apk'
Checked (Join-Path $tools.FullName 'zipalign.exe') @('-f','4',$unsigned,$aligned)
$testApk=Join-Path $run 'tv-ui-tests.apk'
Checked $java @('-jar',(Join-Path $tools.FullName 'lib\apksigner.jar'),'sign','--ks',(Join-Path $PSScriptRoot '.local\signing.p12'),'--ks-key-alias','tvtext-local','--ks-pass',('file:'+(Join-Path $PSScriptRoot '.local\signing-password.txt')),'--v4-signing-enabled','false','--out',$testApk,$aligned)
$adb=Join-Path $Sdk 'platform-tools\adb.exe'
Checked $adb @('-s',$Device,'install','-r',$AppApk)
Checked $adb @('-s',$Device,'install','-r',$testApk)
$drawing=& $adb -s $Device shell getprop debug.hwui.drawing_enabled
if($drawing.Trim() -eq '0'){ Checked $adb @('-s',$Device,'shell','setprop','debug.hwui.drawing_enabled','1') }
$result=& $adb -s $Device shell am instrument -w -r 'local.tvtext.uitests/.ThemeDeviceTests'
$result
if($LASTEXITCODE -ne 0 -or !($result -match 'PASS: Android TV themes:') -or ($result -match 'FAIL:')){ throw 'TV UI instrumentation failed.' }
foreach($name in @('dark-accessibility-off','light-accessibility-off','dark-pairing','light-pairing','dark-connections','light-connections','dark-connections-many','dark-revoke','dark-keyboard-test','dark-ready','en-ready')){
    Checked $adb @('-s',$Device,'pull',('/sdcard/Android/data/com.tvtext.app/files/TVText-TV-'+$name+'.png'),(Join-Path $run ($name+'.png')))
}
Write-Output "TV UI TESTS PASSED. Results: $run"
