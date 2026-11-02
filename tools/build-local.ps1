param(
    [string]$JavaHome = 'C:\Users\topto\.jdks\graalvm-jdk-21.0.8',
    [string]$IdeHome = '',
    [switch]$CompileOnly
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
if (-not $IdeHome) { $IdeHome = Join-Path $taskRoot '.tools\sdk-2025.1.2' }
$taskLib = if (Test-Path (Join-Path $IdeHome 'lib')) { Join-Path $IdeHome 'lib' } else { $IdeHome }
if (-not (Test-Path (Join-Path $taskLib 'app.jar'))) { throw 'IDEA SDK app.jar is missing. Run fetch-sdk-api.py or specify -IdeHome.' }
$taskBuild = Join-Path $taskRoot 'build'
$taskClasses = Join-Path $taskBuild ('classes-' + [guid]::NewGuid().ToString('N'))
$taskPlugin = Join-Path $taskBuild 'staging\idea-clipboard-sync\lib'
$taskDist = Join-Path $taskBuild 'distributions'
New-Item -ItemType Directory -Force -Path $taskClasses,$taskPlugin,$taskDist | Out-Null
$taskSources = @(Get-ChildItem (Join-Path $taskRoot 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $JavaHome 'bin\javac.exe') '-J-Duser.language=en' -encoding UTF-8 -source 21 -target 21 -cp "$taskLib\*" -d $taskClasses @taskSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($CompileOnly) { Write-Output "Compilation passed against SDK: $IdeHome"; exit 0 }
$taskJar = Join-Path $taskPlugin 'idea-clipboard-sync.jar'
& (Join-Path $JavaHome 'bin\jar.exe') --create --file $taskJar -C $taskClasses . -C (Join-Path $taskRoot 'src\main\resources') .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
[xml]$taskDescriptor = Get-Content -LiteralPath (Join-Path $taskRoot 'src\main\resources\META-INF\plugin.xml') -Raw -Encoding UTF8
$taskVersion = $taskDescriptor.'idea-plugin'.version
$taskZip = Join-Path $taskDist "idea-clipboard-sync-$taskVersion.zip"
Compress-Archive -LiteralPath (Split-Path $taskPlugin -Parent) -DestinationPath $taskZip -Force
Write-Output "SDK: $IdeHome"
Write-Output "Plugin ZIP: $taskZip"
Get-FileHash -LiteralPath $taskZip -Algorithm SHA256 | Format-List
