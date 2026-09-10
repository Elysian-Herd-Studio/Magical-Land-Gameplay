param([string]$DependencyClasspathFile, [string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
if (!$DependencyClasspathFile -or !$OutputDirectory) { throw 'Pass -DependencyClasspathFile and -OutputDirectory' }
$transitionRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$transitionCp = (Get-Content -LiteralPath $DependencyClasspathFile -Raw).Trim()
$transitionJava = 'C:/Program Files/Microsoft/jdk-25.0.4.7-hotspot/bin'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$transitionSources = @('client/sense/EarthSenseTransitionState.java','client/sense/EarthSenseTransitionSound.java',
    'client/sense/EarthSenseTransitionSources.java','mixin/client/EarthSenseTransitionSoundSystemMixin.java',
    'mixin/client/EarthSenseTransitionSourceMixin.java') |
    ForEach-Object { Join-Path $transitionRepo ('src/client/java/top/csituka/magicaland/gameplay/' + $_) }
$transitionSources += @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $transitionJava 'javac.exe') --release 17 -proc:none -cp $transitionCp -d $OutputDirectory $transitionSources
if ($LASTEXITCODE -ne 0) { throw 'Narrow transition compilation failed' }
foreach ($transitionTest in @('EarthSenseTransitionTest','EarthSenseTransitionNativeTest')) {
    & (Join-Path $transitionJava 'java.exe') -cp ($OutputDirectory + ';' + $transitionCp) ('top.csituka.magicaland.gameplay.client.sense.' + $transitionTest) $transitionRepo
    if ($LASTEXITCODE -ne 0) { throw "Transition test failed: $transitionTest" }
}
