"""Capture the actual browser replay with measured frame timestamps; no synthetic UI frames."""
from pathlib import Path
from playwright.sync_api import sync_playwright
from PIL import Image
import time, os, shutil, json, io
ROOT=Path(__file__).resolve().parents[1]
html=(ROOT/'index.html').read_text(encoding='utf-8')
frames=[];timestamps=[];errors=[]
with sync_playwright() as p:
    b=p.chromium.launch(headless=True,executable_path=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium'))
    pg=b.new_page(viewport={'width':412,'height':868},device_scale_factor=1)
    pg.on('pageerror',lambda e:errors.append(str(e)))
    pg.set_content(html)
    pg.evaluate("document.body.classList.add('embed');AgentPrototype.state.theme='dark';AgentPrototype.render(false)")
    pg.wait_for_timeout(300)
    pg.evaluate('AgentPrototype.replayMotion()')
    start=time.monotonic();i=0
    while time.monotonic()-start<10.3:
        timestamps.append(round((time.monotonic()-start)*1000))
        data=pg.screenshot(type='png',animations='allow')
        frames.append(Image.open(io.BytesIO(data)).convert('RGB').resize((360,758),Image.Resampling.LANCZOS))
        i+=1
        next_t=start+i/12
        remain=next_t-time.monotonic()
        if remain>0:time.sleep(remain)
    browser=b.version
    b.close()
durations=[max(20,round((timestamps[i+1]-timestamps[i])/10)*10) for i in range(len(timestamps)-1)]+[750]
# A global palette keeps neutrals and semantic colors steady across frames.
colorsheet=Image.new('RGB',(360*5,758*3))
for j,k in enumerate([round(x*(len(frames)-1)/14) for x in range(15)]):
    colorsheet.paste(frames[k],((j%5)*360,(j//5)*758))
pal=colorsheet.quantize(colors=224,method=Image.Quantize.MEDIANCUT)
q=[im.quantize(palette=pal,dither=Image.Dither.NONE) for im in frames]
q[0].save(ROOT/'motion-preview.gif',save_all=True,append_images=q[1:],duration=durations,loop=0,optimize=True,disposal=2)
(ROOT/'motion-capture.json').write_text(json.dumps({'browser':browser,'frames':len(frames),'encodedGifFrames':Image.open(ROOT/'motion-preview.gif').n_frames,'note':'Identical captured frames may be merged; playback durations are preserved.','sampleTimestampsMs':timestamps,'gifDurationsMs':durations,'errors':errors,'method':'Actual Chromium screenshots of AgentPrototype.replayMotion, frames resized to 360x758, global palette','scope':'Browser prototype only; not Android frame-time benchmarking'},ensure_ascii=False,indent=2),encoding='utf-8')
print('motion frames',len(frames),'duration',sum(durations),'ms; errors',errors)
