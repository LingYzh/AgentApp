"""Executable UI acceptance checks. Requires Playwright + Chromium, no Android SDK.
Uses set_content to support offline/restricted environments. Actual external IO is mocked.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright
import json, shutil, os
ROOT=Path(__file__).resolve().parents[1]
HTML=(ROOT/'index.html').read_text(encoding='utf-8')
report={'checks':[],'errors':[],'requests':[],'environment':'Playwright + system Chromium; standalone HTML; synthetic fixtures, no API/device calls'}
with sync_playwright() as p:
    executable=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium')
    browser=p.chromium.launch(headless=True,**({'executable_path':executable} if executable else {}))
    report['browser']=browser.version
    def test(name, fn, motion=False):
        pg=browser.new_page(viewport={'width':412,'height':892})
        pg.on('pageerror',lambda e:report['errors'].append(str(e)))
        pg.on('request',lambda r:report['requests'].append(r.url))
        pg.set_content(HTML)
        pg.evaluate("document.body.classList.add('embed')")
        if not motion:pg.evaluate("AgentPrototype.state.motion=false;document.documentElement.dataset.motion='off'")
        try:
            fn(pg);report['checks'].append({'test':name,'pass':True})
        except Exception as e:report['checks'].append({'test':name,'pass':False,'error':str(e)[:1200]})
        finally:pg.close()
    def nav(pg,r):pg.evaluate('(r)=>AgentPrototype.navigate(r)',r)
    def click(pg,a):pg.locator(f'#phone [data-action="{a}"]:visible').first.click(timeout=2500)
    def eq(pg,e,w):
        a=pg.evaluate(e);assert a==w,f'{e}: {a!r} != {w!r}'
    def check_all(pg):
        for s in pg.evaluate('AgentPrototype.screens'):
            nav(pg,s[0]);assert pg.locator('#phone-content .topbar').count()==1
    test('All 26 routes render and have a top bar',check_all)
    def white(pg):
        pg.evaluate("AgentPrototype.state.theme='dark';AgentPrototype.render()")
        nav(pg,'styles')
        colors=pg.locator('.btn.primary,.btn.destructive,.btn.ink').evaluate_all('(els)=>els.map(e=>getComputedStyle(e).color)')
        assert colors and all(c=='rgb(255, 255, 255)' for c in colors),colors
        nav(pg,'home');pg.locator('#message-input').fill('测试')
        assert pg.locator('.send').evaluate('(e)=>getComputedStyle(e).color')=='rgb(255, 255, 255)'
        assert pg.locator('.send svg').evaluate('(e)=>getComputedStyle(e).stroke')=='rgb(255, 255, 255)'
    test('Dark filled actions and send icon use white foreground',white)
    def modelcaps(pg):
        click(pg,'sheet:model');assert pg.locator('.model-option').count()==3
        assert pg.locator('.model-option').last.locator('.native-cap').count()==4
        assert '手动覆盖' in pg.locator('#overlay').inner_text()
        pg.locator('[data-search=models]').fill('Gemini');assert pg.locator('.model-option').count()==1
        click(pg,'model:Gemini · 示例');eq(pg,'AgentPrototype.state.model','Gemini · 示例')
    test('Model picker shows per-model native capability icons and search',modelcaps)
    def ratio(pg,unknown=False,compacted=False):
        pg.evaluate('(s)=>{AgentPrototype.state.unknown=s[0];AgentPrototype.state.compacted=s[1];AgentPrototype.show("context")}',[unknown,compacted])
        v=pg.evaluate('''()=>{let es=[...document.querySelectorAll('.context-segment')];return {sum:es.reduce((a,e)=>a+parseFloat(e.style.width),0),colors:es.map(e=>e.style.background),count:es.length,label:document.querySelector('.context-composition').getAttribute('aria-label')}}''')
        expected=100 if unknown else 6.2 if compacted else 17.14
        assert abs(v['sum']-expected)<.001,v
        assert len(set(v['colors']))==v['count'] and v['count']>=7
        if unknown:assert '容量未知' in v['label'] and '占容量' not in v['label']
    test('Context known capacity segments sum to 17.14%, not 100%',lambda pg:ratio(pg))
    test('Context unknown capacity shows composition, no fake utilization',lambda pg:ratio(pg,True))
    test('Compacted context adds summary segment and sums to 6.2%',lambda pg:ratio(pg,False,True))
    def slider(pg):
        click(pg,'sheet:reasoning-quick')
        eq(pg,'AgentPrototype.state.effortValue',None)
        assert '中 · medium' in pg.locator('.reasoning-title').inner_text()
        assert pg.locator('.reasoning-popover .model-option').count()==0
        r=pg.locator('#effort-slider');r.focus();pg.keyboard.press('End')
        eq(pg,'AgentPrototype.state.effortValue','max')
        pg.keyboard.press('Home');eq(pg,'AgentPrototype.state.effortValue','none')
        assert 'thinking.type = "disabled"' in pg.locator('.reasoning-wire').inner_text()
        click(pg,'reasoning-reset');eq(pg,'AgentPrototype.state.effortValue',None)
        assert 'medium' in pg.locator('.reasoning-title').inner_text()
    test('Slider keyboard steps, explicit none, and reset follow remain distinct',slider)
    def slidercommit(pg):
        click(pg,'sheet:reasoning-quick');pg.locator('#effort-slider').evaluate("e=>{e.value=4;e.dispatchEvent(new Event('input',{bubbles:true}))}")
        eq(pg,'AgentPrototype.state.effortValue',None)
        assert 'xhigh' in pg.locator('.reasoning-title').inner_text()
        pg.locator('#effort-slider').dispatch_event('change');eq(pg,'AgentPrototype.state.effortValue','xhigh')
    test('Dragging previews without committing until change',slidercommit)
    def detail(pg):
        click(pg,'sheet:reasoning-quick');click(pg,'sheet:reasoning');assert pg.locator('.effort-option').count()==7
        assert pg.locator('.effort-option code').all_text_contents()==['null','none','low','medium','high','xhigh','max']
        click(pg,'reasoning-value:high');eq(pg,'AgentPrototype.state.effortValue','high')
    test('Slider title opens detailed canonical + protocol values',detail)
    def unsupported(pg):
        pg.evaluate("AgentPrototype.handle('reasoning-value:max');AgentPrototype.handle('model:Gemini · 示例')")
        eq(pg,'AgentPrototype.state.effortValue',None);assert '不支持' in pg.locator('.feedback-text').inner_text()
        click(pg,'sheet:reasoning-quick');click(pg,'sheet:reasoning');values=pg.locator('.effort-option code').all_text_contents()
        assert values==['null','minimal','low','medium','high'],values
    test('Unsupported effort resets when switching protocol; options follow support',unsupported)
    def unk(pg):
        pg.evaluate("AgentPrototype.handle('model:自定义模型 · 示例');AgentPrototype.show('reasoning-quick')")
        assert pg.locator('.reasoning-slider.indeterminate').count()==1
        eq(pg,'AgentPrototype.state.effortValue',None)
    test('Unknown model default does not display none as selected',unk)
    def radio(pg):
        nav(pg,'providers');pg.locator('input[name=default-provider][value=p2]').check()
        eq(pg,'AgentPrototype.state.selectedProviderId','p2')
        assert pg.locator('input[name=default-provider]:checked').count()==1
        assert pg.locator('.provider-card [data-action^="default-provider"]').count()==0
        assert pg.locator('.provider-card [data-action^="edit:providers:"]').count()==3
        click(pg,'list-menu:providers');click(pg,'manage:providers')
        assert pg.locator('input[name=default-provider]').count()==0
        pg.locator('[data-provider-select=p1]').check();pg.locator('[data-provider-select=p3]').check()
        eq(pg,'AgentPrototype.state.selectedProviderId','p2');eq(pg,'AgentPrototype.state.selected.size',2)
        assert pg.locator('.provider-card input:checked').count()==2
    test('Radio global default vs independent multi-select checkboxes',radio)
    def provideredit(pg):
        nav(pg,'providers');click(pg,'edit:providers:p2');eq(pg,'AgentPrototype.state.screen','provider-edit')
        assert pg.locator('[data-field=provider-name]').input_value()=='兼容接口'
    test('One clear provider edit target preserves edit workflow',provideredit)
    def banner(pg):
        nav(pg,'chat');click(pg,'banner-demo') if pg.locator('[data-action=banner-demo]').count() else pg.evaluate("AgentPrototype.handle('banner-demo')")
        v=pg.evaluate('''()=>{const b=document.querySelector('.feedback-lane').getBoundingClientRect(),t=document.querySelector('.topbar').getBoundingClientRect(),c=document.querySelector('.composer-wrap').getBoundingClientRect();return {below:b.top>=t.bottom-1,above:b.bottom<c.top,position:getComputedStyle(document.querySelector('.feedback-lane')).position}}''')
        assert v['below'] and v['above'] and v['position'] not in ['absolute','fixed'],v
        assert pg.locator('#toast').count()==0
        click(pg,'banner-close');assert pg.locator('.feedback-lane').count()==0
    test('Feedback occupies top layout and never covers bottom composer',banner)
    def banner_sheet(pg):
        click(pg,'sheet:permissions');pg.evaluate("AgentPrototype.handle('banner-demo')")
        assert pg.locator('#overlay .feedback-lane').count()==1
        assert pg.locator('#phone-content .feedback-lane').count()==0
    test('Feedback moves inside active panel, not underneath scrim',banner_sheet)
    def tool(pg):
        nav(pg,'chat');# v3 exposes rows directly: no parent step card
        pg.locator('[data-tool-id=read] > summary').click()
        assert pg.locator('[data-tool-id=read]').get_attribute('open') is not None
        assert pg.locator('#overlay [role=dialog]').count()==0
        assert pg.locator('[data-tool-id=read] .md-code').count()==2
        assert 'project-notes.md' in pg.locator('[data-code-id=args-read] pre').inner_text()
        eq(pg,'AgentPrototype.state.screen','chat')
        pg.evaluate('AgentPrototype.render()');assert pg.locator('[data-tool-id=read]').get_attribute('open') is not None
    test('Tool arguments/results expand inline and survive rerender',tool)
    def pending(pg):
        nav(pg,'running');pg.locator('[data-tool-id=pending] > summary').click()
        assert '尚未执行' in pg.locator('[data-tool-id=pending]').inner_text()
        assert pg.locator('[data-code-id=result-command]').count()==0
    test('Pending tool does not fabricate a completed result',pending)
    def md(pg):
        nav(pg,'markdown')
        assert pg.locator('#markdown-live h1').count()==1
        assert pg.locator('#markdown-live blockquote').count()==1
        assert pg.locator('#markdown-live ul ul').count()==1
        assert pg.locator('#markdown-live .task-item').count()==3
        assert pg.locator('.md-code').count()==3
        assert pg.locator('.md-table-wrap').count()==1
        assert pg.locator('.syntax-keyword').count()>1
    test('Markdown sample covers headings, quote, nested/task lists, highlighted code and table',md)
    def codewrap(pg):
        nav(pg,'markdown');code=pg.locator('[data-code-id=showcase-1]')
        code.locator('[data-action^="code-wrap:"]').click();assert code.locator('pre').get_attribute('class')=='code-wrapped'
        code.locator('[data-action^="code-wrap:"]').click();assert code.locator('pre').get_attribute('class')==''
    test('Code wrap toggles locally without rerendering the page',codewrap)
    def copycode(pg):
        pg.evaluate("Object.defineProperty(navigator,'clipboard',{value:{writeText:async t=>{window.copiedCode=t}},configurable:true})")
        nav(pg,'markdown');click(pg,'code-copy:showcase-0');pg.wait_for_timeout(30)
        eq(pg,"window.copiedCode",pg.evaluate("AgentPrototype.codeRegistry.get('showcase-0')"))
        assert '<span' not in pg.evaluate('window.copiedCode')
    test('Code copy sends original text, never highlighted HTML (clipboard mocked)',copycode)
    def safe(pg):
        s=pg.evaluate("AgentPrototype.renderMarkdown('<img src=x onerror=alert(1)> [bad](javascript:alert) **good**')")
        assert '<img' not in s and 'href="javascript:' not in s and '<strong>good</strong>' in s,s
    test('Prototype Markdown escapes raw HTML and rejects unsafe link schemes',safe)
    def src(pg):
        nav(pg,'markdown');click(pg,'markdown-tab:source');assert pg.locator('.md-code').count()==1
        assert pg.locator('.md-code pre').inner_text()==pg.evaluate('AgentPrototype.markdownSample')
    test('Markdown source view matches raw sample exactly',src)
    def stream(pg):
        nav(pg,'markdown');pg.evaluate('AgentPrototype.animationLog.length=0');click(pg,'markdown-stream');pg.wait_for_timeout(430)
        assert pg.locator('#markdown-live').inner_text()
        assert not any(x['label'].startswith('route-') for x in pg.evaluate('AgentPrototype.animationLog'))
        click(pg,'markdown-stream');eq(pg,'AgentPrototype.state.streamingMarkdown',False)
    test('Stream demo progresses without replaying block/page animations',stream,motion=True)
    def transitions(pg):
        nav(pg,'chat');pg.wait_for_timeout(330);click(pg,'sheet:reasoning-quick');pg.wait_for_timeout(310);pg.keyboard.press('Escape');pg.wait_for_timeout(220)
        labels=[a['label'] for a in pg.evaluate('AgentPrototype.animationLog')]
        for l in ['route-enter','route-exit','overlay-enter','overlay-exit']:assert l in labels,labels
        assert pg.locator('.page-snapshot').count()==0
        eq(pg,"document.querySelector('#phone-content').inert",False)
    test('Navigation and popup enter/exit animate and clean inert snapshots',transitions,True)
    def reduced(pg):
        pg.emulate_media(reduced_motion='reduce');pg.evaluate('AgentPrototype.animationLog.length=0');nav(pg,'chat');click(pg,'sheet:reasoning-quick');pg.keyboard.press('Escape')
        assert len(pg.evaluate('AgentPrototype.animationLog'))==0
        eq(pg,"document.querySelector('#phone-content').inert",False)
    test('prefers-reduced-motion disables route/popup animations',reduced,True)
    def send(pg):
        click(pg,'suggest:整理这份项目说明，给我一份可执行的迭代计划。');click(pg,'send')
        eq(pg,'AgentPrototype.state.screen','running');click(pg,'sheet:approval');click(pg,'allow-command');eq(pg,'AgentPrototype.state.screen','chat')
    test('Existing send → tool approval → completed flow remains intact',send)
    def permission(pg):
        click(pg,'sheet:permissions');click(pg,'permission-draft:Auto');click(pg,'permission-save');eq(pg,'AgentPrototype.state.overlay','auto-confirm');click(pg,'auto-accept');eq(pg,'AgentPrototype.state.permission','Auto')
    test('Auto still requires second confirmation',permission)
    def compact(pg):
        nav(pg,'chat');click(pg,'sheet:context');click(pg,'sheet:compact');click(pg,'compact-start');click(pg,'compact-cancel');pg.wait_for_timeout(2500);eq(pg,'AgentPrototype.state.compacted',False)
    test('Cancelled compaction does not commit context changes',compact)
    def child(pg):
        nav(pg,'child');assert pg.locator('#message-input').count()==0;click(pg,'sheet:stop-child');click(pg,'stop-child');eq(pg,'AgentPrototype.state.childStopped',True)
    test('Read-only child and explicit stop still work',child)
    def attachment(pg):
        click(pg,'sheet:attach');click(pg,'add-attachment:reference.png');eq(pg,'AgentPrototype.state.attachments.length',1);click(pg,'sheet:attachment');click(pg,'remove-attachment');eq(pg,'AgentPrototype.state.attachments.length',0)
    test('Pending attachment addition/removal still work',attachment)
    browser.close()
(ROOT/'regression-test-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
for t in report['checks']:
    print(('PASS' if t['pass'] else 'FAIL')+' '+t['test']+((' '+t.get('error','')) if not t['pass'] else ''))
print('TOTAL',len(report['checks']),'PASS',sum(t['pass'] for t in report['checks']),'ERRORS',report['errors'])
raise SystemExit(1 if report['errors'] or any(not t['pass'] for t in report['checks']) else 0)
