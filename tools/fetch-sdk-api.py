"""Fetch only compilation jars from the official minimum-version IDE ZIP using HTTP ranges."""
import io
import urllib.request
import zipfile
from pathlib import Path

url = 'https://download.jetbrains.com/idea/ideaIC-2025.1.2.win.zip'
with urllib.request.urlopen(urllib.request.Request(url, method='HEAD'), timeout=30) as r:
    size = int(r.headers['Content-Length'])
    final_url = r.url

class RemoteZip(io.RawIOBase):
    def __init__(self): self.pos = 0
    def seekable(self): return True
    def readable(self): return True
    def tell(self): return self.pos
    def seek(self, offset, whence=0):
        self.pos = offset if whence == 0 else self.pos + offset if whence == 1 else size + offset
        return self.pos
    def read(self, count=-1):
        count = min(size - self.pos, count if count >= 0 else size - self.pos)
        if count <= 0: return b''
        req = urllib.request.Request(final_url, headers={'Range': f'bytes={self.pos}-{self.pos+count-1}'})
        with urllib.request.urlopen(req, timeout=60) as r:
            if r.status != 206: raise RuntimeError('Server does not honor range requests')
            result = r.read()
            if len(result) != count: raise RuntimeError(f'Range length mismatch: {len(result)} != {count}')
        self.pos += count
        return result

target = Path(__file__).resolve().parents[1] / '.tools/sdk-2025.1.2'
target.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(RemoteZip()) as z:
    jars = [i for i in z.infolist() if i.filename.startswith('lib/') and '/' not in i.filename[4:] and i.filename.endswith('.jar')]
    wanted = {'app.jar', 'app-client.jar', 'lib-client.jar', 'lib.jar', 'util.jar', 'util-8.jar', 'util_rt.jar', 'annotations.jar', 'platform-loader.jar', 'jdom.jar'}
    for item in jars:
        if Path(item.filename).name not in wanted: continue
        dest = target / Path(item.filename).name
        if dest.exists() and dest.stat().st_size == item.file_size: continue
        print('Fetching', item.filename, item.compress_size, flush=True)
        data = z.read(item)
        dest.write_bytes(data)
    for name in ('product-info.json', 'build.txt'):
        if name in z.namelist(): (target / name).write_bytes(z.read(name))
print('Minimum SDK API jars ready', flush=True)
