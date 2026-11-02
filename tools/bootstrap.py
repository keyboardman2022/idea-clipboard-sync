"""Download verified Gradle distribution and test runner using Python's TLS stack."""
from pathlib import Path
import hashlib
import urllib.request
import zipfile

root = Path(__file__).resolve().parents[1]
cache = root / '.tools'
cache.mkdir(exist_ok=True)

def fetch(url, target):
    if not target.exists():
        print('Downloading', target.name, flush=True)
        with urllib.request.urlopen(url, timeout=60) as response, target.with_suffix(target.suffix + '.part').open('wb') as out:
            while block := response.read(1024 * 1024):
                out.write(block)
        target.with_suffix(target.suffix + '.part').replace(target)
    return target

url = 'https://services.gradle.org/distributions/gradle-9.0.0-bin.zip'
archive = fetch(url, cache / 'gradle-9.0.0-bin.zip')
expected = urllib.request.urlopen(url + '.sha256', timeout=30).read().decode().strip()
assert hashlib.sha256(archive.read_bytes()).hexdigest() == expected, 'Gradle SHA-256 mismatch'
if not (cache / 'gradle-9.0.0').exists():
    with zipfile.ZipFile(archive) as z:
        z.extractall(cache)
wrapper = root / 'gradle/wrapper'
wrapper.mkdir(parents=True, exist_ok=True)
fetch('https://raw.githubusercontent.com/gradle/gradle/v9.0.0/gradle/wrapper/gradle-wrapper.jar', wrapper / 'gradle-wrapper.jar')
for name in ('gradlew', 'gradlew.bat'):
    fetch('https://raw.githubusercontent.com/gradle/gradle/v9.0.0/' + name, root / name)
(wrapper / 'gradle-wrapper.properties').write_text('distributionBase=GRADLE_USER_HOME\ndistributionPath=wrapper/dists\ndistributionUrl=https\\://services.gradle.org/distributions/gradle-9.0.0-bin.zip\ndistributionSha256Sum=' + expected + '\nzipStoreBase=GRADLE_USER_HOME\nzipStorePath=wrapper/dists\n', encoding='utf-8')
fetch('https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar', cache / 'junit-console.jar')
print('Bootstrap complete', flush=True)
