param(
    [Parameter(Mandatory)][string]$GsonJar,
    [Parameter(Mandatory)][string]$ModMenuJar,
    [string]$JavaBin='',
    [string]$OutputDirectory=(Join-Path ([IO.Path]::GetTempPath()) ('magicaland-gameplay-config-'+[guid]::NewGuid().ToString('N')))
)
$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$false
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$GsonJar=(Resolve-Path -LiteralPath $GsonJar).Path
$ModMenuJar=(Resolve-Path -LiteralPath $ModMenuJar).Path
if(Test-Path -LiteralPath $OutputDirectory){throw 'Use a new test output directory.'}
$output=(New-Item -ItemType Directory -Path $OutputDirectory).FullName
$sources=@(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'stubs') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
$sources+=Join-Path $PSScriptRoot 'GameplaySettingsTest.java'
$sources+=Join-Path $PSScriptRoot 'GameplaySenseFilterTest.java'
$sources+=Join-Path $PSScriptRoot 'RaceScreensTest.java'
foreach($file in @('GameplaySettingsScreen.java','EarthSenseSettingsScreen.java','ModMenuIntegration.java')){
    $sources+=Join-Path $repo ('src/client/java/top/csituka/magicaland/gameplay/client/'+$file)
}
foreach($file in @('RaceSelectionScreen.java','RaceRulesScreen.java','RaceRulesDraft.java')){
    $sources+=Join-Path $repo ('src/client/java/top/csituka/magicaland/gameplay/client/race/'+$file)
}
foreach($file in @('RaceDefinition.java','RaceDefinitions.java','RaceRules.java')){
    $sources+=Join-Path $repo ('src/main/java/top/csituka/magicaland/gameplay/race/'+$file)
}
foreach($file in @('config/GameplayClientConfig.java','MagicalLandGameplay.java')){
    $sources+=Join-Path $repo ('src/main/java/top/csituka/magicaland/gameplay/'+$file)
}
$javac=if($JavaBin){Join-Path $JavaBin 'javac'}else{'javac'}
$java=if($JavaBin){Join-Path $JavaBin 'java'}else{'java'}
$classPath=$GsonJar+[IO.Path]::PathSeparator+$ModMenuJar
& $javac --release 17 -proc:none -encoding UTF-8 -cp $classPath -d $output @sources
if($LASTEXITCODE -ne 0){throw 'Gameplay settings fixture compilation failed'}
& $java -ea -cp ($output+[IO.Path]::PathSeparator+$classPath) GameplaySettingsTest $repo $output 2>&1 | Tee-Object (Join-Path $output 'config-test.log')
if($LASTEXITCODE -ne 0){throw 'Gameplay settings regression failed'}
& $java -ea -cp ($output+[IO.Path]::PathSeparator+$classPath) GameplaySenseFilterTest $repo $output
if($LASTEXITCODE -ne 0){throw 'Sense filter settings regression failed'}
& $java -ea -cp ($output+[IO.Path]::PathSeparator+$classPath) RaceScreensTest $output
if($LASTEXITCODE -ne 0){throw 'Race screen regression failed'}
