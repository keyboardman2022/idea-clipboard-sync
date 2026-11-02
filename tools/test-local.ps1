param([string]$JavaHome = 'C:\Users\topto\.jdks\graalvm-jdk-21.0.8')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskOut = Join-Path $taskRoot 'build\local-test'
New-Item -ItemType Directory -Force -Path $taskOut | Out-Null
$taskRunner = Join-Path $taskRoot '.tools\junit-console.jar'
$taskSources = @(Get-ChildItem (Join-Path $taskRoot 'src\main\java\dev\clipboard\sync\core') -Filter '*.java' -Recurse -ErrorAction SilentlyContinue | ForEach-Object FullName)
$taskSources += @(Get-ChildItem (Join-Path $taskRoot 'src\test\java') -Filter '*.java' -Recurse | ForEach-Object FullName)
& (Join-Path $JavaHome 'bin\javac.exe') '-J-Duser.language=en' -encoding UTF-8 -source 21 -target 21 -cp $taskRunner -d $taskOut @taskSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $JavaHome 'bin\java.exe') -jar $taskRunner execute --class-path $taskOut --scan-class-path --details summary --disable-banner
exit $LASTEXITCODE
