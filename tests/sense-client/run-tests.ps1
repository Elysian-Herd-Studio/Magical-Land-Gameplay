param(
    [Parameter(Mandatory=$true)][string]$DependencyClasspathFile,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$JdkBin = 'C:/Program Files/Microsoft/jdk-25.0.4.7-hotspot/bin',
    [string]$NodeBin = 'node'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$senseRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$senseOutput = [IO.Path]::GetFullPath($OutputDirectory)
if ($senseOutput -eq $senseRepo -or $senseOutput.StartsWith($senseRepo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Use a dedicated output directory outside the repository; never user run/config/world directories.'
}
$senseRun = Join-Path $senseOutput ('run-' + [guid]::NewGuid().ToString('N'))
$senseSessionClasses = Join-Path $senseRun 'session-classes'
$senseAbilityClasses = Join-Path $senseRun 'ability-classes'
New-Item -ItemType Directory -Path $senseSessionClasses,$senseAbilityClasses -Force | Out-Null
$senseDependencies = (Get-Content -Raw -LiteralPath $DependencyClasspathFile).Trim().Split([IO.Path]::PathSeparator)
foreach ($senseDependency in $senseDependencies) {
    if ([IO.Path]::GetExtension($senseDependency) -ne '.jar' -or -not (Test-Path -LiteralPath $senseDependency -PathType Leaf)) { throw "Missing dependency JAR: $senseDependency" }
}
$senseUtf8 = [Text.UTF8Encoding]::new($false)
function Invoke-SenseJava([string]$Tool, [string]$Name, [string[]]$JavaArguments) {
    $senseArgFile = Join-Path $senseRun ($Name + '.args')
    [IO.File]::WriteAllLines($senseArgFile, @($JavaArguments | ForEach-Object { '"' + $_.Replace('\','/').Replace('"','\"') + '"' }), $senseUtf8)
    $senseLines = @(& (Join-Path $JdkBin ($Tool + '.exe')) ('@' + $senseArgFile) 2>&1 | ForEach-Object { $_.ToString() })
    $senseCode = $LASTEXITCODE
    [IO.File]::WriteAllLines((Join-Path $senseRun ($Name + '.log')), $senseLines, $senseUtf8)
    if ($senseCode -ne 0) { $senseLines | Write-Output; throw "$Name failed; see $senseRun" }
    return $senseLines
}
$senseProtocol = Join-Path $senseRepo 'src/main/java/top/csituka/magicaland/gameplay/sense/EarthSenseProtocol.java'
$senseSession = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/client/sense/EarthSenseSession.java'
$senseAbility = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/client/AbilityClient.java'
$senseSessionTest = Join-Path $PSScriptRoot 'EarthSenseSessionTest.java'
$senseTransitionState = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/client/sense/EarthSenseTransitionState.java'
$senseTransitionTest = Join-Path $PSScriptRoot 'EarthSenseTransitionLifecycleTest.java'
$senseViewMath = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/client/sense/EarthSenseViewMath.java'
$senseViewMathTest = Join-Path $PSScriptRoot 'EarthSenseViewMathTest.java'
$senseViewHookTest = Join-Path $PSScriptRoot 'EarthSenseViewHookTest.java'
$senseFovMixin = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/mixin/client/EarthSenseFovMixin.java'
$senseMouseMixin = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/mixin/client/EarthSenseMouseMixin.java'
$senseInput = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/client/sense/EarthSenseFocusInput.java'
$senseInputTest = Join-Path $PSScriptRoot 'EarthSenseFocusInputTest.java'
$senseEnvelope = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/client/sense/EarthSenseFocusEnvelope.java'
$senseEnvelopeTest = Join-Path $PSScriptRoot 'EarthSenseFocusEnvelopeTest.java'
$senseViewerMotion = Join-Path $senseRepo 'src/main/java/top/csituka/magicaland/gameplay/sense/EarthSenseViewerMotion.java'
$senseRules = Join-Path $senseRepo 'src/main/java/top/csituka/magicaland/gameplay/sense/EarthSenseRules.java'
$senseAbilityTest = Join-Path $PSScriptRoot 'AbilityClientTest.java'
$senseWheelTest = Join-Path $PSScriptRoot 'AbilityWheelStructureTest.mjs'
$senseFocusStructureTest = Join-Path $PSScriptRoot 'EarthSenseFocusStructureTest.mjs'
$senseTransitionStructureTest = Join-Path $PSScriptRoot 'EarthSenseTransitionStructureTest.mjs'
$senseClientSource = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/client/sense/EarthSenseClient.java'
$senseRemoteSource = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/client/RemoteToolClient.java'
$senseInputMixinSource = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/mixin/client/EarthSenseInputMixin.java'
$sensePlayerInputMixinSource = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/mixin/client/EarthSensePlayerInputMixin.java'
$senseMixinRegistration = Join-Path $senseRepo 'src/client/resources/magicaland.gameplay.client.mixins.json'
$senseWheelSource = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/client/AbilityWheelScreen.java'
$senseRaceSource = Join-Path $senseRepo 'src/client/java/top/csituka/magicaland/gameplay/client/race/RaceClient.java'
$senseTextStub = Join-Path $senseRepo 'tests/config/stubs/net/minecraft/text/Text.java'
$senseAbilityStubs = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'stubs') -Recurse -Filter '*.java' -File | Sort-Object FullName | ForEach-Object FullName)
$senseSessionClasspath = (@($senseSessionClasses) + $senseDependencies) -join [IO.Path]::PathSeparator
Push-Location -LiteralPath $senseRun
try {
    Invoke-SenseJava 'javac' 'session-typecheck' @('--release','17','-proc:none','-encoding','UTF-8','-cp',$senseSessionClasspath,'-d',$senseSessionClasses,$senseProtocol,$senseSession,$senseSessionTest,$senseInput,$senseInputTest,$senseEnvelope,$senseEnvelopeTest,$senseViewerMotion,$senseRules,$senseTransitionState,$senseTransitionTest,$senseViewMath,$senseViewMathTest,$senseViewHookTest) | Write-Output
    Invoke-SenseJava 'java' 'EarthSenseSessionTest' @('-ea','-Xmx256M','-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',$senseSessionClasspath,'top.csituka.magicaland.gameplay.client.sense.EarthSenseSessionTest') | Write-Output
    Invoke-SenseJava 'java' 'EarthSenseFocusInputTest' @('-ea','-Xmx128M','-Djava.awt.headless=true','-cp',$senseSessionClasspath,'top.csituka.magicaland.gameplay.client.sense.EarthSenseFocusInputTest') | Write-Output
    Invoke-SenseJava 'java' 'EarthSenseFocusEnvelopeTest' @('-ea','-Xmx128M','-Djava.awt.headless=true','-cp',$senseSessionClasspath,'top.csituka.magicaland.gameplay.client.sense.EarthSenseFocusEnvelopeTest') | Write-Output
    Invoke-SenseJava 'java' 'EarthSenseTransitionLifecycleTest' @('-ea','-Xmx256M','-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',$senseSessionClasspath,'top.csituka.magicaland.gameplay.client.sense.EarthSenseTransitionLifecycleTest') | Write-Output
    Invoke-SenseJava 'java' 'EarthSenseViewMathTest' @('-ea','-Xmx128M','-Djava.awt.headless=true','-cp',$senseSessionClasspath,'top.csituka.magicaland.gameplay.client.sense.EarthSenseViewMathTest') | Write-Output
    Invoke-SenseJava 'java' 'EarthSenseViewHookTest' @('-ea','-Xmx128M','-Djava.awt.headless=true','-cp',$senseSessionClasspath,'top.csituka.magicaland.gameplay.client.sense.EarthSenseViewHookTest',$senseRepo) | Write-Output
    Invoke-SenseJava 'javac' 'ability-typecheck' (@('--release','17','-proc:none','-encoding','UTF-8','-d',$senseAbilityClasses,$senseAbility,$senseAbilityTest,$senseTextStub) + $senseAbilityStubs) | Write-Output
    Invoke-SenseJava 'java' 'AbilityClientTest' @('-ea','-Xmx128M','-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',$senseAbilityClasses,'top.csituka.magicaland.gameplay.client.AbilityClientTest') | Write-Output
    $senseWheelLines = @(& $NodeBin $senseWheelTest 2>&1 | ForEach-Object { $_.ToString() })
    $senseWheelCode = $LASTEXITCODE
    [IO.File]::WriteAllLines((Join-Path $senseRun 'AbilityWheelStructureTest.log'), $senseWheelLines, $senseUtf8)
    $senseWheelLines | Write-Output
    if ($senseWheelCode -ne 0) { throw "AbilityWheelStructureTest failed; see $senseRun" }
    $senseFocusLines = @(& $NodeBin $senseFocusStructureTest $DependencyClasspathFile (Join-Path $JdkBin 'javap.exe') 2>&1 | ForEach-Object { $_.ToString() })
    $senseFocusCode = $LASTEXITCODE
    [IO.File]::WriteAllLines((Join-Path $senseRun 'EarthSenseFocusStructureTest.log'), $senseFocusLines, $senseUtf8)
    $senseFocusLines | Write-Output
    if ($senseFocusCode -ne 0) { throw "EarthSenseFocusStructureTest failed; see $senseRun" }
    $senseTransitionLines = @(& $NodeBin $senseTransitionStructureTest 2>&1 | ForEach-Object { $_.ToString() })
    $senseTransitionCode = $LASTEXITCODE
    [IO.File]::WriteAllLines((Join-Path $senseRun 'EarthSenseTransitionStructureTest.log'), $senseTransitionLines, $senseUtf8)
    $senseTransitionLines | Write-Output
    if ($senseTransitionCode -ne 0) { throw "EarthSenseTransitionStructureTest failed; see $senseRun" }
    $senseHashes = @(@($senseProtocol,$senseSession,$senseAbility,$senseSessionTest,$senseAbilityTest,$senseTextStub,$senseWheelTest,$senseWheelSource,$senseRaceSource,$senseInput,$senseInputTest,$senseEnvelope,$senseEnvelopeTest,$senseViewerMotion,$senseRules,$senseFocusStructureTest,$senseTransitionStructureTest,$senseTransitionState,$senseTransitionTest,$senseClientSource,$senseRemoteSource,$senseInputMixinSource,$sensePlayerInputMixinSource,$senseMixinRegistration,$senseViewMath,$senseViewMathTest,$senseViewHookTest,$senseFovMixin,$senseMouseMixin) + $senseAbilityStubs | ForEach-Object {
        $senseHash = Get-FileHash -LiteralPath $_ -Algorithm SHA256
        [pscustomobject]@{Path=$_;SHA256=$senseHash.Hash}
    })
    [IO.File]::WriteAllText((Join-Path $senseRun 'result.json'), ([ordered]@{Passed=$true;Tests=@('EarthSenseSessionTest','EarthSenseFocusInputTest','EarthSenseFocusEnvelopeTest','EarthSenseTransitionLifecycleTest','EarthSenseViewMathTest','EarthSenseViewHookTest','AbilityClientTest','AbilityWheelStructureTest','EarthSenseFocusStructureTest','EarthSenseTransitionStructureTest');Sources=$senseHashes;RealPacketByteBuf=$true;StubAbilityClients=$true;GameLoop=$false;Gradle=$false;UserWorldsAccessed=$false} | ConvertTo-Json -Depth 5), $senseUtf8)
    Write-Output "Saved sense client verification: $senseRun"
} finally { Pop-Location }
