/* AgentApp UI v3. Presentation prototype only. No model, shell, Git or filesystem execution. */
Object.assign(state, {
    toolsScenario: 'completed',
    toolFull: {},
    draftId: null,
    demoCreatedCount: 0,
});
Object.assign(motionTokens, {state:180,layout:240,tab:200,press:110});
const toolLab = ['tool-lab','26','工具记录 · 样本','设计系统','记录就在正文里。','不再用卡片套住整组工具。命令与编辑各用一行，按需展开 Shell 输出或逐行 Diff。','ui/chat/ChatActivityCards.kt + ui/files/','切换完成、运行、失败、空输出与缺少 Diff 样本；点击任意工具行。'];
screens.push(toolLab);screenMap['tool-lab']=toolLab;
screenMap.chat[5]='命令收起仅显示“运行了命令”；编辑显示文件名与增删数。无外层线框，详情直接内联。';
screenMap.chat[7]='直接点击“已编辑”或“运行了命令”；展开查看 Diff、命令及输出，不用先打开步骤卡片。';
screenMap.home[5]='正式应用冷启动进入新对话草稿。打开页面不落盘空会话，首次发送才创建。历史保留在侧栏。';
screenMap.home[7]='选择助手、模型或附件；首次发送创建演示会话。菜单里的历史会话仍可访问。';
const v3DiffBefore = [
    '    onDeleteFile: (String) -> Unit,',
    '    onDeleteSelected: (Set<String>) -> Unit = { paths -> paths.forEach(onDeleteFile) },',
    '    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },',
    '    onExportSelected: (Set<String>) -> Unit = {},'
].join('\n');
const v3DiffAfter = [
    '    onDeleteFile: (String) -> Unit,',
    '    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },',
    '    onDeleteSelected: (Set<String>) -> Unit = { paths -> paths.forEach(onDeleteFile) },',
    '    onExportSelected: (Set<String>) -> Unit = {},'
].join('\n');
const v3Fixtures = {
    read:{kind:'generic',name:'read_file',title:'读取了项目说明',path:'project-notes.md',args:{path:'project-notes.md'},result:toolFixtures.read.result,status:'completed'},
    child:{kind:'generic',name:'run_subagent',title:'委派了子代理',path:'梳理现有功能',args:{task:'阅读项目说明，梳理已经实现的功能。'},result:'已创建只读子会话 demo-child-01。',status:'completed'},
    plan:{kind:'edit',name:'edit_file',path:'iteration-plan.md',status:'completed',before:'统一所有页面的样式。',after:'先统一顶部栏与输入框。\n将工具记录收进可展开行。\n保留原有权限和目录边界。\n覆盖错误与停止状态。',startLine:6,language:'TEXT'},
    edit:{kind:'edit',name:'edit_file',path:'app/src/main/java/com/example/myapplication/ui/files/FilesScreen.kt',status:'completed',before:v3DiffBefore,after:v3DiffAfter,startLine:299,language:'KOTLIN'},
    command:{kind:'command',name:'run_command',status:'completed',command:'git diff --check\ngit diff --stat',result:' .../ui/files/FilesScreen.kt | 2 +-\n 1 file changed, 1 insertion(+), 1 deletion(-)',exitCode:0},
    planCommand:{kind:'command',name:'run_command',status:'completed',command:'pwd\nls -l iteration-plan.md',result:'/data/user/0/com.Ling.actant/files/workspace\n-rw------- 1 app app 312 iteration-plan.md',exitCode:0},
    pending:{kind:'command',name:'run_command',status:'pending',command:'pwd',result:null,exitCode:null},
    executing:{kind:'command',name:'run_command',status:'running',command:'git diff --check\ngit diff --stat',result:'正在读取工作区变更…',exitCode:null},
    failed:{kind:'command',name:'run_command',status:'failed',command:'git diff --check',result:'app/src/main/java/com/example/myapplication/ui/files/FilesScreen.kt:301: trailing whitespace.\n+    onExportSelected: (Set<String>) -> Unit = {},  ',exitCode:2},
    empty:{kind:'command',name:'run_command',status:'completed',command:'git diff --check',result:'',exitCode:0},
    cancelled:{kind:'command',name:'run_command',status:'cancelled',command:'git diff --check',result:'检查尚未完成，已收到取消请求。',exitCode:null},
    nodiff:{kind:'edit',name:'edit_file',path:'FilesScreen.kt',status:'completed',previewOmitted:true,before:null,after:null,result:'文件已修改；本次记录没有保存可用的变更预览。'},
};
const inFlightDetails = new WeakMap();
const motionNodeAnimations = new WeakMap();
const v3Runtime={lastRoute:null,lastTheme:null,renderEpoch:0,replaying:false,historySends:new Set()};

function action(name,label,cl='btn',ico='',disabled=false) {
    const trailing=ico==='down';
    return `<button class="${cl}${trailing?' with-chevron':''}" data-action="${esc(name)}" ${disabled?'disabled':''}>${ico&&!trailing?icon(ico):''}${label?`<span class="button-label">${label}</span>`:''}${trailing?icon(ico,'button-chevron'):''}</button>`;
}

