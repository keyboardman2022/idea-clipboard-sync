"""Add official module descriptors needed by Plugin Verifier to the minimum API layout."""
import runpy
import zipfile
from pathlib import Path

scope = runpy.run_path(str(Path(__file__).with_name('fetch-sdk-api.py')))
target = Path(__file__).resolve().parents[1] / '.tools/idea-2025.1.2'
with zipfile.ZipFile(scope['RemoteZip']()) as archive:
    for item in archive.infolist():
        if item.filename not in ('modules/module-descriptors.jar', 'bin/idea.properties'): continue
        destination = target / item.filename
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(archive.read(item))
        print('Extracted', item.filename, flush=True)
