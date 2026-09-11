param(
    [Parameter(Mandatory=$true)][string]$Dependencies,
    [Parameter(Mandatory=$true)][string]$OutputRoot,
    [string]$JavaBin = '',
    [string]$Node = 'node'
)
$ErrorActionPreference='Stop'
if (-not $JavaBin -and $env:JAVA_HOME) { $JavaBin = Join-Path $env:JAVA_HOME 'bin' }
$levitationExecutableSuffix = if ([IO.Path]::DirectorySeparatorChar -eq '\') { '.exe' } else { '' }
$levitationJavac = if ($JavaBin) { Join-Path $JavaBin ('javac' + $levitationExecutableSuffix) } else { 'javac' }
$levitationJava = if ($JavaBin) { Join-Path $JavaBin ('java' + $levitationExecutableSuffix) } else { 'java' }
$levitationRepo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$levitationRun = Join-Path $OutputRoot ('run-'+[guid]::NewGuid().ToString('N'))
$levitationClasses = Join-Path $levitationRun 'classes'
New-Item -ItemType Directory -Path $levitationClasses -Force | Out-Null
$levitationCp=(Get-Content -LiteralPath $Dependencies -Raw).Trim()
$levitationSources=@(
 'src/main/java/top/csituka/magicaland/gameplay/levitation/UnicornLevitationMath.java',
 'src/main/java/top/csituka/magicaland/gameplay/levitation/UnicornLevitationProtocol.java',
 'src/client/java/top/csituka/magicaland/gameplay/client/levitation/UnicornLevitationInput.java',
 'src/client/java/top/csituka/magicaland/gameplay/client/levitation/UnicornLevitationNativeInput.java',
 'src/client/java/top/csituka/magicaland/gameplay/client/levitation/UnicornLevitationSession.java',
 'tests/levitation-client/UnicornLevitationSessionTest.java',
 'tests/levitation-client/UnicornLevitationNativeInputTest.java',
 'tests/levitation-client/native-stubs/net/minecraft/client/option/GameOptions.java',
 'tests/levitation-client/native-stubs/net/minecraft/client/option/KeyBinding.java'
) | ForEach-Object { Join-Path $levitationRepo $_ }
$levitationArgs=@('--release','17','-proc:none','-encoding','UTF-8','-cp',$levitationCp,'-d',$levitationClasses)+$levitationSources
$levitationArgFile=Join-Path $levitationRun 'javac.args'
$levitationArgs | ForEach-Object {'"'+$_.Replace('\','/')+'"'} | Set-Content -LiteralPath $levitationArgFile -Encoding utf8
& $levitationJavac "@$levitationArgFile" 2>&1 | Tee-Object -FilePath (Join-Path $levitationRun 'javac.log')
if($LASTEXITCODE -ne 0){throw 'Levitation client targeted Java 17 compile failed'}
foreach($levitationTest in @('UnicornLevitationSessionTest','UnicornLevitationNativeInputTest')) {
 $levitationRunArgs=@('-ea','-Xmx128M','-Djava.awt.headless=true','-cp',"$levitationClasses;$levitationCp",("top.csituka.magicaland.gameplay.client.levitation."+$levitationTest))
 $levitationJavaArgs=Join-Path $levitationRun ($levitationTest+'.args')
 $levitationRunArgs | ForEach-Object {'"'+$_.Replace('\','/')+'"'} | Set-Content -LiteralPath $levitationJavaArgs -Encoding utf8
 & $levitationJava "@$levitationJavaArgs" 2>&1 | Tee-Object -FilePath (Join-Path $levitationRun ($levitationTest+'.log'))
 if($LASTEXITCODE -ne 0){throw "$levitationTest regression failed"}
}
& $Node (Join-Path $PSScriptRoot 'LevitationClientStructureTest.mjs') 2>&1 | Tee-Object -FilePath (Join-Path $levitationRun 'structure.log')
if($LASTEXITCODE -ne 0){throw 'Levitation structure regression failed'}
Write-Output "Output: $levitationRun"
