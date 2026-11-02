from pathlib import Path
import zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
version = ET.parse(root / 'src/main/resources/META-INF/plugin.xml').findtext('version')
destination = root / f'build/distributions/idea-clipboard-sync-{version}-sources.zip'
paths = [root / name for name in ('README.md', '.gitignore', '.gitattributes', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat')]
for folder in ('src', 'tools', 'docs', 'gradle'):
    paths.extend(p for p in (root / folder).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(paths): archive.write(path, Path('idea-clipboard-sync') / path.relative_to(root))
print('Source release:', destination)
