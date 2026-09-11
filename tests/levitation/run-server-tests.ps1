param(
    [Parameter(Mandatory=$true)][string]$DependencyClasspathFile,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$JdkBin = ''
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
if (-not $JdkBin) { $JdkBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac -CommandType Application -ErrorAction Stop).Source } }
$levitationExecutableSuffix = if ([IO.Path]::DirectorySeparatorChar -eq '\') { '.exe' } else { '' }
$levitationRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$levitationOutput = [IO.Path]::GetFullPath($OutputDirectory)
if ($levitationOutput -eq $levitationRepo -or $levitationOutput.StartsWith($levitationRepo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Use isolated output outside the repository.' }
$levitationRun = Join-Path $levitationOutput ('run-' + [guid]::NewGuid().ToString('N'))
$levitationClasses = Join-Path $levitationRun 'classes'
New-Item -ItemType Directory -Path $levitationClasses -Force | Out-Null
$levitationDependencies = (Get-Content -Raw -LiteralPath $DependencyClasspathFile).Trim().Split([IO.Path]::PathSeparator)
foreach ($levitationDependency in $levitationDependencies) { if (-not (Test-Path -LiteralPath $levitationDependency -PathType Leaf)) { throw "Missing dependency: $levitationDependency" } }
$levitationUtf8 = [Text.UTF8Encoding]::new($false)
function Invoke-LevitationJava([string]$Tool, [string]$Name, [string[]]$JavaArguments) {
    $levitationArgs = Join-Path $levitationRun ($Name + '.args')
    [IO.File]::WriteAllLines($levitationArgs, @($JavaArguments | ForEach-Object { '"' + $_.Replace('\','/').Replace('"','\"') + '"' }), $levitationUtf8)
    $levitationLines = @(& (Join-Path $JdkBin ($Tool + $levitationExecutableSuffix)) ('@' + $levitationArgs) 2>&1 | ForEach-Object { $_.ToString() })
    $levitationCode = $LASTEXITCODE
    [IO.File]::WriteAllLines((Join-Path $levitationRun ($Name + '.log')), $levitationLines, $levitationUtf8)
    if ($levitationCode -ne 0) { $levitationLines | Write-Output; throw "$Name failed; see $levitationRun" }
    return $levitationLines
}
$levitationSource = Join-Path $levitationRepo 'src/main/java/top/csituka/magicaland/gameplay'
$levitationProduction = @('levitation/UnicornLevitationMath','levitation/UnicornLevitationRules','levitation/UnicornLevitationBudget','levitation/UnicornLevitationProtocol','race/RaceDefinition','race/RaceDefinitions','race/RaceRules','race/RaceState') | ForEach-Object { Join-Path $levitationSource ($_.ToString() + '.java') }
$levitationTests = @('UnicornLevitationRulesTest','UnicornLevitationBudgetTest','UnicornLevitationProtocolTest','UnicornLevitationPersistenceTest','UnicornLevitationIntegrationTest','UnicornLevitationPacketOrderTest') | ForEach-Object { Get-Item -LiteralPath (Join-Path $PSScriptRoot ($_.ToString() + '.java')) }
$levitationCp = (@($levitationClasses) + $levitationDependencies) -join [IO.Path]::PathSeparator
Push-Location -LiteralPath $levitationRun
try {
    Invoke-LevitationJava 'javac' 'server-core-typecheck' (@('--release','17','-proc:none','-encoding','UTF-8','-cp',$levitationCp,'-d',$levitationClasses) + $levitationProduction + @($levitationTests.FullName)) | Write-Output
    foreach ($levitationTest in $levitationTests) {
        Invoke-LevitationJava 'java' $levitationTest.BaseName @('-ea','-Xmx512M','-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',$levitationCp,('top.csituka.magicaland.gameplay.levitation.' + $levitationTest.BaseName),$levitationRun,$levitationRepo) | Write-Output
    }
    $levitationHashes = @($levitationProduction) + @($levitationTests.FullName) | ForEach-Object { $levitationHash = Get-FileHash -LiteralPath $_ -Algorithm SHA256; [pscustomobject]@{Path=$_;SHA256=$levitationHash.Hash} }
    [IO.File]::WriteAllText((Join-Path $levitationRun 'result.json'), ([ordered]@{Passed=$true;Tests=$levitationTests.BaseName;Sources=@($levitationHashes);GameLoop=$false;Gradle=$false;UserWorldsAccessed=$false} | ConvertTo-Json -Depth 5), $levitationUtf8)
    Write-Output "Saved levitation server verification: $levitationRun"
} finally { Pop-Location }
