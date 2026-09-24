#!/usr/bin/env python3
"""Offline UAH resource checks. Python 3.10+; Pillow required.
Option --output writes a JSON report. This is NOT an Android APK build.
"""
from __future__ import annotations
import argparse, hashlib, json, math, re
from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image

KIT=Path(__file__).resolve().parents[1]
RES=KIT/'android/app/src/main/res'
A='{http://schemas.android.com/apk/res/android}'
DENSITIES={'mdpi':1,'hdpi':1.5,'xhdpi':2,'xxhdpi':3,'xxxhdpi':4}


def verify() -> dict:
    checks=[]
    def check(name,condition,detail=''):
        checks.append({'check':name,'passed':bool(condition),'detail':detail})
    files=[p for p in RES.rglob('*') if p.is_file()]
    check('android-runtime-count',len(files)==19,str(len(files)))
    for den,scale in DENSITIES.items():
        n=int(48*scale)
        for name in ('ic_launcher','ic_launcher_round'):
            p=RES/f'mipmap-{den}/{name}.png'
            with Image.open(p) as im:
                check(f'{den}/{name} dimensions',im.size==(n,n),str(im.size))
                check(f'{den}/{name} RGBA',im.mode=='RGBA',im.mode)
                check(f'{den}/{name} transparent corner',im.getpixel((0,0))[3]==0)
        for layer in ('foreground','background','monochrome'):
            p=KIT/f'adaptive-png/{den}/{layer}.png'
            with Image.open(p) as im:
                check(f'adaptive-{den}-{layer} size',im.size==(int(108*scale),int(108*scale)))
                alpha=im.getchannel('A')
                check(f'adaptive-{den}-{layer} alpha',alpha.getextrema()==((255,255) if layer=='background' else (0,255)))
    for p in sorted(KIT.glob('icons/*.png')):
        n=int(p.stem.rsplit('-',1)[-1])
        with Image.open(p) as im:
            check(p.name+' dimensions',im.size==(n,n))
            check(p.name+' sRGB',bool(im.info.get('icc_profile')))
            if 'mark-' in p.name:
                check(p.name+' genuine transparency',im.getchannel('A').getextrema()==(0,255))
    geometry=json.loads((KIT/'artwork/geometry.json').read_text())
    for p in sorted(RES.glob('drawable/*.xml')):
        root=ET.parse(p).getroot()
        check(p.name+' vector',root.tag=='vector')
        if p.stem!='ic_uah_background':
            paths=[n.get(A+'pathData') for n in root.iter('path')]
            check(p.name+' identical source paths',paths==geometry['paths'])
            group=root.find('group')
            check(p.name+' group scale',float(group.get(A+'scaleX'))==geometry['scale'] and float(group.get(A+'scaleY'))==geometry['scale'])
        if 'foreground' in p.name or 'monochrome' in p.name or 'background' in p.name:
            check(p.name+' 108dp viewport',root.get(A+'width')=='108dp' and root.get(A+'viewportWidth')=='108')
    ids={('drawable',p.stem) for p in RES.glob('drawable/*.xml')}
    for p in sorted(RES.glob('mipmap-anydpi-*/*.xml')):
        root=ET.parse(p).getroot()
        check(str(p.relative_to(RES))+' adaptive root',root.tag=='adaptive-icon')
        for el in root:
            ref=el.get(A+'drawable','')
            kind,_,name=ref.lstrip('@').partition('/')
            check(p.parent.name+'/'+p.stem+'/'+el.tag+' reference',(kind,name) in ids,ref)
        check(str(p.relative_to(RES))+' mono API',('v33' in p.parent.name)==(root.find('monochrome') is not None))
    for p in KIT.glob('artwork/*.svg'):
        txt=p.read_text()
        check(p.name+' pure paths/no embedded media',not any(x in txt for x in ('<image','<text','<filter','<linearGradient','<radialGradient','<script','<foreignObject')))
    # Compare alpha masks at xxxhdpi (identical shape, independent coloration).
    fg=Image.open(KIT/'adaptive-png/xxxhdpi/foreground.png').convert('RGBA')
    mono=Image.open(KIT/'adaptive-png/xxxhdpi/monochrome.png').convert('RGBA')
    check('foreground-monochrome-alpha-equal',fg.getchannel('A').tobytes()==mono.getchannel('A').tobytes())
    max_radius=0.0
    points=[]
    for y in range(fg.height):
        for x in range(fg.width):
            if fg.getpixel((x,y))[3]>=128:
                xp=(x+.5)*108/fg.width; yp=(y+.5)*108/fg.height
                points.append((xp,yp))
                max_radius=max(max_radius,math.hypot(xp-54,yp-54))
    check('foreground-inside-central-66dp-circle',max_radius<=33.01,f'{max_radius:.3f}dp radius')
    bbox=[min(p[0] for p in points),min(p[1] for p in points),max(p[0] for p in points),max(p[1] for p in points)]
    check('foreground-within-66dp-square',min(bbox[:2])>=21 and max(bbox[2:])<=87,str(bbox))
    with Image.open(KIT/'store/uah-play-512.png') as im:
        check('store-512-square',im.size==(512,512))
        check('store-opaque-full-square',im.getchannel('A').getextrema()==(255,255))
        check('store-32-bit-RGBA-sRGB',im.mode=='RGBA' and bool(im.info.get('icc_profile')))
        check('store-under-1024KB',(KIT/'store/uah-play-512.png').stat().st_size<=1024*1024)
    check('no-font-files',not any(p.suffix.lower() in ('.ttf','.otf','.woff','.woff2','.ttc') for p in KIT.rglob('*')))
    return {'kind':'offline-resource-validation','all_passed':all(c['passed'] for c in checks),'checks_total':len(checks),'checks_passed':sum(c['passed'] for c in checks),'checks':checks,'android_apk_build':'not performed','device_test':'not performed','github_write':'not performed'}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path)
    args=p.parse_args();result=verify()
    if args.output:args.output.write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf-8')
    print(f"{result['checks_passed']}/{result['checks_total']} offline checks passed")
    for c in result['checks']:
        if not c['passed']:print('FAIL',c['check'],c['detail'])
    raise SystemExit(0 if result['all_passed'] else 1)
