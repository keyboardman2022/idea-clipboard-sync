$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
[xml]$taskDescriptor = Get-Content -LiteralPath (Join-Path $taskRoot 'src\main\resources\META-INF\plugin.xml') -Raw -Encoding UTF8
$taskVersion = $taskDescriptor.'idea-plugin'.version
$taskInput = Join-Path $taskRoot "build\distributions\clipboard-install-$taskVersion"
$taskTemp = Join-Path $taskRoot ('build\installer-smoke-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $taskTemp | Out-Null
$taskSavedTemp = $env:TEMP
try {
    $env:TEMP = $taskTemp
    & ([ScriptBlock]::Create([IO.File]::ReadAllText((Join-Path $taskInput 'vm-install-all.txt'))))
    $taskOriginal = (Get-FileHash (Join-Path $taskRoot "build\distributions\idea-clipboard-sync-$taskVersion.zip")).Hash
    $taskOutput = Join-Path $taskTemp "idea-clipboard-sync-$taskVersion.zip"
    if ((Get-FileHash -LiteralPath $taskOutput).Hash -ne $taskOriginal) { throw 'Single clipboard installer round trip failed' }
    Remove-Item -LiteralPath $taskOutput -Force
    $taskParts = @(Get-ChildItem -LiteralPath $taskInput -Filter 'part-*.txt' | Sort-Object Name -Descending)
    foreach ($taskPart in $taskParts) { & ([ScriptBlock]::Create([IO.File]::ReadAllText($taskPart.FullName))) }
    & ([ScriptBlock]::Create([IO.File]::ReadAllText($taskParts[0].FullName)))
    & ([ScriptBlock]::Create([IO.File]::ReadAllText((Join-Path $taskInput 'finish.txt'))))
    if ((Get-FileHash -LiteralPath $taskOutput).Hash -ne $taskOriginal) { throw 'Chunked clipboard installer round trip failed' }
    Write-Output 'PASS: single and chunked clipboard installers; reverse order and duplicate part; SHA-256 identical.'
} finally { $env:TEMP = $taskSavedTemp }
