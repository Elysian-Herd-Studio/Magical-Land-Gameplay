param(
    [Parameter(Mandatory=$true)][string]$DependencyClasspathFile,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$AppearanceApiJar,
    [string]$JdkBin = 'C:/Program Files/Microsoft/jdk-25.0.4.7-hotspot/bin'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$raceRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$raceOutput = [IO.Path]::GetFullPath($OutputDirectory)
if ($raceOutput -eq $raceRepo -or $raceOutput.StartsWith($raceRepo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Use a dedicated output directory outside the repository; no user run/config/world directories.'
}
$raceRun = Join-Path $raceOutput ('run-' + [guid]::NewGuid().ToString('N'))
$raceClasses = Join-Path $raceRun 'classes'
New-Item -ItemType Directory -Path $raceClasses -Force | Out-Null
$raceDependencies = (Get-Content -Raw -LiteralPath $DependencyClasspathFile).Trim().Split([IO.Path]::PathSeparator)
foreach ($raceDependency in $raceDependencies) {
    if ([IO.Path]::GetExtension($raceDependency) -ne '.jar' -or -not (Test-Path -LiteralPath $raceDependency -PathType Leaf)) { throw "Missing dependency JAR: $raceDependency" }
}
$raceUtf8 = [Text.UTF8Encoding]::new($false)
function Invoke-RaceJava([string]$Tool, [string]$Name, [string[]]$JavaArguments) {
    $raceArgFile = Join-Path $raceRun ($Name + '.args')
    [IO.File]::WriteAllLines($raceArgFile, @($JavaArguments | ForEach-Object { '"' + $_.Replace('\','/').Replace('"','\"') + '"' }), $raceUtf8)
    $raceLines = @(& (Join-Path $JdkBin ($Tool + '.exe')) ('@' + $raceArgFile) 2>&1 | ForEach-Object { $_.ToString() })
    $raceCode = $LASTEXITCODE
    [IO.File]::WriteAllLines((Join-Path $raceRun ($Name + '.log')), $raceLines, $raceUtf8)
    if ($raceCode -ne 0) { $raceLines | Write-Output; throw "$Name failed; see $raceRun" }
    return $raceLines
}
$raceSource = Join-Path $raceRepo 'src/main/java/top/csituka/magicaland/gameplay/race'
$raceProduction = @('RaceDefinition','RaceDefinitions','RaceRules','RacePolicy','RaceProtocol','RaceState') | ForEach-Object { Join-Path $raceSource ($_.ToString() + '.java') }
$raceTests = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' -File | Sort-Object Name)
$raceCp = (@($raceClasses) + $raceDependencies) -join [IO.Path]::PathSeparator
Push-Location -LiteralPath $raceRun
try {
    Invoke-RaceJava 'javac' 'race-typecheck' (@('--release','17','-proc:none','-encoding','UTF-8','-cp',$raceCp,'-d',$raceClasses) + $raceProduction + @($raceTests.FullName)) | Write-Output
    foreach ($raceTest in $raceTests) {
        Invoke-RaceJava 'java' $raceTest.BaseName @('-ea','-Xmx512M','-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',$raceCp,('top.csituka.magicaland.gameplay.race.' + $raceTest.BaseName),$raceRun,$raceRepo) | Write-Output
    }
    if ($AppearanceApiJar) {
        $raceApi = (Resolve-Path -LiteralPath $AppearanceApiJar).Path
        $raceApiEntries = Invoke-RaceJava 'jar' 'api-public-entries' @('tf',$raceApi)
        if (@($raceApiEntries | Where-Object { $_.EndsWith('.class') -and -not $_.StartsWith('top/csituka/magicaland/api/') }).Count) { throw 'Only public Appearance API classes allowed in the API JAR' }
        $raceAllClasses = Join-Path $raceRun 'all-gameplay-classes'
        New-Item -ItemType Directory -Path $raceAllClasses | Out-Null
        $raceAllSources = @(Get-ChildItem -LiteralPath (Join-Path $raceRepo 'src/main/java'),(Join-Path $raceRepo 'src/client/java') -Recurse -Filter '*.java' -File | Sort-Object FullName | ForEach-Object FullName)
        Invoke-RaceJava 'javac' 'gameplay-api-only-typecheck' (@('--release','17','-proc:none','-implicit:none','-encoding','UTF-8','-cp',((@($raceApi)+$raceDependencies)-join ';'),'-d',$raceAllClasses)+$raceAllSources) | Write-Output
        Write-Output "PASS API-only Gameplay typecheck: $($raceAllSources.Count) sources"
    }
    $raceHashes = @($raceProduction + @($raceTests.FullName) | ForEach-Object { $raceHash=Get-FileHash -LiteralPath $_ -Algorithm SHA256; [pscustomobject]@{Path=$_;SHA256=$raceHash.Hash} })
    [IO.File]::WriteAllText((Join-Path $raceRun 'result.json'), ([ordered]@{Passed=$true;Tests=$raceTests.BaseName;Sources=$raceHashes;GameLoop=$false;Gradle=$false;UserWorldsAccessed=$false} | ConvertTo-Json -Depth 5), $raceUtf8)
    Write-Output "Saved race verification: $raceRun"
} finally { Pop-Location }
