param(
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$Kotlin = 'C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc',
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [switch]$Shrink
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\Common.ps1')
& (Join-Path $PSScriptRoot 'tests\tooling\BuildTests.ps1')
$jvm = Resolve-JvmTools $Jdk $Kotlin
$csc = Resolve-CSharpCompiler
$version = Read-ProjectVersion
if ($Shrink) { $r8Tools = Resolve-AndroidBuildTools $Sdk }
$run = Join-Path $PSScriptRoot ('build\test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $run | Out-Null
$java = $jvm.Java
$testJar = Join-Path $run 'core-tests.jar'
$kotlinTests = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\android-tv') -Filter '*.kt' | ForEach-Object FullName)
Invoke-Checked $java (@('-cp',(Join-Path $Kotlin 'lib\*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-reflect','-no-stdlib','-jvm-target','1.8','-classpath',(Join-Path $Kotlin 'lib\kotlin-stdlib.jar'),'-include-runtime','-kotlin-home',$Kotlin,'-d',$testJar,(Join-Path $PSScriptRoot 'android-tv\src\com\tvtext\app\Protocol.kt'),(Join-Path $PSScriptRoot 'android-tv\src\com\tvtext\app\ReceiverStatus.kt')) + $kotlinTests)
if($Shrink) {
    $r8 = Get-Item -LiteralPath $r8Tools
    $shrunkJar = Join-Path $run 'shrunk-tests.jar'
    Invoke-Checked $java @('-cp',(Join-Path $r8.FullName 'lib\d8.jar'),'com.android.tools.r8.R8','--classfile','--lib',$Jdk,'--pg-conf',(Join-Path $PSScriptRoot 'android-tv\r8-rules.pro'),'--output',$shrunkJar,$testJar,(Join-Path $Kotlin 'lib\annotations-13.0.jar'))
    $testJar = $shrunkJar
}
$metadata = Write-WindowsVersionFiles $run $version
$testExe = Join-Path $run 'ClientTests.exe'
$csharp = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'windows') -Filter '*.cs' | ForEach-Object FullName)
$csharp += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\windows') -Filter '*.cs' | ForEach-Object FullName)
Invoke-Checked $csc (@('/nologo','/target:exe','/main:ClientTests','/codepage:65001',('/out:'+$testExe),'/r:System.dll','/r:System.Core.dll','/r:System.Drawing.dll','/r:System.Windows.Forms.dll','/r:System.Security.dll',$metadata.AssemblyInfo) + $csharp)
Invoke-Checked $testExe @('dpi-layout')
Invoke-Checked $testExe @('connection-save')
Invoke-Checked $testExe @('sender-workflow')
# The normal fixture runs DPI-unaware. A second executable uses the release
# manifest so startup is also checked at the real Windows DPI, before any Scale().
$dpiTestExe = Join-Path $run 'ClientDpiTests.exe'
Invoke-Checked $csc (@('/nologo','/target:exe','/main:ClientTests','/codepage:65001',('/out:'+$dpiTestExe),('/win32manifest:'+$metadata.Manifest),'/r:System.dll','/r:System.Core.dll','/r:System.Drawing.dll','/r:System.Windows.Forms.dll','/r:System.Security.dll',$metadata.AssemblyInfo) + $csharp)
Invoke-Checked $dpiTestExe @('dpi-layout')
Invoke-Checked $dpiTestExe @('theme',(Join-Path $run 'theme-native-dpi'))
$fixture = Join-Path $run 'fixture.p12'
Invoke-Checked (Join-Path $Jdk 'bin\keytool.exe') @('-J-Dkeystore.pkcs12.legacy=true','-genkeypair','-keystore',$fixture,'-storetype','PKCS12','-storepass','fixture-only','-alias','fixture','-keyalg','RSA','-keysize','2048','-validity','2','-dname','CN=TVText Test Fixture','-noprompt')
$details = Join-Path $run 'fixture-details.txt'
$done = Join-Path $run 'fixture-done.txt'
$log = Join-Path $run 'server.log'
$err = Join-Path $run 'server-error.log'
$serverArgs = @('-cp', ('"'+$testJar+'"'), 'com.tvtext.app.CoreTests', ('"'+$fixture+'"'), ('"'+$details+'"'), ('"'+$done+'"'))
$server = Start-Process -FilePath $java -ArgumentList $serverArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput $log -RedirectStandardError $err
# Retain the process handle while it is alive: Windows cannot reliably retrieve
# ExitCode after PowerShell's HasExited polling closes an uncached handle.
$null = $server.Handle
try {
    $until = [DateTime]::UtcNow.AddSeconds(30)
    while (!(Test-Path -LiteralPath $details) -and !$server.HasExited -and [DateTime]::UtcNow -lt $until) { Start-Sleep -Milliseconds 100 }
    if (!(Test-Path -LiteralPath $details)) { throw 'TLS fixture did not start.' }
    Invoke-Checked $testExe @($details,$done)
    if (!$server.WaitForExit(15000)) { throw 'TLS fixture did not stop.' }
    $serverLog = @(Get-Content -LiteralPath $log)
    Assert-ProcessSuccess $server 'Kotlin TLS fixture'
    if ($serverLog -notcontains 'PASS: two independent TVs received only their selected UI messages, without broadcast or replay') {
        throw 'Kotlin fixture did not complete its assertions.'
    }
    $serverLog
    Invoke-Checked $testExe @('preview',(Join-Path $run 'windows-preview.png'))
    Invoke-Checked $testExe @('pair-dialog',(Join-Path $run 'pair-dialog'))
    Invoke-Checked $testExe @('device-list',(Join-Path $run 'device-list'))
    Invoke-Checked $testExe @('theme',(Join-Path $run 'theme'))
    Write-Output "ALL TESTS PASSED. Results: $run"
} finally {
    [IO.File]::WriteAllText($done,'done')
    if (!$server.HasExited -and !$server.WaitForExit(3000)) { Stop-Process -Id $server.Id }
    if (Test-Path -LiteralPath $err) { Get-Content -LiteralPath $err }
}
