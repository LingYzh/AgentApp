#!/usr/bin/env python3
"""Install the UAH icon kit. Python 3.10+, standard library only.
Default: dry run. --apply writes only after a local ZIP backup.
--rename also updates visible brand string resources, NOT Android identity.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

KIT = Path(__file__).resolve().parents[1]
SOURCE = KIT / 'android/app/src/main/res'
ANDROID = '{http://schemas.android.com/apk/res/android}'
LAUNCHER = {'ic_launcher', 'ic_launcher_round'}
DRAWABLES = {'ic_uah_foreground', 'ic_uah_background', 'ic_uah_monochrome', 'ic_uah_mark', 'ic_stat_uah'}
EXTENSIONS = {'.png', '.webp', '.xml', '.jpg', '.jpeg'}
BRAND = {'app_name': 'Used AI Harness', 'app_name_full': 'Used AI Harness', 'app_name_short': 'UAH'}


def safe(path: Path, root: Path) -> None:
    root = root.resolve(strict=True)
    if not path.resolve().is_relative_to(root):
        raise ValueError(f'Path escapes project: {path}')
    current = path
    while current != root and current != current.parent:
        if current.is_symlink():
            raise ValueError(f'Refusing symlink: {current}')
        current = current.parent


def raw_text(path: Path) -> tuple[str, str]:
    data = path.read_bytes()
    return data.decode('utf-8-sig'), ('\r\n' if b'\r\n' in data else '\n')


def rename_plan(res: Path) -> dict[Path, bytes]:
    """Replace only named string elements, preserving other content and EOLs."""
    updates: dict[Path, bytes] = {}
    default_found: set[str] = set()
    for path in sorted(res.glob('values*/*.xml')):
        if not path.is_file():
            continue
        root = ET.parse(path).getroot()
        found: list[str] = []
        for node in root:
            name = node.get('name')
            if name not in BRAND:
                continue
            if node.tag != 'string' or len(node) != 0:
                raise ValueError(f'Complex {name} definition in {path}; merge manually instead.')
            if name in found:
                raise ValueError(f'Duplicate brand string in {path}: {name}')
            found.append(name)
            if path.parent.name == 'values':
                if name in default_found:
                    raise ValueError(f'Duplicate default brand string: {name}')
                default_found.add(name)
        if not found:
            continue
        text, eol = raw_text(path)
        original = text
        for name in found:
            expression = re.compile(r'(<string\b[^>]*\bname\s*=\s*[\"\']' + re.escape(name) + r'[\"\'][^>]*>)(.*?)(</string\s*>)', re.S)
            text, n = expression.subn(lambda m: m[1] + BRAND[name] + m[3], text)
            if n != 1:
                raise ValueError(f'Cannot safely patch {name} in {path}; merge manually.')
        ET.fromstring(text)
        if text != original:
            updates[path] = text.encode('utf-8')
    if 'app_name' not in default_found:
        raise ValueError('No default string app_name found. Rename manually for this project layout.')
    missing = [k for k in ('app_name_full','app_name_short') if k not in default_found]
    if missing:
        target = res/'values/uah_brand_strings.xml'
        if target.exists():
            text,eol = raw_text(target)
            source = updates.get(target, text.encode()).decode('utf-8')
            if not source.rstrip().endswith('</resources>'):
                raise ValueError(f'Cannot safely extend {target}')
            additions = ''.join(f'    <string name="{k}" translatable="false">{BRAND[k]}</string>{eol}' for k in missing)
            updated = source.replace('</resources>', additions+'</resources>', 1)
        else:
            updated = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + ''.join(f'    <string name="{k}" translatable="false">{BRAND[k]}</string>\n' for k in missing) + '</resources>\n'
        ET.fromstring(updated)
        updates[target] = updated.encode('utf-8')
    return updates


def run(project: Path, apply: bool, rename: bool) -> dict[str, object]:
    if project.is_symlink():
        raise ValueError('Use a non-symlink project path.')
    project = project.resolve(strict=True)
    main = project/'app/src/main'
    res = main/'res'
    if not res.is_dir():
        raise ValueError('Expected --project/app/src/main/res.')
    safe(res, project)
    for entry in res.glob('values*/*.xml'):
        safe(entry, project)
    manifest = main/'AndroidManifest.xml'
    safe(manifest, project)
    app = ET.parse(manifest).getroot().find('application')
    if app is None or app.get(ANDROID+'icon') != '@mipmap/ic_launcher' or app.get(ANDROID+'roundIcon') != '@mipmap/ic_launcher_round':
        raise ValueError('Unexpected manifest icon references; merge manually. Nothing changed.')
    if rename and app.get(ANDROID+'label') != '@string/app_name':
        raise ValueError('Application label does not use @string/app_name; rename manually.')
    for component in app:
        if component.get(ANDROID+'label'):
            print('NOTE: component-specific label needs manual review:', component.tag, component.get(ANDROID+'name'), component.get(ANDROID+'label'))
    sources = sorted(p for p in SOURCE.rglob('*') if p.is_file())
    if len(sources) != 19:
        raise ValueError('Incomplete kit: expected 19 Android runtime resources.')
    updates: dict[Path, bytes] = {}
    for source in sources:
        safe(source, KIT)
        updates[res/source.relative_to(SOURCE)] = source.read_bytes()
    removals: set[Path] = set()
    for folder in res.iterdir():
        if not folder.is_dir():
            continue
        names = LAUNCHER if folder.name.startswith('mipmap') else DRAWABLES if folder.name.startswith('drawable') else set()
        for p in folder.iterdir():
            if p.is_file() and p.stem in names and p.suffix.lower() in EXTENSIONS and p not in updates:
                removals.add(p)
    if rename:
        updates.update(rename_plan(res))
    # Validate all value XML and prevent drawable aliases from colliding with files.
    for p in res.glob('values*/*.xml'):
        root = ET.parse(p).getroot()
        for el in root:
            resource_type = el.get('type') if el.tag == 'item' else el.tag
            if resource_type in ('mipmap','drawable') and el.get('name') in LAUNCHER|DRAWABLES:
                raise ValueError(f'Existing resource alias could collide: {p}; merge manually.')
    for p in set(updates)|removals:
        safe(p, project)
    # Skip byte-identical files, making subsequent applies idempotent.
    updates = {p:data for p,data in updates.items() if not p.exists() or p.read_bytes()!=data}
    print('Project:',project)
    print('Mode:', 'APPLY' if apply else 'DRY RUN — no files changed')
    for p in sorted(removals): print('REMOVE old icon:',p.relative_to(project))
    for p in sorted(updates): print('UPDATE' if p.exists() else 'ADD',p.relative_to(project))
    for p in sorted((project/'app/src').glob('*/res/mipmap*/*')):
        if not p.is_relative_to(res) and p.stem in LAUNCHER:
            print('WARNING: another source set may override launcher icon:',p.relative_to(project))
    for p in sorted((project/'app/src').glob('*/res/values*/*.xml')):
        if not p.is_relative_to(res):
            root=ET.parse(p).getroot()
            if any(n.get('name') in BRAND for n in root): print('WARNING: another source set may override brand strings:',p.relative_to(project))
    report={'updated':len(updates),'removed':len(removals),'renamed':rename,'applied':False}
    if not apply:
        print('Review the plan; append --apply to write. --rename includes visible names.')
        return report
    if not updates and not removals:
        print('Already up to date. No backup or writes needed.')
        return report
    touched=set(updates)|removals
    original={p:p.read_bytes() for p in touched if p.exists()}
    added={p for p in updates if not p.exists()}
    stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    backup=project/'.uah-icon-backups'/f'{stamp}.zip'
    safe(backup,project)
    backup.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(backup,'x',zipfile.ZIP_DEFLATED) as z:
        for p,data in original.items(): z.writestr('before/'+p.relative_to(project).as_posix(),data)
        z.writestr('restore.json',json.dumps({'remove_created_files':[p.relative_to(project).as_posix() for p in sorted(added)],'restore_from':'before/','warning':'Stop Gradle/build tools first; do not delete app data.'},indent=2))
    temp: list[Path] = []
    try:
        for p in sorted(removals): p.unlink()
        for p,data in updates.items():
            p.parent.mkdir(parents=True,exist_ok=True)
            with tempfile.NamedTemporaryFile(prefix='.uah-',suffix='.tmp',dir=p.parent,delete=False) as f:
                tmp=Path(f.name); temp.append(tmp); f.write(data)
            tmp.replace(p)
            if p.read_bytes()!=data: raise OSError(f'Write verification failed: {p}')
    except Exception:
        for p in temp:
            if p.exists() and not p.is_symlink(): p.unlink()
        for p in added:
            if p.is_file() and not p.is_symlink(): p.unlink()
        for p,data in original.items(): p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(data)
        print('Failure: original files restored; backup retained:',backup,file=sys.stderr)
        raise
    report['applied']=True; report['backup']=str(backup)
    print('Done. Backup:',backup)
    print('applicationId, namespace, Application/Activity class names, permissions and app data were NOT changed.')
    print('Next: build, compare installed icon, verify launcher cache without clearing app data.')
    return report


def main() -> None:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--project',required=True,type=Path)
    p.add_argument('--apply',action='store_true')
    p.add_argument('--rename',action='store_true',help='Set app_name/full to Used AI Harness and short to UAH')
    args=p.parse_args()
    try: run(args.project,args.apply,args.rename)
    except (OSError,ValueError,ET.ParseError,zipfile.BadZipFile) as exc: p.exit(1,'ERROR: '+str(exc)+'\n')

if __name__=='__main__': main()