/** Small LCS for bounded DEMO fixtures only. Native implementation uses FileChanges.diff. */
function demoDiff(before,after,start=1) {
    const a=before===''?[]:before.split('\n'), b=after===''?[]:after.split('\n');
    if(a.length*b.length>100000) return null;
    const dp=Array.from({length:a.length+1},()=>Array(b.length+1).fill(0));
    for(let i=a.length-1;i>=0;i--)for(let j=b.length-1;j>=0;j--)dp[i][j]=a[i]===b[j]?dp[i+1][j+1]+1:Math.max(dp[i+1][j],dp[i][j+1]);
    const lines=[];let i=0,j=0,add=0,remove=0;
    while(i<a.length||j<b.length){
        if(i<a.length&&j<b.length&&a[i]===b[j]){lines.push({kind:'same',old:start+i,new:start+j,text:a[i]});i++;j++;}
        else if(j<b.length&&(i===a.length||dp[i][j+1]>=dp[i+1][j])){lines.push({kind:'add',old:null,new:start+j,text:b[j]});j++;add++;}
        else{lines.push({kind:'remove',old:start+i,new:null,text:a[i]});i++;remove++;}
    }
    return {lines,add,remove};
}
function toolStateLabel(t) {
    if(t.kind==='command')return ({completed:'运行了命令',running:'正在运行命令',pending:'等待命令批准',failed:'命令执行失败',cancelled:'命令已中止'})[t.status]||'命令尚未完成';
    return ({completed:'已编辑',running:'正在编辑',pending:'等待编辑批准',failed:'编辑失败',cancelled:'编辑已中止'})[t.status]||'文件编辑';
}
function toolKey(id,context='sample') {return `${context}::assistant-01::demo-call-${id}`;}
function diffStats(d) {return d?`<span class="inline-diff-stats"><span class="diff-plus">+${d.add}</span><span class="diff-minus">−${d.remove}</span></span>`:'';}
function shellPanel(t,id) {
    const codeId='shell-'+id,full=state.toolFull[id]||false,wrap=state.codeWrap[codeId]||false;
    const command=t.command||'', output=t.result;
    const whole='$ '+command.split('\n').join('\n$ ')+(output===null?'':output.length?'\n\n'+output:'');
    codeRegistry.set(codeId,whole);
    return `<section class="tool-shell ${full?'is-full':''}" data-code-id="${codeId}"><header class="tool-panel-header"><span class="tool-panel-title">Shell</span><span class="stretch"></span><button class="code-action" data-action="code-wrap:${codeId}" aria-label="切换命令输出换行" aria-pressed="${wrap}">${icon('wrap')}<span>${wrap?'不换行':'换行'}</span></button><button class="code-action icon-only" data-action="code-copy:${codeId}" aria-label="复制命令与输出">${icon(state.codeCopied[codeId]?'check':'copy')}</button></header><pre class="shell-scroll ${wrap?'code-wrapped':''}" tabindex="0" aria-label="命令及本次输出，支持横向和纵向滚动"><code><span class="shell-command">${esc('$ '+command.split('\n').join('\n$ '))}</span>${output===null?'':output.length?'\n\n<span class="shell-output">'+esc(output)+'</span>':'\n\n<span class="shell-empty">（本次命令未产生输出）</span>'}</code></pre>${output===null?'<div class="tool-waiting">尚未执行，暂无输出。批准后才会运行。</div>':''}<footer class="tool-panel-footer"><span>${t.exitCode!==null&&t.exitCode!==undefined?'退出码 '+t.exitCode:({running:'执行中 · 输出持续更新',pending:'等待审批',cancelled:'已中止 · 保留已有输出'})[t.status]||'退出码未报告'}</span><button data-action="tool-full:${id}" class="text-btn compact-action">${full?'限制高度':'展开高度'}${icon('down')}</button></footer></section>${t.status==='pending'?action('sheet:approval','审阅执行权限','text-btn','shield'):''}`;
}
function diffPanel(t,id,d) {
    if(!d)return `<div class="inline-tool-note"><strong>${esc(t.path)}</strong><p>文件已修改，但本次记录未保存可用 Diff。不能根据当前文件倒推或虚构增删行。</p>${t.result?`<p>${esc(t.result)}</p>`:''}</div>`;
    const codeId='diff-'+id,wrap=state.codeWrap[codeId]||false;
    const patch=`--- a/${t.path}\n+++ b/${t.path}\n@@ -${t.startLine||1},${t.before.split('\n').length} +${t.startLine||1},${t.after.split('\n').length} @@\n`+d.lines.map(l=>(l.kind==='add'?'+':l.kind==='remove'?'-':' ')+l.text).join('\n');
    codeRegistry.set(codeId,patch);
    const basename=t.path.split('/').at(-1);
    return `<section class="inline-diff ${state.toolFull[id]?'is-full':''}" data-code-id="${codeId}"><header class="tool-panel-header"><span class="tool-panel-title ellipsis" title="${esc(t.path)}">${esc(basename)}</span>${diffStats(d)}<span class="stretch"></span><button class="code-action icon-only" data-action="code-wrap:${codeId}" aria-label="切换 Diff 自动换行" aria-pressed="${wrap}">${icon('wrap')}</button><button class="code-action icon-only" data-action="code-copy:${codeId}" aria-label="复制文件变更补丁">${icon(state.codeCopied[codeId]?'check':'copy')}</button></header><div class="diff-scroll ${wrap?'code-wrapped':''}" tabindex="0" aria-label="${esc(basename)} 已执行的文件变更"><div class="diff-lines">${d.lines.map(l=>`<div class="inline-diff-line ${l.kind}" aria-label="${l.kind==='add'?'新增':l.kind==='remove'?'删除':'上下文'} ${l.new??l.old} 行"><span class="diff-lineno" title="${l.old===null?'新增行 '+l.new:l.new===null?'原行 '+l.old:'原行 '+l.old+' / 新行 '+l.new}">${l.new??l.old}</span><span class="diff-sign">${l.kind==='add'?'+':l.kind==='remove'?'−':' '}</span><code>${highlightCode(l.text,t.language||'TEXT')}</code></div>`).join('')}</div></div><footer class="tool-panel-footer"><span>已执行的变更 · 不是待应用补丁</span><button data-action="tool-full:${id}" class="text-btn compact-action">${state.toolFull[id]?'限制高度':'展开高度'}${icon('down')}</button></footer></section>`;
}
function toolItem(id,running=false,context='chat') {
    const t=v3Fixtures[id];if(!t)return '';
    const key=toolKey(id,context),open=!!state.detailOpen[key];
    const isEdit=t.kind==='edit',isCommand=t.kind==='command';
    const d=isEdit&&typeof t.before==='string'&&typeof t.after==='string'?demoDiff(t.before,t.after,t.startLine||1):null;
    const ico=isEdit?'edit':isCommand?'terminal':id==='child'?'tree':'file';
    let summary;
    if(isEdit)summary=`<span class="tool-caption" data-title-closed="${toolStateLabel(t)}" data-title-open="已编辑的文件">${open?'已编辑的文件':toolStateLabel(t)}</span><span class="tool-collapsed-meta"><span class="edited-filename" title="${esc(t.path)}">${esc(t.path.split('/').at(-1))}</span>${diffStats(d)}</span>`;
    else summary=`<span class="tool-caption">${isCommand?toolStateLabel(t):t.title}</span>`;
    const body=isCommand?shellPanel(t,id):isEdit?diffPanel(t,id,d):`<div class="generic-tool-body"><p class="inline-tool-path">${esc(t.path)}</p>${codeBlock(JSON.stringify(t.args,null,4),'JSON','args-'+id,'调用参数')}${codeBlock(t.result||'暂无返回','TEXT','result-'+id,'工具返回')}${id==='child'?action('go:child','查看只读子会话','text-btn','tree'):''}</div>`;
    return `<details class="tool-inline" data-detail-key="${key}" data-tool-id="${id}" data-kind="${t.kind}" data-status="${t.status}" ${open?'open':''}><summary aria-expanded="${open}">${t.status==='running'?'<span class="spinner"></span>':icon(ico,'tool-kind-icon')}${summary}${icon('chevron','tool-chevron')}</summary><div class="tool-inline-body">${body}</div></details>`;
}
function activity(running=false) {
    const keys=running?['read','child','pending']:['read','plan','planCommand'];
    return `<div class="tool-flow" aria-label="本次工具调用">${keys.map(k=>toolItem(k,running,'chat')).join('')}</div>`;
}
function child() {
    let html=childV1();
    html=html.replace(/<details class="activity" open>[\s\S]*?<\/details>/,`<div class="tool-flow">${toolItem('read',false,'child')}</div>`);
    return html;
}
function toolLabPage() {
    const scenarios=[['completed','已完成'],['running','执行中'],['pending','待批准'],['failed','失败'],['empty','无输出'],['cancelled','中止'],['nodiff','无 Diff']];
    const selected=state.toolsScenario;
    const body=selected==='completed'?toolItem('edit',false,'lab')+toolItem('command',false,'lab'):toolItem(({running:'executing'})[selected]||selected,false,'lab');
    return titlebar('工具记录','go:chat',ib('motion-replay','重播工具交互','refresh'),'轻量内联 · 演示数据')+`<div class="content chat-scroll tools-lab"><div class="tool-scenario-tabs" role="tablist" aria-label="工具状态样本">${scenarios.map(([id,name])=>`<button role="tab" aria-selected="${selected===id}" class="${selected===id?'active':''}" data-action="tool-scenario:${id}">${name}</button>`).join('')}</div><div class="user-message">把文件页面的参数顺序整理一下，再检查改动有没有格式问题。</div><div class="assistant-message markdown-chat"><p>${selected==='completed'?'已调整参数顺序，保留原有回调逻辑。下面是这次改动和命令记录。':selected==='running'?'文件检查正在执行。可以展开查看目前返回的内容。':selected==='pending'?'这条命令尚未执行。批准前不会显示成功结果。':selected==='failed'?'检查返回了错误。展开记录查看具体位置和原始输出。':selected==='empty'?'这次检查没有产生输出。没有输出和没有执行是两种状态。':selected==='cancelled'?'命令已停止，已产生的记录仍然保留。':'本次记录缺少变更预览，因此不显示虚构的增删计数。'}</p><div class="tool-flow" data-ui-key="lab-flow">${body}</div><p class="tool-closing">${selected==='completed'?'这次只调整参数位置，没有更改文件操作行为。实机检查仍由你完成。':'工具结果保留在原位置；查看详情不会切换页面。'}</p><div class="message-actions">${ib('copy','复制回答','copy')}<span class="meta">演示样本 · 未执行任何命令</span></div></div></div>`+composer(selected==='running');
}
function page() {return state.screen==='tool-lab'?toolLabPage():pageV2();}

