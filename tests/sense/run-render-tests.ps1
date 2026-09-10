param(
    [Parameter(Mandatory = $true)][string]$ClasspathFile,
    [string]$JavaBin = "",
    [string]$OutputDirectory = ""
)
$ErrorActionPreference = "Stop"
$senseRepo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "../..")).Path
$senseDependencies = (Get-Content -LiteralPath $ClasspathFile -Raw).Trim().Split(';')
$senseMinecraft = $senseDependencies | Where-Object { $_ -match 'minecraft-client' } | Select-Object -First 1
if (-not $senseMinecraft) { throw "Provide the cached named Minecraft 1.20.1 client/Fabric/LWJGL dependency classpath; this runner never downloads or runs Gradle." }
if (-not $JavaBin) { $JavaBin = Split-Path -Parent (Get-Command javac).Source }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("magicaland-sense-render-" + [guid]::NewGuid()) }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$senseClasses = Join-Path $OutputDirectory "classes"
New-Item -ItemType Directory -Path $senseClasses -Force | Out-Null
$senseSource = Join-Path $senseRepo "src/client/java/top/csituka/magicaland/gameplay/client/sense"
$senseTestNames = @("EarthSenseVisualMathTest", "EarthSenseFilterGpuTest", "EarthSenseRenderStructureTest")
$senseSources = @((Join-Path $senseSource "EarthSenseVisualMath.java"), (Join-Path $senseSource "EarthSenseFilter.java"))
$senseSources += $senseTestNames | ForEach-Object { Join-Path $PSScriptRoot ($_ + ".java") }
function Invoke-SenseJava([string]$Tool, [string]$Name, [string[]]$Arguments) {
    $senseArgumentFile = Join-Path $OutputDirectory ($Name + ".args")
    $senseQuoted = $Arguments | ForEach-Object { '"' + $_.Replace('\', '/').Replace('"', '\"') + '"' }
    [System.IO.File]::WriteAllLines($senseArgumentFile, $senseQuoted, [System.Text.UTF8Encoding]::new($false))
    & (Join-Path $JavaBin ($Tool + ".exe")) ("@" + $senseArgumentFile)
    if ($LASTEXITCODE -ne 0) { throw "Earth sense $Name failed" }
}
Invoke-SenseJava "javac" "compile" (@("--release", "17", "-proc:none", "-encoding", "UTF-8", "-cp", ($senseDependencies -join ';'), "-d", $senseClasses) + $senseSources)
$senseRuntime = @($senseClasses) + @($senseDependencies | Where-Object { $_ -notmatch 'natives-windows-(arm64|x86)\.' })
$senseClasspath = $senseRuntime -join ';'
Invoke-SenseJava "java" "math" @("-cp", $senseClasspath, "top.csituka.magicaland.gameplay.client.sense.EarthSenseVisualMathTest")
# GLFW creates only a hidden offscreen context; no Minecraft client or server is launched.
Invoke-SenseJava "java" "gpu" @("-cp", $senseClasspath, "top.csituka.magicaland.gameplay.client.sense.EarthSenseFilterGpuTest", (Join-Path $senseRepo "src/client/resources/assets/magicaland_gameplay/shaders"))
Invoke-SenseJava "java" "gpu-native" @("-cp", $senseClasspath, "top.csituka.magicaland.gameplay.client.sense.EarthSenseFilterGpuTest", (Join-Path $senseRepo "src/client/resources/assets/magicaland_gameplay/shaders"), "native")
Invoke-SenseJava "java" "structure" @("-cp", $senseClasspath, "top.csituka.magicaland.gameplay.client.sense.EarthSenseRenderStructureTest", $senseMinecraft, $senseRepo)
Write-Output "PASS isolated rendering tests; core sensor/session tests have a separate runner. Output: $OutputDirectory"
