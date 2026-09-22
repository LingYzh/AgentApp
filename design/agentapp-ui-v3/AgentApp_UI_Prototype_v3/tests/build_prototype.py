"""Rebuild single-file prototype from editable files. Python standard library only."""
from pathlib import Path
import re
ROOT = Path(__file__).resolve().parents[1]
html = (ROOT / 'index.html').read_text(encoding='utf-8')
css = '\n'.join((ROOT / 'source' / p).read_text(encoding='utf-8') for p in ('styles.css', 'refinements.css', 'iteration-v3.css'))
js = '\n'.join((ROOT / 'source' / p).read_text(encoding='utf-8') for p in ('prototype.js', 'refinements.js', 'iteration-v3.js'))
html = re.sub(r'<style>[\s\S]*?</style>', lambda _: '<style>\n' + css + '\n</style>', html, count=1)
html = re.sub(r'<script>[\s\S]*?</script>', lambda _: '<script>\n' + js + '\n</script>', html, count=1)
(ROOT / 'index.html').write_text(html, encoding='utf-8')
print(ROOT / 'index.html')
