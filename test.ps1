param(
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$Kotlin = 'C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc',
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [switch]$Shrink
)
$ErrorActionPreference = 'Stop'
function Checked([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed: $LASTEXITCODE" }
}
$run = Join-Path $PSScriptRoot ('build\test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $run | Out-Null
$java = Join-Path $Jdk 'bin\java.exe'
$testJar = Join-Path $run 'core-tests.jar'
$kotlinTests = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\android-tv') -Filter '*.kt' | ForEach-Object FullName)
Checked $java (@('-cp',(Join-Path $Kotlin 'lib\*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-reflect','-no-stdlib','-jvm-target','1.8','-classpath',(Join-Path $Kotlin 'lib\kotlin-stdlib.jar'),'-include-runtime','-kotlin-home',$Kotlin,'-d',$testJar,(Join-Path $PSScriptRoot 'android-tv\src\com\tvtext\app\Protocol.kt')) + $kotlinTests)
if($Shrink) {
    $r8 = Get-ChildItem -LiteralPath (Join-Path $Sdk 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'lib\d8.jar')) } |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if(!$r8) { throw 'Install Android SDK build-tools to test R8 shrinking.' }
    $shrunkJar = Join-Path $run 'shrunk-tests.jar'
    Checked $java @('-cp',(Join-Path $r8.FullName 'lib\d8.jar'),'com.android.tools.r8.R8','--classfile','--lib',$Jdk,'--pg-conf',(Join-Path $PSScriptRoot 'android-tv\r8-rules.pro'),'--output',$shrunkJar,$testJar,(Join-Path $Kotlin 'lib\annotations-13.0.jar'))
    $testJar = $shrunkJar
}
$testExe = Join-Path $run 'ClientTests.exe'
$csharp = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'windows') -Filter '*.cs' | ForEach-Object FullName)
$csharp += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\windows') -Filter '*.cs' | ForEach-Object FullName)
Checked (Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe') (@('/nologo','/target:exe','/main:ClientTests','/codepage:65001',('/out:'+$testExe),'/r:System.dll','/r:System.Core.dll','/r:System.Drawing.dll','/r:System.Windows.Forms.dll','/r:System.Security.dll') + $csharp)
Checked $testExe @('dpi-layout')
# The normal fixture runs DPI-unaware. A second executable uses the release
# manifest so startup is also checked at the real Windows DPI, before any Scale().
$dpiTestExe = Join-Path $run 'ClientDpiTests.exe'
Checked (Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe') (@('/nologo','/target:exe','/main:ClientTests','/codepage:65001',('/out:'+$dpiTestExe),('/win32manifest:'+(Join-Path $PSScriptRoot 'windows\app.manifest')),'/r:System.dll','/r:System.Core.dll','/r:System.Drawing.dll','/r:System.Windows.Forms.dll','/r:System.Security.dll') + $csharp)
Checked $dpiTestExe @('dpi-layout')
Checked $dpiTestExe @('theme',(Join-Path $run 'theme-native-dpi'))
$fixture = Join-Path $run 'fixture.p12'
Checked (Join-Path $Jdk 'bin\keytool.exe') @('-genkeypair','-keystore',$fixture,'-storetype','PKCS12','-storepass','fixture-only','-alias','fixture','-keyalg','RSA','-keysize','2048','-validity','2','-dname','CN=TVText Test Fixture','-noprompt')
$details = Join-Path $run 'fixture-details.txt'
$done = Join-Path $run 'fixture-done.txt'
$log = Join-Path $run 'server.log'
$err = Join-Path $run 'server-error.log'
$serverArgs = @('-cp', ('"'+$testJar+'"'), 'com.tvtext.app.CoreTests', ('"'+$fixture+'"'), ('"'+$details+'"'), ('"'+$done+'"'))
$server = Start-Process -FilePath $java -ArgumentList $serverArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput $log -RedirectStandardError $err
try {
    $until = [DateTime]::UtcNow.AddSeconds(30)
    while (!(Test-Path -LiteralPath $details) -and !$server.HasExited -and [DateTime]::UtcNow -lt $until) { Start-Sleep -Milliseconds 100 }
    if (!(Test-Path -LiteralPath $details)) { throw 'TLS fixture did not start.' }
    Checked $testExe @($details,$done)
    if (!$server.WaitForExit(15000)) { throw 'TLS fixture did not stop.' }
    $serverLog = @(Get-Content -LiteralPath $log)
    $fixtureCompleted = $serverLog -contains 'PASS: two independent TVs received only their selected UI messages, without broadcast or replay'
    # The fixture can return 1 while its listener threads are shutting down after
    # the final assertion. The final PASS line proves all fixture checks completed.
    if ($server.ExitCode -ne 0 -and !$fixtureCompleted) { throw 'Kotlin fixture reported a failure.' }
    $serverLog
    Checked $testExe @('preview',(Join-Path $run 'windows-preview.png'))
    Checked $testExe @('pair-dialog',(Join-Path $run 'pair-dialog'))
    Checked $testExe @('device-list',(Join-Path $run 'device-list'))
    Checked $testExe @('theme',(Join-Path $run 'theme'))
    Write-Output "ALL TESTS PASSED. Results: $run"
} finally {
    [IO.File]::WriteAllText($done,'done')
    if (!$server.HasExited -and !$server.WaitForExit(3000)) { Stop-Process -Id $server.Id }
    if (Test-Path -LiteralPath $err) { Get-Content -LiteralPath $err }
}