/* Incremental keyed DOM patching keeps controls, scroll positions and focus stable.
   The Android implementation should use stateful Compose components, not this web code. */
function nodeKey(n) {
    if(n.nodeType!==1)return null;
    for(const k of ['data-ui-key','data-detail-key','id','data-field','data-search','data-action'])if(n.hasAttribute(k))return k+':'+n.getAttribute(k);
    return null;
}
function prepareMarkup(root) {
    root.querySelectorAll('.provider-card').forEach(card=>card.dataset.uiKey='provider:'+(card.querySelector('input')?.dataset.providerSelect||card.querySelector('input')?.getAttribute('value')||card.querySelector('.provider-main')?.dataset.action.split(':').at(-1)));
    root.querySelectorAll('details').forEach((d,i)=>{
        if(!d.dataset.detailKey)d.dataset.detailKey=`${state.overlay||state.screen}:details:${(d.querySelector('summary .stretch')?.textContent||d.querySelector('summary')?.textContent||i).trim().slice(0,65)}`;
        if(d.dataset.detailKey in state.detailOpen)d.open=state.detailOpen[d.dataset.detailKey];
        d.querySelector(':scope > summary')?.setAttribute('aria-expanded',String(d.open));
    });
}
function patchNode(old,next,changes) {
    if(old.nodeType===3){if(old.data!==next.data){changes.text.add(old.parentElement);old.data=next.data;}return;}
    if(old.nodeType!==1)return;
    for(const a of [...old.attributes])if(!next.hasAttribute(a.name)&&!['data-animating'].includes(a.name))old.removeAttribute(a.name);
    for(const a of [...next.attributes])if(old.getAttribute(a.name)!==a.value){old.setAttribute(a.name,a.value);changes.attrs.add(old);}
    if(old instanceof HTMLInputElement){if(old.type!=='file'&&old.value!==next.value&&old!==document.activeElement)old.value=next.value;old.checked=next.checked;}
    if(old instanceof HTMLTextAreaElement){if(old!==document.activeElement)old.value=next.value;return;}
    patchChildren(old,next,changes);
    if(old instanceof HTMLSelectElement&&old!==document.activeElement)old.value=next.value;
}
function patchChildren(parent,nextParent,changes) {
    const olds=[...parent.childNodes].filter(n=>!(n.nodeType===1&&n.classList.contains('feedback-lane')));
    const used=new Set();let cursor=parent.firstChild;
    for(const next of [...nextParent.childNodes]){
        while(cursor?.nodeType===1&&cursor.classList.contains('feedback-lane'))cursor=cursor.nextSibling;
        const key=nodeKey(next);
        let old=key?olds.find(n=>!used.has(n)&&nodeKey(n)===key):null;
        if(!old&&!key&&cursor&&!used.has(cursor)&&!nodeKey(cursor)&&cursor.nodeType===next.nodeType&&cursor.nodeName===next.nodeName)old=cursor;
        if(!old&&!key)old=olds.find(n=>!used.has(n)&&!nodeKey(n)&&n.nodeType===next.nodeType&&n.nodeName===next.nodeName);
        if(old){used.add(old);if(old!==cursor)parent.insertBefore(old,cursor);patchNode(old,next,changes);cursor=old.nextSibling;}
        else{const n=next.cloneNode(true);parent.insertBefore(n,cursor);changes.added.add(n);cursor=n.nextSibling;}
    }
    for(const old of olds)if(!used.has(old)){if(old.nodeType===1)changes.removed.push({node:old.cloneNode(true),rect:old.getBoundingClientRect()});old.remove();}
}
const motionSelector='.provider-card,.list-row,.list-card,.field,.notice,.section-title,.sticky-actions,.selection-toolbar,.attachment-strip,.attachment-mini,.composer,.task-status,.tabs,.tool-scenario-tabs,.md-code,.inline-diff,.tool-shell,.run-card,.option-row,.model-option,.effort-option';
function captureGeometry(root) {
    const map=new Map();root.querySelectorAll(motionSelector).forEach(n=>{const r=n.getBoundingClientRect();if(r.width&&r.height)map.set(n,r);});return map;
}
function animateChanged(root,changes,oldRects,label='state') {
    if(!canAnimate())return;
    const added=[...changes.added].filter(n=>n.nodeType===1&&n.isConnected&&!n.closest('[inert]'));
    for(const n of added){if(added.some(p=>p!==n&&p.contains(n)))continue;if(n.matches('input,textarea,option,svg,path')||n.closest('pre,.diff-lines'))continue;animateElement(n,[{opacity:0,transform:'translateY(4px)'},{opacity:1,transform:'translateY(0)'}],motionTokens.state,label+'-enter');}
    for(const [n,before] of oldRects){if(!n.isConnected)continue;const after=n.getBoundingClientRect();const dx=before.left-after.left,dy=before.top-after.top;if(Math.abs(dx)+Math.abs(dy)<1||Math.abs(dy)>650)continue;
        motionNodeAnimations.get(n)?.cancel();const a=animateElement(n,[{transform:`translate(${dx}px,${dy}px)`},{transform:'translate(0,0)'}],motionTokens.layout,label+'-layout');if(a)motionNodeAnimations.set(n,a);
    }
    const flashes=new Set();
    for(const n of changes.text){const target=n?.closest('.button-label,.tool-caption,.default-caption,.tag,.status-banner,.task-status,.feedback-text,.context-headline,.context-percent,.legend-row,.fine,.page-intro p');if(target?.isConnected&&!target.closest('pre,.diff-lines,#markdown-live'))flashes.add(target);}
    for(const n of flashes)animateElement(n,[{opacity:.3},{opacity:1}],motionTokens.state,label+'-label');
    for(const change of changes.removed){const {node,rect}=change;if(!node.matches?.('.provider-card,.list-row,.attachment-mini,.selection-toolbar,.notice')||!rect.width||!rect.height)continue;const phone=document.getElementById('phone'),p=phone.getBoundingClientRect();Object.assign(node.style,{position:'absolute',left:rect.left-p.left+'px',top:rect.top-p.top+'px',width:rect.width+'px',height:rect.height+'px',margin:'0',pointerEvents:'none',zIndex:'4'});node.classList.add('state-ghost');node.inert=true;node.setAttribute('aria-hidden','true');node.querySelectorAll('[id]').forEach(el=>el.removeAttribute('id'));phone.append(node);const a=animateElement(node,[{opacity:.6},{opacity:0,transform:'translateY(-4px)'}],motionTokens.exit,label+'-exit');if(a)a.onfinish=()=>node.remove();else node.remove();}
}
function applyMarkup(root,html,animate=true) {
    const t=document.createElement('template');t.innerHTML=html;prepareMarkup(t.content);
    const oldRects=captureGeometry(root);const changes={added:new Set(),text:new Set(),attrs:new Set(),removed:[]};
    patchChildren(root,t.content,changes);
    if(animate)animateChanged(root,changes,oldRects);
    return changes;
}
function render(keepScroll=true) {
    const content=document.getElementById('phone-content');
    const changed=!!v3Runtime.lastRoute&&v3Runtime.lastRoute!==state.screen;
    const themeChange=!!v3Runtime.lastTheme&&v3Runtime.lastTheme!==state.theme;
    const oldScroll=content.querySelector('.content')?.scrollTop||0;
    let ghost=null;
    document.querySelectorAll('.page-snapshot').forEach(n=>n.remove());
    if(changed&&canAnimate()){
        ghost=content.cloneNode(true);ghost.removeAttribute('id');ghost.classList.add('page-snapshot');ghost.inert=true;ghost.setAttribute('aria-hidden','true');ghost.querySelectorAll('[id]').forEach(n=>n.removeAttribute('id'));ghost.querySelector('.feedback-lane')?.remove();document.getElementById('phone').append(ghost);const scroll=ghost.querySelector('.content');if(scroll)scroll.scrollTop=oldScroll;
    }
    document.documentElement.dataset.theme=state.theme;document.getElementById('phone').dataset.theme=state.theme;
    if(changed||!content.childNodes.length){const t=document.createElement('template');t.innerHTML=page();prepareMarkup(t.content);content.replaceChildren(t.content);}
    else applyMarkup(content,page(),!themeChange&&!state.streamingMarkdown);
    if(changed||!keepScroll){const sc=content.querySelector('.content');if(sc)sc.scrollTop=0;}
    v3Runtime.lastRoute=state.screen;v3Runtime.lastTheme=state.theme;lastRoute=state.screen;lastTheme=state.theme;
    renderOverlay();renderReview();renderFeedback();
    if(changed){
        content.getAnimations().forEach(a=>a.cancel());
        animateElement(content,[{opacity:0,transform:`translateX(${motionDirection*18}px)`},{opacity:1,transform:'translateX(0)'}],motionTokens.route,'route-enter');
        if(ghost){const a=animateElement(ghost,[{opacity:1,transform:'translateX(0)'},{opacity:0,transform:`translateX(${-motionDirection*10}px)`}],motionTokens.exit,'route-exit');if(a)a.onfinish=()=>ghost.remove();else ghost.remove();}
    }
    if(themeChange&&canAnimate())animationLog.push({label:'theme-color',duration:motionTokens.theme,at:Math.round(performance.now())});
    document.title=screenMap[state.screen][2]+' · AgentApp UI 原型 v3';
}
function renderOverlay() {
    const host=document.getElementById('overlay'),next=state.overlay,previous=overlayRendered;
    let dialog=host.querySelector('[role=dialog]');
    if(next===null&&host.classList.contains('closing'))return;
    if(next===null&&dialog){
        const epoch=++overlayEpoch;overlayRendered=null;host.classList.add('closing');
        const a=animateElement(dialog,[{opacity:1,transform:'translate(0,0)'},{opacity:0,transform:dialog.classList.contains('drawer')?'translateX(-24px)':'translateY(22px)'}],motionTokens.exit,'overlay-exit');
        animateElement(host.querySelector('.overlay-scrim'),[{opacity:1},{opacity:0}],motionTokens.exit,'scrim-exit');
        const finish=()=>{if(epoch!==overlayEpoch)return;host.replaceChildren();host.classList.remove('closing');document.getElementById('phone-content').inert=false;restoreFocus();renderFeedback();};
        if(a)a.onfinish=finish;else finish();return;
    }
    if(next===null){host.replaceChildren();overlayRendered=null;document.getElementById('phone-content').inert=false;return;}
    if(previous===next&&!host.classList.contains('closing')){applyMarkup(host,overlay());renderFeedback();return;}
    ++overlayEpoch;host.classList.remove('closing');
    let ghost=null;
    if(dialog&&canAnimate()){
        const r=dialog.getBoundingClientRect(),h=host.getBoundingClientRect();ghost=dialog.cloneNode(true);ghost.removeAttribute('id');ghost.removeAttribute('role');ghost.inert=true;ghost.setAttribute('aria-hidden','true');ghost.classList.add('overlay-snapshot');ghost.querySelectorAll('[id]').forEach(n=>n.removeAttribute('id'));
        Object.assign(ghost.style,{position:'absolute',top:(r.top-h.top)+'px',left:(r.left-h.left)+'px',bottom:'auto',right:'auto',width:r.width+'px',height:r.height+'px',maxHeight:'none',pointerEvents:'none',zIndex:'3'});
    }
    const t=document.createElement('template');t.innerHTML=overlay();prepareMarkup(t.content);host.replaceChildren(t.content);overlayRendered=next;document.getElementById('phone-content').inert=true;
    dialog=host.querySelector('[role=dialog]');
    const frames=dialog?.classList.contains('drawer')?[{opacity:.3,transform:'translateX(-30px)'},{opacity:1,transform:'translateX(0)'}]:dialog?.classList.contains('reasoning-popover')?[{opacity:0,transform:'translateY(10px) scale(.98)'},{opacity:1,transform:'translateY(0) scale(1)'}]:[{opacity:0,transform:'translateY(32px)'},{opacity:1,transform:'translateY(0)'}];
    animateElement(dialog,frames,motionTokens.sheet,'overlay-enter');
    if(ghost){host.append(ghost);const a=animateElement(ghost,[{opacity:1},{opacity:0,transform:'translateY(8px)'}],motionTokens.exit,'overlay-replace-exit');if(a)a.onfinish=()=>ghost.remove();else ghost.remove();}
    else animateElement(host.querySelector('.overlay-scrim'),[{opacity:0},{opacity:1}],motionTokens.banner,'scrim-enter');
    renderFeedback();dialog?.focus({preventScroll:true});
}
function syncToolTitle(detail,open) {
    const summary=detail.querySelector(':scope > summary');summary?.setAttribute('aria-expanded',String(open));
    const title=summary?.querySelector('[data-title-open]');
    if(title){const next=open?title.dataset.titleOpen:title.dataset.titleClosed;if(title.textContent!==next){title.textContent=next;animateElement(title,[{opacity:.3},{opacity:1}],motionTokens.state,'tool-title');}}
    const meta=summary?.querySelector('.tool-collapsed-meta');if(meta){meta.hidden=open;}
}
function mutateDetails(detail,open) {
    if(!detail)return;
    if(detail.dataset.detailKey)state.detailOpen[detail.dataset.detailKey]=open;
    const before=detail.getBoundingClientRect().height;
    inFlightDetails.get(detail)?.cancel();detail.style.height='';detail.style.overflow='';
    detail.open=true;detail.dataset.expanded=String(open);syncToolTitle(detail,open);
    const summary=detail.querySelector(':scope > summary');const target=open?detail.scrollHeight:summary.getBoundingClientRect().height;
    if(!canAnimate()){detail.open=open;return;}
    detail.style.overflow='hidden';
    const a=animateElement(detail,[{height:before+'px'},{height:target+'px'}],motionTokens.expand,'inline-expand');
    if(a){inFlightDetails.set(detail,a);a.onfinish=()=>{if(inFlightDetails.get(detail)!==a)return;detail.open=open;detail.style.height='';detail.style.overflow='';inFlightDetails.delete(detail);};}
    else detail.open=open;
}
function renderFeedback(animate=false) {
    document.getElementById('toast')?.remove();
    if(!state.banner){document.querySelectorAll('.feedback-lane:not(.feedback-exiting)').forEach(n=>n.remove());return;}
    const dialog=state.overlay?document.querySelector('#overlay [role=dialog]'):null;
    const header=dialog?.querySelector('.sheet-header,.drawer-header,.reasoning-top')||document.querySelector('#phone-content .topbar');if(!header)return;
    let lane=document.querySelector('.feedback-lane:not(.feedback-exiting)');const added=!lane;
    if(!lane){lane=document.createElement('div');lane.className='feedback-lane';lane.addEventListener('mouseenter',()=>clearTimeout(toastTimer));lane.addEventListener('focusin',()=>clearTimeout(toastTimer));lane.addEventListener('mouseleave',scheduleBanner);lane.addEventListener('focusout',scheduleBanner);}
    const html=`<div class="top-feedback ${state.banner.type||'info'}"><span class="feedback-icon">${icon(state.banner.type==='error'?'info':'check')}</span><span class="stretch feedback-text" role="${state.banner.type==='error'?'alert':'status'}" aria-live="${state.banner.type==='error'?'assertive':'polite'}">${esc(state.banner.text)}</span>${ib('banner-close','关闭消息提示','close')}</div>`;
    if(lane.innerHTML!==html)lane.innerHTML=html;
    if(lane.previousElementSibling!==header)header.after(lane);
    if(animate&&added){const height=lane.getBoundingClientRect().height;lane.style.overflow='hidden';const a=animateElement(lane,[{height:'0px',opacity:0,transform:'translateY(-6px)'},{height:height+'px',opacity:1,transform:'translateY(0)'}],motionTokens.banner,'top-banner');if(a)a.onfinish=()=>{lane.style.overflow='';};}
    else if(animate)animateElement(lane.querySelector('.feedback-text'),[{opacity:.4},{opacity:1}],motionTokens.state,'banner-update');
}
function dismissBanner() {
    clearTimeout(toastTimer);state.banner=null;
    const lane=document.querySelector('.feedback-lane');if(!lane)return;lane.classList.add('feedback-exiting');lane.style.overflow='hidden';
    lane.getAnimations().forEach(a=>a.cancel());const a=animateElement(lane,[{opacity:1,height:lane.getBoundingClientRect().height+'px'},{opacity:0,height:'0px'}],motionTokens.exit,'banner-exit');if(a)a.onfinish=()=>lane.remove();else lane.remove();
}
function renderReview() {
    renderReviewV2();
    const info=document.getElementById('review-info');info.innerHTML=info.innerHTML.replace('DESIGN V2 /','DESIGN V3 /').replace('25 个页面','26 个页面').replace('工具详情在原位置展开','工具与 Diff 在原位置展开');
    info.insertAdjacentHTML('beforeend',`<div class="rule"></div><div class="eyebrow">V3 / LIGHTWEIGHT FLOW</div><p>工具收起是一行，展开才出现输出底板。命令、Diff 不再套步骤卡片。</p><div class="column">${action('go:tool-lab','查看工具记录样本','review-btn')}${action('new-chat','模拟回到新对话','review-btn')}</div><p class="fine" style="color:#817B6C">冷启动：新对话；历史：侧栏。<br>本轮创建的演示会话：${state.demoCreatedCount} 条。</p>`);
}
function navigate(route,push=true) {
    if(!screenMap[route])return;
    if(state.overlay==='compacting'){toast('请先等待或取消上下文压缩。');return;}
    if(state.streamingMarkdown){clearInterval(markdownTimer);state.streamingMarkdown=false;}
    motionDirection=push?1:-1;
    if(push&&route!==state.screen)state.stack.push(state.screen);
    if(route!==state.screen)state.banner=null;
    state.screen=route;state.overlay=null;state.query='';state.manage=false;state.selected.clear();state.permissionDraft='';state.forms.folder=false;
    render(false);
}
function animateRegionSize(el,mutate,label) {
    if(!el){mutate();return;}
    const h=el.getBoundingClientRect().height;el.getAnimations().forEach(a=>a.cancel());el.style.height='';mutate();
    const to=el.getBoundingClientRect().height;
    if(canAnimate()&&Math.abs(to-h)>.5){el.style.overflow='hidden';const a=animateElement(el,[{height:h+'px'},{height:to+'px'}],motionTokens.layout,label);if(a)a.onfinish=()=>{el.style.height='';el.style.overflow='';};}
}
function runMarkdownStream(){return runMarkdownStreamV2();}
function replayMotion() {
    replayTimers.forEach(clearTimeout);replayTimers=[];v3Runtime.replaying=true;
    state.banner=null;state.overlay=null;state.toolsScenario='completed';
    state.detailOpen[toolKey('command','lab')]=false;state.detailOpen[toolKey('edit','lab')]=false;
    navigate('home');
    const later=(ms,fn)=>replayTimers.push(setTimeout(fn,ms));
    later(650,()=>navigate('tool-lab'));
    later(1350,()=>mutateDetails(document.querySelector('[data-tool-id="command"]'),true));
    later(1700,()=>document.querySelector('.tools-lab')?.scrollTo({top:100,behavior:canAnimate()?'smooth':'auto'}));
    later(3150,()=>mutateDetails(document.querySelector('[data-tool-id="command"]'),false));
    later(3700,()=>mutateDetails(document.querySelector('[data-tool-id="edit"]'),true));
    later(4050,()=>document.querySelector('.tools-lab')?.scrollTo({top:90,behavior:canAnimate()?'smooth':'auto'}));
    later(5600,()=>mutateDetails(document.querySelector('[data-tool-id="edit"]'),false));
    later(6100,()=>show('reasoning-quick'));
    later(7150,()=>show('reasoning'));
    later(8300,()=>close());
    later(8850,()=>{v3Runtime.replaying=false;toast('轻量工具行、原地展开与状态转场演示完成。');});
}
function handle(act) {
    const [verb,...parts]=act.split(':');const value=parts.join(':');
    if(verb==='tool-scenario'){state.toolsScenario=value;render();return;}
    if(verb==='tool-full'){
        const el=document.querySelector(`[data-tool-id="${CSS.escape(value)}"] .tool-shell,[data-tool-id="${CSS.escape(value)}"] .inline-diff`);
        animateRegionSize(el,()=>{state.toolFull[value]=!state.toolFull[value];el?.classList.toggle('is-full',state.toolFull[value]);const b=el?.querySelector('[data-action^="tool-full:"]');if(b)b.innerHTML=(state.toolFull[value]?'限制高度':'展开高度')+icon('down');},'output-height');return;
    }
    if(verb==='code-wrap'){
        const el=document.querySelector(`[data-code-id="${CSS.escape(value)}"]`);
        animateRegionSize(el,()=>{
            state.codeWrap[value]=!(state.codeWrap[value]??false);
            el?.querySelector('pre,.diff-scroll')?.classList.toggle('code-wrapped',state.codeWrap[value]);
            const button=el?.querySelector('[data-action^="code-wrap:"]');if(button){button.setAttribute('aria-pressed',String(state.codeWrap[value]));if(!button.classList.contains('icon-only'))button.innerHTML=icon('wrap')+'<span>'+(state.codeWrap[value]?'不换行':'换行')+'</span>';}
        },'code-wrap');return;
    }
    if(verb==='use-agent'){state.draftId=null;state.draft='';state.attachments=[];state.agent=value;navigate('home');return;}
    if(act==='new-chat'){
        state.draftId=null;state.draft='';state.attachments=[];state.runStopped=false;state.messageDeleted=false;state.agent='通用助手';state.effortValue=null;state.reasoning='跟随配置';
        const p=providers.find(p=>p.id===state.selectedProviderId);if(p)state.model=p.model;
        navigate('home');return;
    }
    if(act==='send'){
        if(state.screen==='running'&&!state.runStopped)return;
        if(!state.draft.trim()&&!state.attachments.length)return;
        if(state.screen==='home'&&!state.draftId){state.draftId='local-demo-'+Date.now();state.demoCreatedCount++;conversations.unshift({id:state.draftId,name:state.draft.slice(0,24)||'附件对话',description:'本轮原型中创建 · 仅演示',date:'今天',model:state.model});}
        handleV2(act);return;
    }
    if(act==='motion-toggle'){
        state.motion=!state.motion;
        if(!canAnimate())document.getAnimations().forEach(a=>{if(a.effect?.getTiming().iterations===Infinity)a.cancel();else try{a.finish();}catch{a.cancel();}});
        document.documentElement.dataset.motion=canAnimate()?'on':'off';renderReview();return;
    }
    if(act==='stop'&&state.screen==='tool-lab'){state.toolsScenario='cancelled';render();return;}
    handleV2(act);
}
/* Local label changes that don't call render, such as code copy, still get a small transition. */
const labelObserver=new MutationObserver(records=>{
    if(!canAnimate())return;
    const buttons=new Set();for(const r of records){const el=r.target.nodeType===1?r.target:r.target.parentElement;const b=el?.closest?.('[data-action^="code-copy:"]');if(b)buttons.add(b);}
    for(const b of buttons)animateElement(b,[{opacity:.3},{opacity:1}],motionTokens.state,'copy-feedback');
});
labelObserver.observe(document.getElementById('phone'),{childList:true,subtree:true,characterData:true});
// With the finger down, range values track the gesture directly, not through delayed tweens.
document.addEventListener('input',e=>{if(e.target.id==='message-input'){const el=e.target;const old=el.offsetHeight;el.style.height='auto';const h=Math.max(44,Math.min(120,el.scrollHeight));el.style.height=h+'px';if(Math.abs(old-h)>2)animateElement(el,[{height:old+'px'},{height:h+'px'}],motionTokens.state,'composer-height');}});
window.AgentPrototype={state,screens,sheets,navigate,show,close,render,handle,contextSegments,wireField,activeModel,animationLog,markdownSample,renderMarkdown,codeRegistry,replayMotion,mutateDetails,demoDiff,fixtures:v3Fixtures,toolKey,canAnimate};
state.screen=screenMap[qs.get('screen')]?qs.get('screen'):'home';
render(false);
if(qs.get('sheet'))show(qs.get('sheet'));
