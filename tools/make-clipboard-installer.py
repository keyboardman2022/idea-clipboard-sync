"""Create text-only Windows VM bootstrap installers for the final plugin ZIP."""
from pathlib import Path
import base64
import hashlib
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
version = ET.parse(root / 'src/main/resources/META-INF/plugin.xml').findtext('version')
archive = root / f'build/distributions/idea-clipboard-sync-{version}.zip'
data = archive.read_bytes()
checksum = hashlib.sha256(data).hexdigest().upper()
payload = base64.b64encode(data).decode('ascii')
output = archive.parent / f'clipboard-install-{version}'
output.mkdir(exist_ok=True)
final = f"$taskZipPath = Join-Path $env:TEMP 'idea-clipboard-sync-{version}.zip'\n" + "[IO.File]::WriteAllBytes($taskZipPath, [Convert]::FromBase64String($taskPayload))\n" + f"if ((Get-FileHash -LiteralPath $taskZipPath -Algorithm SHA256).Hash -ne '{checksum}') {{ throw 'Plugin checksum mismatch. Recopy the data.' }}\n" + "Write-Host ('Plugin ZIP ready: ' + $taskZipPath)\n"
(output / 'vm-install-all.txt').write_text("$ErrorActionPreference = 'Stop'\n$taskPayload = '" + payload + "'\n" + final, encoding='ascii')
parts = [payload[n:n+12000] for n in range(0, len(payload), 12000)]
prefix = "$taskTransferDir = Join-Path $env:TEMP 'idea-clipboard-sync-" + checksum[:12] + "'\n"
for index, part in enumerate(parts, 1):
    script = "$ErrorActionPreference = 'Stop'\n" + prefix + "New-Item -ItemType Directory -Force -Path $taskTransferDir | Out-Null\n" + f"[IO.File]::WriteAllText((Join-Path $taskTransferDir '{index:03d}.part'), '{part}', [Text.Encoding]::ASCII)\n" + f"Write-Host 'Received installer part {index}/{len(parts)}'\n"
    (output / f'part-{index:03d}.txt').write_text(script, encoding='ascii')
finish = "$ErrorActionPreference = 'Stop'\n" + prefix + "$taskPayload = ''\n" + f"1..{len(parts)} | ForEach-Object {{ $taskPayload += [IO.File]::ReadAllText((Join-Path $taskTransferDir ('{{0:000}}.part' -f $_))) }}\n" + final
(output / 'finish.txt').write_text(finish, encoding='ascii')
(archive.parent / (archive.name + '.sha256')).write_text(checksum + '  ' + archive.name + '\n', encoding='ascii')
assert base64.b64decode(''.join(parts)) == data
print(f'Clipboard installers ready: {len(parts)} parts, SHA-256 {checksum}')
