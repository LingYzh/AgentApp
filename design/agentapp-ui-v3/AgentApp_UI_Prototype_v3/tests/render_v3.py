"""Render full route coverage and targeted states, using only fixture data."""
from pathlib import Path
from playwright.sync_api import sync_playwright
import os,shutil,json
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'screenshots';OUT.mkdir(exist_ok=True)
HTML=(ROOT/'index.html').read_text(encoding='utf-8')
report={'renders':[],'errors':[],'limitations':['Desktop Chromium render, not Android Compose or TalkBack/IME verification.']}
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True,executable_path=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium'))
    pg=browser.new_page(viewport={'width':412,'height':868},device_scale_factor=1.5)
    pg.on('pageerror',lambda e:report['errors'].append(str(e)))
    pg.set_content(HTML)
    pg.evaluate("document.body.classList.add('embed');AgentPrototype.state.motion=false;document.documentElement.dataset.motion='off'")
    def shot(name,screen,theme='light',width=412):
        overflow=pg.evaluate('''()=>{const p=document.querySelector('#phone'),c=document.querySelector('#phone-content>.content');return {phone:p.scrollWidth-p.clientWidth,content:c?c.scrollWidth-c.clientWidth:0}}''')
        pg.locator('#phone').screenshot(path=str(OUT/(name+'.png')))
        report['renders'].append({'file':name+'.png','screen':screen,'theme':theme,'width':width,'horizontalOverflow':overflow})
    def reset(screen,theme='light'):
        pg.evaluate('''([r,t])=>{const s=AgentPrototype.state;s.theme=t;s.overlay=null;s.detailOpen={};s.banner=null;s.codeWrap={};s.toolFull={};s.toolsScenario='completed';s.runStopped=false;s.childStopped=false;s.messageDeleted=false;s.query='';s.manage=false;s.selected.clear();AgentPrototype.navigate(r);AgentPrototype.render(false)}''',[screen,theme])
    for theme in ['light','dark']:
        for screen in pg.evaluate('AgentPrototype.screens'):
            reset(screen[0],theme);shot(f'{screen[1]}-{screen[0]}-{theme}',screen[0],theme)
        reset('tool-lab',theme)
        pg.locator('[data-tool-id=command]>summary').click();pg.locator('.tools-lab').evaluate('(e)=>e.scrollTop=100')
        shot('tool-command-'+theme,'tool-lab',theme)
        reset('tool-lab',theme)
        pg.locator('[data-tool-id=edit]>summary').click();pg.locator('.tools-lab').evaluate('(e)=>e.scrollTop=80')
        shot('tool-diff-'+theme,'tool-lab',theme)
        pg.evaluate("AgentPrototype.handle('code-wrap:diff-edit')")
        shot('tool-diff-wrap-'+theme,'tool-lab',theme)
        for scenario in ['pending','failed','empty','cancelled','nodiff']:
            reset('tool-lab',theme);pg.evaluate('(v)=>AgentPrototype.handle("tool-scenario:"+v)',scenario)
            pg.locator('.tool-inline>summary').click();pg.locator('.tools-lab').evaluate('(e)=>e.scrollTop=70')
            shot('tool-'+scenario+'-'+theme,'tool-lab',theme)
        for overlay in ['model','reasoning-quick','reasoning','context','permissions','approval']:
            reset('running' if overlay=='approval' else 'chat',theme)
            pg.evaluate('(o)=>AgentPrototype.show(o)',overlay)
            shot('panel-'+overlay+'-'+theme,'chat',theme)
        reset('providers',theme);pg.evaluate("AgentPrototype.handle('manage:providers');AgentPrototype.handle('select:p1');AgentPrototype.handle('select:p3')")
        shot('providers-multiselect-'+theme,'providers',theme)
        reset('chat',theme);pg.evaluate("AgentPrototype.handle('banner-demo')")
        shot('banner-top-'+theme,'chat',theme)
    pg.set_viewport_size({'width':360,'height':800})
    for screen in ['home','chat','providers','tool-lab','markdown']:
        reset(screen,'dark');shot('360-'+screen,screen,'dark',360)
    for typ in ['command','edit']:
        reset('tool-lab','dark');pg.locator(f'[data-tool-id={typ}]>summary').click();pg.locator('.tools-lab').evaluate('(e)=>e.scrollTop=100')
        shot('360-tool-'+typ,'tool-lab','dark',360)
    for o in ['model','reasoning-quick','context']:
        reset('chat','dark');pg.evaluate('(o)=>AgentPrototype.show(o)',o);shot('360-'+o,'chat','dark',360)
    browser.close()
(ROOT/'visual-test-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('screenshots',len(report['renders']),'errors',report['errors'])
print('overflow',[r for r in report['renders'] if r['horizontalOverflow']['phone']>1 or r['horizontalOverflow']['content']>1])
