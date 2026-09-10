param(
    [string]$DependencyClasspathFile,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
if (!$DependencyClasspathFile -or !$OutputDirectory) { throw 'Pass -DependencyClasspathFile and -OutputDirectory' }
$audioRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$audioCp = (Get-Content -LiteralPath $DependencyClasspathFile -Raw).Trim()
$audioJava = 'C:/Program Files/Microsoft/jdk-25.0.4.7-hotspot/bin'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$audioSources = @('client/sense/EarthSenseAudio.java','client/sense/EarthSenseLowPass.java',
    'mixin/client/EarthSenseSourceMixin.java','mixin/client/EarthSenseSoundSystemMixin.java') |
    ForEach-Object { Join-Path $audioRepo ('src/client/java/top/csituka/magicaland/gameplay/' + $_) }
$audioSources += @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $audioJava 'javac.exe') --release 17 -proc:none -cp $audioCp -d $OutputDirectory $audioSources
if ($LASTEXITCODE -ne 0) { throw 'Narrow audio compilation failed' }
foreach ($audioTest in @('EarthSenseLowPassTest','EarthSenseAudioLoopbackTest','EarthSenseAudioHookTest')) {
    & (Join-Path $audioJava 'java.exe') -cp ($OutputDirectory + ';' + $audioCp) ('top.csituka.magicaland.gameplay.client.sense.' + $audioTest) $audioRepo
    if ($LASTEXITCODE -ne 0) { throw "Audio test failed: $audioTest" }
}
