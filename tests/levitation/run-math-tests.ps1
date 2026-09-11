param([Parameter(Mandatory=$true)][string]$DependencyClasspathFile,[Parameter(Mandatory=$true)][string]$OutputDirectory,[string]$JdkBin='')
$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$false
if (-not $JdkBin) { $JdkBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac -CommandType Application -ErrorAction Stop).Source } }
$levExecutableSuffix = if ([IO.Path]::DirectorySeparatorChar -eq '\') { '.exe' } else { '' }
$levRepo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$levOutput=[IO.Path]::GetFullPath($OutputDirectory)
if($levOutput -eq $levRepo -or $levOutput.StartsWith($levRepo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Use output outside repository.'}
$levRun=Join-Path $levOutput ('run-'+[guid]::NewGuid().ToString('N'))
$levClasses=Join-Path $levRun 'classes'
New-Item -ItemType Directory -Path $levClasses -Force | Out-Null
$levCp=(Get-Content -Raw -LiteralPath $DependencyClasspathFile).Trim()
$levSources=@('UnicornLevitationMath','UnicornLevitationGround') | ForEach-Object {Join-Path $levRepo ('src/main/java/top/csituka/magicaland/gameplay/levitation/'+$_+'.java')}
$levSources+=Join-Path $PSScriptRoot 'UnicornLevitationPhysicsTest.java'
$levUtf8=[Text.UTF8Encoding]::new($false)
function Invoke-LevMath([string]$Tool,[string]$Name,[string[]]$Arguments){
 $levArgs=Join-Path $levRun ($Name+'.args')
 [IO.File]::WriteAllLines($levArgs,@($Arguments | ForEach-Object {'"'+$_.Replace('\','/').Replace('"','\"')+'"'}),$levUtf8)
 $levLines=@(& (Join-Path $JdkBin ($Tool+$levExecutableSuffix)) ('@'+$levArgs) 2>&1 | ForEach-Object {$_.ToString()});$levExit=$LASTEXITCODE
 [IO.File]::WriteAllLines((Join-Path $levRun ($Name+'.log')),$levLines,$levUtf8);$levLines | Write-Output
 if($levExit -ne 0){throw "$Name failed: $levRun"}
}
Push-Location -LiteralPath $levRun
try{
 Invoke-LevMath 'javac' 'typecheck' (@('--release','17','-proc:none','-implicit:none','-encoding','UTF-8','-cp',$levCp,'-d',$levClasses)+$levSources)
 Add-Type -AssemblyName System.IO.Compression.FileSystem
 $levRuntime=@($levCp.Split([IO.Path]::PathSeparator))
 $levGroups=@($levRuntime | Group-Object {([IO.Path]::GetFileName($_) -replace '^\d+-','') -replace '-[a-f0-9]{8}-(client|common)-','-'} | Where-Object Count -gt 1 | ForEach-Object {$_.Group -join [IO.Path]::PathSeparator})
 $levHelper=Join-Path $levRun 'helper';New-Item -ItemType Directory -Path $levHelper | Out-Null
 Invoke-LevMath 'javac' 'knot-helper' @('--release','17','-proc:none','-cp',$levCp,'-d',$levHelper,(Join-Path $PSScriptRoot 'LevitationKnotBootstrap.java'))
 $levTestJar=Join-Path $levRun 'levitation-tests.jar';[IO.Compression.ZipFile]::CreateFromDirectory($levClasses,$levTestJar)
 $levHelperJar=Join-Path $levRun 'knot-helper.jar';[IO.Compression.ZipFile]::CreateFromDirectory($levHelper,$levHelperJar)
 $levCommon=@($levRuntime | Where-Object {[IO.Path]::GetFileName($_) -match 'minecraft-common-'})
 $levClient=@($levRuntime | Where-Object {[IO.Path]::GetFileName($_) -match 'minecraft-client-'})
 if($levCommon.Count -ne 1 -or $levClient.Count -ne 1){throw 'Expected one named common and client Minecraft JAR.'}
 Invoke-LevMath 'java' 'UnicornLevitationPhysicsTest' @('-ea','-Xmx512M','-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-Dfabric.development=true',('-Dfabric.remapClasspathFile='+[IO.Path]::GetFullPath($DependencyClasspathFile)),('-Dfabric.classPathGroups='+($levGroups -join ';;')),('-Dfabric.gameJarPath='+$levCommon[0]),('-Dfabric.gameJarPath.client='+$levClient[0]),'-cp',((@($levHelperJar,$levTestJar)+$levRuntime)-join [IO.Path]::PathSeparator),'LevitationKnotBootstrap')
 [IO.File]::WriteAllText((Join-Path $levRun 'result.json'),([ordered]@{Passed=$true;RealMinecraftGeometry=$true;ClassLoader='Fabric Knot, dependency transformations only';Sources=@($levSources | ForEach-Object {Get-FileHash -LiteralPath $_ -Algorithm SHA256});Gradle=$false;GameLoop=$false}|ConvertTo-Json -Depth 5),$levUtf8)
 Write-Output "Saved levitation math/ground verification: $levRun"
}finally{Pop-Location}
