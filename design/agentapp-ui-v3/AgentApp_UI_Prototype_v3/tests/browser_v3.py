"""V3 behavioural checks. Playwright Chromium, no network/model/device execution."""
from pathlib import Path
from playwright.sync_api import sync_playwright
import json, os, shutil
ROOT=Path(__file__).resolve().parents[1]
HTML=(ROOT/'index.html').read_text(encoding='utf-8')
report={'checks':[],'errors':[],'requests':[],'environment':'Desktop Chromium with 360/412 CSS-pixel viewports. Script-injected standalone HTML (file:// blocked by environment policy). Not Android testing.'}
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True,executable_path=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium'))
    report['browser']=browser.version
    def test(name,fn,motion=False,width=412):
        pg=browser.new_page(viewport={'width':width,'height':868})
        pg.on('pageerror',lambda e:report['errors'].append(str(e)))
        pg.on('request',lambda r:report['requests'].append(r.url))
        pg.set_content(HTML);pg.evaluate("document.body.classList.add('embed')")
        if not motion:pg.evaluate("AgentPrototype.state.motion=false;document.documentElement.dataset.motion='off'")
        try:fn(pg);report['checks'].append({'test':name,'pass':True})
        except Exception as e:report['checks'].append({'test':name,'pass':False,'error':str(e)[:1800]})
        finally:pg.close()
    def nav(pg,r):pg.evaluate('(r)=>AgentPrototype.navigate(r)',r)
    def click(pg,a):pg.locator(f'#phone [data-action="{a}"]:visible').first.click(timeout=2200)
    def eq(pg,e,w):
        a=pg.evaluate(e);assert a==w,f'{e}: {a!r} != {w!r}'
    def cold(pg):
        eq(pg,'AgentPrototype.state.screen','home');eq(pg,'AgentPrototype.state.demoCreatedCount',0)
        assert pg.locator('#message-input').count()==1
    test('Cold entry is New conversation, no empty demo record is created',cold)
    def empty(pg):
        for _ in range(3):click(pg,'new-chat') if pg.locator('[data-action=new-chat]:visible').count() else pg.evaluate("AgentPrototype.handle('new-chat')")
        eq(pg,'AgentPrototype.state.demoCreatedCount',0);eq(pg,'AgentPrototype.state.screen','home')
    test('Repeated New conversation without sending creates no history entries',empty)
    def create(pg):
        pg.locator('#message-input').fill('演示首条消息');click(pg,'send')
        eq(pg,'AgentPrototype.state.demoCreatedCount',1);eq(pg,'AgentPrototype.state.screen','running')
        pg.evaluate("AgentPrototype.handle('send')");eq(pg,'AgentPrototype.state.demoCreatedCount',1)
        click(pg,'stop');pg.evaluate("AgentPrototype.handle('new-chat')");pg.locator('#message-input').fill('第二条新会话');click(pg,'send');eq(pg,'AgentPrototype.state.demoCreatedCount',2)
    test('First send creates one record, duplicate send is locked, next new draft is independent',create)
    def hist(pg):
        click(pg,'drawer');click(pg,'go:history');eq(pg,'AgentPrototype.state.screen','history');assert pg.locator('.list-row').count()>=5
    test('History remains accessible from the navigation drawer',hist)
    def compact(pg):
        nav(pg,'tool-lab');rows=pg.locator('.tool-inline');assert rows.count()==2
        assert pg.locator('[data-tool-id=command]>summary').inner_text().strip()=='运行了命令'
        edit=pg.locator('[data-tool-id=edit]>summary').inner_text();assert '已编辑' in edit and 'FilesScreen.kt' in edit and '+1' in edit and '−1' in edit
        assert pg.locator('.tool-activity,.timeline').count()==0
        assert pg.locator('.tool-inline,.tool-flow').evaluate_all('(els)=>els.every(e=>getComputedStyle(e).borderWidth==="0px" && getComputedStyle(e).paddingLeft==="0px" && getComputedStyle(e).paddingRight==="0px")')
    test('Tool summaries are borderless rows; command has no command preview',compact)
    def cmd(pg):
        nav(pg,'tool-lab');pg.locator('[data-tool-id=command]>summary').click()
        assert 'git diff --check' in pg.locator('.shell-scroll').inner_text()
        assert '1 insertion(+), 1 deletion(-)' in pg.locator('.shell-scroll').inner_text()
        assert pg.locator('#overlay [role=dialog]').count()==0;eq(pg,'AgentPrototype.state.screen','tool-lab')
    test('Command opens inline Shell containing the actual fixture command and its output',cmd)
    def diff(pg):
        nav(pg,'tool-lab');pg.locator('[data-tool-id=edit]>summary').click()
        assert '已编辑的文件' in pg.locator('[data-tool-id=edit]>summary').inner_text()
        assert pg.locator('.inline-diff-line.add').count()==1 and pg.locator('.inline-diff-line.remove').count()==1
        assert 'snackbarHostState' in pg.locator('.inline-diff-line.add').inner_text()
        assert pg.locator('.inline-diff-line.add .diff-lineno').inner_text()=='300'
        assert pg.locator('.inline-diff-line.remove .diff-lineno').inner_text()=='301'
        assert pg.locator('#overlay [role=dialog]').count()==0
    test('File edit opens raw-line diff with correct additions/removals and line numbers',diff)
    def widths(pg):
        nav(pg,'tool-lab');pg.locator('[data-tool-id=edit]>summary').click()
        v=pg.evaluate('''()=>{const f=document.querySelector('.tool-flow').getBoundingClientRect(),d=document.querySelector('.inline-diff').getBoundingClientRect();return {left:d.left-f.left,width:f.width-d.width}}''')
        assert abs(v['left'])<1 and abs(v['width'])<1,v
        assert pg.locator('.diff-scroll').evaluate('(e)=>e.scrollWidth>e.clientWidth')
        assert pg.locator('.content').evaluate('(e)=>e.scrollWidth<=e.clientWidth+1')
    test('Diff uses full conversation width with only local horizontal overflow',widths,width=360)
    def copy(pg):
        pg.evaluate("Object.defineProperty(navigator,'clipboard',{value:{writeText:async t=>{window.copied=t}},configurable:true})")
        nav(pg,'tool-lab');pg.locator('[data-tool-id=edit]>summary').click();click(pg,'code-copy:diff-edit');pg.wait_for_timeout(20)
        raw=pg.evaluate('window.copied');assert raw.startswith('--- a/') and '+    snackbarHostState' in raw and '<span' not in raw
    test('Copy Diff copies the patch source, not HTML or line-number labels',copy)
    def emptyout(pg):
        nav(pg,'tool-lab');click(pg,'tool-scenario:empty');pg.locator('[data-tool-id=empty]>summary').click()
        assert '未产生输出' in pg.locator('.shell-scroll').inner_text();assert '退出码 0' in pg.locator('.tool-panel-footer').inner_text()
    test('Empty command output is explicitly distinct from pending execution',emptyout)
    def status(pg):
        nav(pg,'tool-lab')
        for scenario,id,caption in [('pending','pending','等待命令批准'),('running','executing','正在运行命令'),('failed','failed','命令执行失败'),('cancelled','cancelled','命令已中止')]:
            click(pg,'tool-scenario:'+scenario);assert pg.locator('[data-tool-id='+id+']>summary').inner_text().strip()==caption
            pg.locator('[data-tool-id='+id+']>summary').click()
            if scenario=='pending':assert '尚未执行' in pg.locator('.tool-waiting').inner_text()
    test('Pending/running/failed/cancelled records are not falsely marked completed',status)
    def nodiff(pg):
        nav(pg,'tool-lab');click(pg,'tool-scenario:nodiff');assert pg.locator('.inline-diff-stats').count()==0
        pg.locator('[data-tool-id=nodiff]>summary').click();assert '未保存可用 Diff' in pg.locator('.inline-tool-note').inner_text();assert pg.locator('.inline-diff-line').count()==0
    test('Missing snapshot shows no fabricated diff or change count',nodiff)
    def preserve(pg):
        nav(pg,'tool-lab');pg.locator('[data-tool-id=edit]>summary').click()
        pg.evaluate("window.beforeNode=document.querySelector('[data-tool-id=edit]');document.querySelector('.diff-scroll').scrollLeft=80;AgentPrototype.state.theme='dark';AgentPrototype.render()")
        eq(pg,"window.beforeNode===document.querySelector('[data-tool-id=edit]')",True)
        eq(pg,"document.querySelector('[data-tool-id=edit]').open",True)
        assert pg.locator('.diff-scroll').evaluate('(e)=>e.scrollLeft')>=79
    test('Theme rerender preserves tool node, expanded state and horizontal scroll',preserve)
    def align(pg):
        for theme in ['light','dark']:
            pg.evaluate('(t)=>{AgentPrototype.state.theme=t;AgentPrototype.render()}',theme)
            vals=pg.locator('.with-chevron').evaluate_all('''els=>els.filter(e=>e.getBoundingClientRect().width).map(e=>{const a=e.querySelector('.button-label').getBoundingClientRect(),b=e.querySelector('.button-chevron').getBoundingClientRect();return {text:e.innerText,dy:Math.abs(a.top+a.height/2-b.top-b.height/2),sameRow:Math.min(a.bottom,b.bottom)>Math.max(a.top,b.top)}})''')
            assert vals and all(v['dy']<1 and v['sameRow'] for v in vals),vals
    test('Down-chevron label/icon centers align within one pixel in light and dark',align,width=360)
    def nodes(pg):
        nav(pg,'providers');pg.evaluate("window.card=document.querySelector('.provider-card');window.input=document.querySelector('input[value=p1]')")
        pg.locator('input[value=p2]').check();eq(pg,"window.card===document.querySelector('.provider-card')",True)
        eq(pg,"window.input===document.querySelector('input[value=p1]')",True)
    test('Provider default changes keep stable DOM nodes for smooth control transitions',nodes)
    def focus(pg):
        nav(pg,'provider-edit');pg.locator('[data-field=provider-name]').fill('测试编辑')
        pg.evaluate('AgentPrototype.render()');eq(pg,"document.activeElement.dataset.field",'provider-name')
        assert pg.locator('[data-field=provider-name]').input_value()=='测试编辑'
    test('Non-navigation rerenders preserve text-field focus and edited value',focus)
    def animate_inline(pg):
        nav(pg,'tool-lab');pg.wait_for_timeout(300);pg.evaluate('AgentPrototype.animationLog.length=0')
        pg.locator('[data-tool-id=edit]>summary').click();pg.wait_for_timeout(50)
        h1=pg.locator('[data-tool-id=edit]').evaluate('(e)=>e.getBoundingClientRect().height');pg.wait_for_timeout(80)
        h2=pg.locator('[data-tool-id=edit]').evaluate('(e)=>e.getBoundingClientRect().height');assert h2>h1,(h1,h2)
        pg.locator('[data-tool-id=edit]>summary').click();pg.wait_for_timeout(310)
        eq(pg,"document.querySelector('[data-tool-id=edit]').open",False)
        assert any(x['label']=='inline-expand' for x in pg.evaluate('AgentPrototype.animationLog'))
    test('Inline expansion interpolates real layout height and collapses correctly',animate_inline,True)
    def rapid(pg):
        nav(pg,'tool-lab');pg.wait_for_timeout(300)
        pg.evaluate("const d=document.querySelector('[data-tool-id=command]');AgentPrototype.mutateDetails(d,true);setTimeout(()=>AgentPrototype.mutateDetails(d,false),40);setTimeout(()=>AgentPrototype.mutateDetails(d,true),80)")
        pg.wait_for_timeout(400);eq(pg,"document.querySelector('[data-tool-id=command]').open",True)
        eq(pg,"document.querySelector('[data-tool-id=command]').style.height",'')
    test('Rapid expand/collapse reversals finish in the last requested state',rapid,True)
    def overlays(pg):
        click(pg,'sheet:reasoning-quick');pg.wait_for_timeout(320);click(pg,'sheet:reasoning');pg.wait_for_timeout(70)
        assert pg.locator('.overlay-snapshot').count()==1
        pg.wait_for_timeout(220);assert pg.locator('.overlay-snapshot').count()==0
        assert any(x['label']=='overlay-replace-exit' for x in pg.evaluate('AgentPrototype.animationLog'))
        pg.keyboard.press('Escape');pg.wait_for_timeout(240);eq(pg,"document.querySelector('#phone-content').inert",False)
    test('Popup-to-popup replacement animates the old surface out and cleans up',overlays,True)
    def banner(pg):
        pg.evaluate("AgentPrototype.handle('banner-demo')");pg.wait_for_timeout(30)
        h1=pg.locator('.feedback-lane').evaluate('(e)=>e.getBoundingClientRect().height');pg.wait_for_timeout(240)
        h2=pg.locator('.feedback-lane').evaluate('(e)=>e.getBoundingClientRect().height');assert h2>h1
        pg.evaluate("AgentPrototype.handle('banner-close')");pg.wait_for_timeout(240);assert pg.locator('.feedback-lane').count()==0
    test('Top banner reserves animated height on entry and releases it on exit',banner,True)
    def labels(pg):
        nav(pg,'providers');pg.wait_for_timeout(350);pg.evaluate('AgentPrototype.animationLog.length=0');pg.locator('input[value=p2]').check();pg.wait_for_timeout(100)
        assert any(x['label'].startswith('state-') for x in pg.evaluate('AgentPrototype.animationLog'))
    test('Ordinary state changes have local transitions rather than page re-entry',labels,True)
    def css(pg):
        v=pg.evaluate('''()=>Object.fromEntries(['.phone','.with-chevron','.send:before'].map(s=>{let pseudo=s.includes(':before');let el=document.querySelector(s.replace(':before',''));return [s,getComputedStyle(el,pseudo?'::before':null).transitionDuration]}))''')
        assert all(t!='0s' for t in v.values()),v
    test('Width, button, send-state CSS transitions are configured',css,True)
    def reduce(pg):
        pg.emulate_media(reduced_motion='reduce');pg.evaluate('AgentPrototype.animationLog.length=0');nav(pg,'tool-lab')
        pg.locator('[data-tool-id=edit]>summary').click();click(pg,'sheet:reasoning-quick');pg.keyboard.press('Escape')
        assert pg.evaluate('AgentPrototype.animationLog.length')==0
        assert pg.locator('.with-chevron').first.evaluate('(e)=>getComputedStyle(e).transitionDuration')=='0s'
    test('Reduced-motion renders final states with no web animations',reduce,True)
    def no_keyframes_stream(pg):
        nav(pg,'markdown');pg.wait_for_timeout(350);click(pg,'markdown-stream');pg.wait_for_timeout(30);pg.evaluate('AgentPrototype.animationLog.length=0');pg.wait_for_timeout(440)
        logs=pg.evaluate('AgentPrototype.animationLog');assert not logs,logs
    test('Continuous Markdown token updates never reanimate page or content blocks',no_keyframes_stream,True)
    browser.close()
(ROOT/'v3-test-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
for t in report['checks']:print(('PASS' if t['pass'] else 'FAIL')+' '+t['test']+(' '+t.get('error','') if not t['pass'] else ''))
print('TOTAL',len(report['checks']),'PASS',sum(t['pass'] for t in report['checks']),'ERRORS',report['errors'])
raise SystemExit(1 if report['errors'] or any(not t['pass'] for t in report['checks']) else 0)
