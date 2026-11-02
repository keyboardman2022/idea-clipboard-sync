from pathlib import Path
import urllib.request
import os
import shutil

root = Path(__file__).resolve().parents[1]
tools = root / '.tools'
jar = tools / 'verifier-cli-1.411-all.jar'
if not jar.exists():
    print('Downloading official Plugin Verifier 1.411', flush=True)
    urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/jetbrains/intellij/plugins/verifier-cli/1.411/verifier-cli-1.411-all.jar', jar)
api = tools / 'sdk-2025.1.2'
ide = tools / 'idea-2025.1.2'
(ide / 'lib').mkdir(parents=True, exist_ok=True)
(ide / 'plugins').mkdir(exist_ok=True)
for source in api.glob('*.jar'):
    dest = ide / 'lib' / source.name
    if not dest.exists():
        try: os.link(source, dest)
        except OSError: shutil.copyfile(source, dest)
for name in ('product-info.json', 'build.txt'):
    shutil.copyfile(api / name, ide / name)
print('Verifier and minimal official platform-library layout prepared', flush=True)
