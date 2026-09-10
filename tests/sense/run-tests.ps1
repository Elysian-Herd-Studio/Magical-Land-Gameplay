param(
    [Parameter(Mandatory=$true)][string]$DependencyClasspathFile,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$AppearanceApiJar,
    [string]$JdkBin = 'C:/Program Files/Microsoft/jdk-25.0.4.7-hotspot/bin'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$senseRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$senseOutput = [IO.Path]::GetFullPath($OutputDirectory)
if ($senseOutput -eq $senseRepo -or $senseOutput.StartsWith($senseRepo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Use a dedicated output directory outside the repository.' }
$senseRun = Join-Path $senseOutput ('run-' + [guid]::NewGuid().ToString('N'))
$senseClasses = Join-Path $senseRun 'classes'
New-Item -ItemType Directory -Path $senseClasses -Force | Out-Null
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
$senseSource = Join-Path $senseRepo 'src/main/java/top/csituka/magicaland/gameplay/sense'
$senseProduction = @('EarthSenseRules','EarthSenseLease','EarthSenseGround','EarthSenseProtocol','EarthSenseFocus','EarthSenseSignals','EarthSenseViewerMotion') | ForEach-Object { Join-Path $senseSource ($_.ToString() + '.java') }
$senseTests = @('EarthSenseRulesTest','EarthSenseLeaseTest','EarthSenseGroundTest','EarthSenseProtocolTest','EarthSenseFocusTest','EarthSenseSignalsTest','EarthSenseViewerMotionTest','EarthSenseDamageApiTest','EarthSenseIntegrationTest') | ForEach-Object { Get-Item -LiteralPath (Join-Path $PSScriptRoot ($_.ToString() + '.java')) }
$senseCp = (@($senseClasses) + $senseDependencies) -join [IO.Path]::PathSeparator
Push-Location -LiteralPath $senseRun
try {
    Invoke-SenseJava 'javac' 'sense-core-typecheck' (@('--release','17','-proc:none','-encoding','UTF-8','-cp',$senseCp,'-d',$senseClasses) + $senseProduction + @($senseTests.FullName)) | Write-Output
    foreach ($senseTest in $senseTests) {
        Invoke-SenseJava 'java' $senseTest.BaseName @('-ea','-Xmx512M','-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',$senseCp,('top.csituka.magicaland.gameplay.sense.' + $senseTest.BaseName),$senseRun,$senseRepo) | Write-Output
    }
    if ($AppearanceApiJar) {
        $senseApi = (Resolve-Path -LiteralPath $AppearanceApiJar).Path
        $senseApiEntries = Invoke-SenseJava 'jar' 'api-public-entries' @('tf',$senseApi)
        if (@($senseApiEntries | Where-Object { $_.EndsWith('.class') -and -not $_.StartsWith('top/csituka/magicaland/api/') }).Count) { throw 'Only public Appearance API classes allowed in the API JAR' }
        $senseAllClasses = Join-Path $senseRun 'all-gameplay-classes'
        New-Item -ItemType Directory -Path $senseAllClasses | Out-Null
        $senseAllSources = @(Get-ChildItem -LiteralPath (Join-Path $senseRepo 'src/main/java'),(Join-Path $senseRepo 'src/client/java') -Recurse -Filter '*.java' -File | Sort-Object FullName | ForEach-Object FullName)
        Invoke-SenseJava 'javac' 'gameplay-api-only-typecheck' (@('--release','17','-proc:none','-implicit:none','-encoding','UTF-8','-cp',((@($senseApi)+$senseDependencies)-join ';'),'-d',$senseAllClasses)+$senseAllSources) | Write-Output
        Write-Output "PASS API-only Gameplay typecheck: $($senseAllSources.Count) sources"
    }
    $senseHashes = @(Get-ChildItem -LiteralPath $senseSource -Filter '*.java' -File | ForEach-Object FullName) + @($senseTests.FullName) | ForEach-Object { $senseHash=Get-FileHash -LiteralPath $_ -Algorithm SHA256; [pscustomobject]@{Path=$_;SHA256=$senseHash.Hash} }
    [IO.File]::WriteAllText((Join-Path $senseRun 'result.json'), ([ordered]@{Passed=$true;Tests=$senseTests.BaseName;Sources=@($senseHashes);GameLoop=$false;Gradle=$false;UserWorldsAccessed=$false} | ConvertTo-Json -Depth 5), $senseUtf8)
    Write-Output "Saved sense verification: $senseRun"
} finally { Pop-Location }
