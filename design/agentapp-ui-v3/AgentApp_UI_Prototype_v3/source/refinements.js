/* AgentApp UI v2 · behavioural refinements. Demo data only, no network or device access. */
Object.assign(icons, {
    pdf: '<path d="M14 2H6a2 2 0 0 0-2 2v16h16V8ZM14 2v6h6M7 12h10M7 16h7"/>',
    audio: '<path d="M9 17V5l11-2v12M9 8l11-2"/><ellipse cx="6" cy="17" rx="3" ry="3"/><ellipse cx="17" cy="15" rx="3" ry="3"/>',
    video: '<rect x="2" y="5" width="14" height="14" rx="3"/><path d="m16 10 6-4v12l-6-4"/>',
    text: '<path d="M4 5h16M12 5v15M8 20h8"/>',
    wrap: '<path d="M3 6h18M3 11h14a4 4 0 0 1 0 8h-4m3-3-3 3 3 3M3 16h5"/>',
    expand: '<path d="M4 9V4h5m6 0h5v5M4 15v5h5m6 0h5v-5"/>',
});
Object.assign(state, {
    effortValue: null,
    effortDraft: null,
    banner: null,
    toolOpen: {},
    codeWrap: {},
    codeCopied: {},
    detailOpen: {},
    motion: qs.get('motion') !== 'off',
    markdownTab: 'preview',
    streamingMarkdown: false,
    streamingText: '',
});
providers[0].capabilities = {image:true,pdf:true,audio:false,video:false};
providers[0].capabilitySource = 'discovered';
providers[0].modelDefault = 'medium';
providers[0].efforts = ['none','low','medium','high','xhigh','max'];
providers[0].reasoningProtocol = 'ANTHROPIC_ADAPTIVE';
providers[1].capabilities = {image:true,pdf:false,audio:true,video:false};
providers[1].capabilitySource = 'override';
providers[1].modelDefault = null;
providers[1].efforts = ['none','low','medium','high','xhigh','max'];
providers[1].reasoningProtocol = 'OPENAI_CHAT_COMPLETIONS';
providers[2].capabilities = {image:true,pdf:true,audio:true,video:true};
providers[2].capabilitySource = 'discovered';
providers[2].modelDefault = 'medium';
providers[2].efforts = ['minimal','low','medium','high'];
providers[2].reasoningProtocol = 'GEMINI_THINKING_LEVEL';
const effortLabels = {none:'关闭',minimal:'极低',low:'低',medium:'中',high:'高',xhigh:'很高',max:'最大'};
const contextColors = {system:'#6B91CA',tools:'#9C82C6',environment:'#8995A5',user:'#50A78F',assistant:'#D3A05B',results:'#CF7F8B',attachments:'#5BA9BD',summary:'#A0AF70'};
const contextDefs = [
    ['system','系统提示',2200,2200],['tools','工具定义',2000,2000],
    ['environment','环境信息',980,980],['user','用户消息',6400,1000],
    ['assistant','助手消息',15800,3000],['results','工具结果',6380,860],
    ['attachments','附件',520,520],['summary','压缩摘要',0,1840]
];
const motionTokens = {press:110,route:260,exit:180,sheet:280,drawer:260,expand:240,banner:200,theme:180};
const reduceMedia = matchMedia('(prefers-reduced-motion: reduce)');
let overlayEpoch = 0;
let overlayRendered = null;
let motionDirection = 1;
let lastTheme = '';
let markdownTimer = null;
let replayTimers = [];
const animationLog = [];
const codeRegistry = new Map();
const extraScreen = ['markdown','25','Markdown 排版','设计系统','把长内容排得更舒服。','同一套阅读层级用于回答、文件、计划与记忆。代码、表格在局部滚动；流式更新不重播整段动画。','ui/components/MarkdownContent.kt','预览 / 源文；代码复制与换行；宽表格；模拟流式输出。'];
screens.push(extraScreen);
screenMap.markdown = extraScreen;
sheets.splice(1,0,['reasoning-quick','思考强度 · 滑块']);
screenMap.providers[5] = '普通模式 radio 设置全局默认；点击配置主体进入编辑。管理模式换成 checkbox，多选不改变默认。';
screenMap.providers[7] = '点击圆形单选设置默认；点击卡片主体编辑；右上角菜单进入管理与多选。';
screenMap.styles[7] = '查看按钮内容白色与上下文分段色；重播交互动效；进入 Markdown 排版页。';

function activeModel() {
    return providers.find(p => p.model === state.model) || {model:state.model,efforts:[],capabilities:null,modelDefault:null,reasoningProtocol:'UNSUPPORTED'};
}
function canAnimate() { return state.motion && !reduceMedia.matches; }
function animateElement(el, frames, duration, label, options = {}) {
    if (!el || !canAnimate() || !el.animate) return null;
    animationLog.push({label,duration,at:Math.round(performance.now())});
    const anim = el.animate(frames,{duration,easing:'cubic-bezier(.2,.8,.2,1)',fill:'none',...options});
    return anim;
}
function mutateDetailsV2(detail, open) {
    if (!detail) return;
    const key = detail.dataset.detailKey;
    if (key) state.detailOpen[key] = open;
    const start = detail.getBoundingClientRect().height;
    detail.getAnimations().forEach(a => a.cancel());
    detail.open = true;
    detail.style.height = '';
    const target = open ? detail.scrollHeight : detail.querySelector(':scope > summary').getBoundingClientRect().height;
    const anim = animateElement(detail,[{height:start+'px'},{height:target+'px'}],motionTokens.expand,'inline-expand');
    if (!anim) { detail.open = open; return; }
    detail.style.overflow = 'hidden';
    anim.onfinish = () => {detail.open = open;detail.style.height='';detail.style.overflow='';};
}

function composer(running = false) {
    const model = activeModel();
    const explicit = state.effortValue !== null;
    const effective = explicit ? state.effortValue : model.modelDefault;
    const caption = explicit ? `${effortLabels[effective]} · ${effective}` : `跟随${effective?' · '+effective:''}`;
    let html = composerV1(running);
    html = html.replace(/<button data-action="sheet:reasoning">[\s\S]*?<\/button>/, model.efforts?.length ? `<button data-action="sheet:reasoning-quick" aria-label="调整思考强度，${esc(caption)}">${icon('spark')}<span>${esc(caption)}</span>${icon('down')}</button>` : '');
    html = html.replace('<span class="context-indicator"></span>', miniContext());
    return html;
}
function miniContext() {
    const seg = contextSegments();
    const total = seg.reduce((n,s)=>n+s.tokens,0);
    const denom = state.unknown ? total : Math.max(total,200000);
    let cumulative=0;
    const stops=seg.filter(s=>s.tokens>0).map(s=>{let a=cumulative;cumulative+=s.tokens/denom*100;return `${s.color} ${a}% ${cumulative}%`;});
    stops.push(`var(--line) ${cumulative}% 100%`);
    return `<span class="context-ring" style="background:conic-gradient(${stops.join(',')})" aria-hidden="true"></span>`;
}
function contextSegments() {return contextDefs.map(([key,label,before,after])=>({key,label,tokens:state.compacted?after:before,color:contextColors[key]}));}
function contextBarMarkup() {
    const seg=contextSegments();const total=seg.reduce((n,s)=>n+s.tokens,0);
    const denominator=state.unknown?Math.max(total,1):Math.max(total,200000);
    const percentage=total/200000*100;
    return `<div class="context-composition ${state.unknown?'unknown':''}" role="img" aria-label="${state.unknown?'本地估算组成，容量未知':`本地估算占容量 ${percentage.toFixed(1)}%`}，${seg.filter(s=>s.tokens).map(s=>s.label+' '+s.tokens+' tokens').join('，')}">
        ${seg.filter(s=>s.tokens>0).map(s=>`<span class="context-segment" data-segment="${s.key}" data-tokens="${s.tokens}" style="width:${s.tokens/denominator*100}%;background:${s.color}" title="${s.label}：${s.tokens.toLocaleString()} tokens"></span>`).join('')}
    </div><div class="context-scale"><span>${state.unknown?'仅表示已用内容的组成':'0'}</span><span>${state.unknown?'不表示容量占用率':'200,000 tokens'}</span></div>`;
}
function contextSheet() {
    const seg=contextSegments();const total=seg.reduce((n,s)=>n+s.tokens,0);
    return sheet('上下文占用',`<p>用不同颜色区分内容来源，空余容量保持中性。</p>
        <div class="row between"><span class="label">本地估算 · 非计费数据</span>${action('unknown-toggle',state.unknown?'切换为已配置':'容量未知示例','text-btn')}</div>
        <div class="context-headline"><strong>${total.toLocaleString()}</strong><span>tokens</span><div>${state.unknown?'未配置容量':`${(total/200000*100).toFixed(1)}% <span class="muted">/ 200,000</span>`}</div></div>
        ${contextBarMarkup()}
        <div class="context-legend-heading"><span>内容类型</span><span>Token · 占已用比例</span></div>
        <div class="context-legend">${seg.filter(s=>s.tokens).map(s=>`<div class="context-legend-row"><span class="legend-dot" style="background:${s.color}"></span><span class="stretch">${s.label}</span><span class="mono">${s.tokens.toLocaleString()}</span><span class="context-percent">${(s.tokens/total*100).toFixed(1)}%</span></div>`).join('')}</div>
        <p class="fine mt16">分类来自本地估算；媒体用量仍受页数、分辨率及供应商处理影响。颜色沿用当前项目的分类映射。${state.unknown?'容量未知时不显示已占容量百分比。':''}</p>
        <details class="activity usage-report" data-detail-key="context-report"><summary>${icon('model')}<span class="stretch">最近一次服务端报告</span>${icon('down','chev')}</summary><div class="activity-body">${[['输入','32,610'],['输出','1,248'],['缓存读取（输入子集）','28,000'],['缓存写入','未报告']].map(([a,b])=>`<div class="legend-row"><span>${a}</span><span>${b}</span></div>`).join('')}<p class="fine mt16">仅为演示数据。缓存读取不重复计入；未报告不等于 0。不能按本地分类分摊服务端账单。</p></div></details>
        ${state.compacted?'<div class="notice good">早期消息已摘要化，原始记录仍保留。</div>':''}`,
        action('sheet:compact','压缩上下文','btn full','history',state.screen==='running'&&!state.runStopped||state.screen==='child'));
}
function nativeCapabilities(p) {
    if(!p.capabilities) return `<div class="native-caps unknown">${icon('info')}<span>原生文件能力未知</span></div>`;
    const names={image:'图片',pdf:'PDF',audio:'音频',video:'视频'};
    const supported=Object.keys(names).filter(k=>p.capabilities[k]);
    if(!supported.length)return `<div class="native-caps unknown">${icon('text')}<span>仅文本 · 未声明原生文件输入</span></div>`;
    return `<div class="native-caps" aria-label="原生输入支持：${supported.map(k=>names[k]).join('、')}">${supported.map(k=>`<span class="native-cap" title="原生支持${names[k]}">${icon(k)}<span>${names[k]}</span></span>`).join('')}</div>`;
}
function modelSheet(o) {
    return sheet(o==='model'?'切换模型':o==='agent-model'?'Agent 默认模型':o==='subagent-model'?'子代理模型':'可用模型',
        `<p>文件图标表示原生输入能力，不等于工作区工具能读取的文件类型。</p>${searchbox('models')}
        ${o==='agent-model'?option('bind-model:跟随全局配置','跟随全局配置','由应用的全局选择决定',true):o==='subagent-model'?option('sub-model:继承主代理','继承主代理','不设置强制覆盖',!state.forceChild):''}
        <div id="model-options">${providers.filter(p=>!state.query||(p.name+' '+p.model).toLowerCase().includes(state.query.toLowerCase())).map(p=>`<div class="section-title">${esc(p.name)} <span class="fine">${p.capabilitySource==='override'?'手动覆盖':p.capabilitySource==='discovered'?'接口发现':'未配置'}</span></div><button class="model-option" data-action="${o==='agent-model'?'bind-model:':o==='subagent-model'?'sub-model:':'model:'}${esc(p.model)}"><span class="model-symbol">${icon('model')}</span><span class="stretch"><strong>${esc(p.model)}</strong><span class="model-description">${esc(p.description)}</span>${nativeCapabilities(p)}</span><span class="radio ${state.model===p.model?'selected':''}"></span></button>`).join('')||'<p class="inline-note">没有匹配的模型。</p>'}</div>
        <div class="notice mt16">图标与能力是演示配置，不是对真实同名模型的能力声明。实际实现使用手动覆盖优先、接口发现其次的有效能力。</div>${action('go:providers','管理模型配置','text-btn full','settings')}`);
}
function effectiveEffort() {return state.effortValue ?? activeModel().modelDefault;}
function wireField(value,model=activeModel()) {
    if(value===null||value===undefined)return '不发送可选思考字段';
    if(model.reasoningProtocol==='OPENAI_CHAT_COMPLETIONS')return `reasoning_effort = "${value}"`;
    if(model.reasoningProtocol==='ANTHROPIC_ADAPTIVE')return value==='none'?'thinking.type = "disabled"':`output_config.effort = "${value}"`;
    if(model.reasoningProtocol==='GEMINI_THINKING_LEVEL')return `thinkingConfig.thinkingLevel = "${value}"`;
    return `canonical effort = "${value}"`;
}
function reasoningQuick() {
    const m=activeModel(), opts=m.efforts||[];
    if(!opts.length) return sheet('思考强度不可用','<p>当前模型没有可确认的思考参数。保留 Provider 原有行为，不强制注入字段。</p>');
    const effective=effectiveEffort(), idx=opts.indexOf(effective), shown=Math.max(0,idx);
    const explicit=state.effortValue!==null;
    const label=effective?`${effortLabels[effective]} · ${effective}`:'模型默认';
    return `<button class="overlay-scrim quick-scrim" data-action="close" aria-label="关闭思考强度"></button><section class="reasoning-popover" role="dialog" aria-modal="true" aria-labelledby="reasoning-quick-title" tabindex="-1">
        <div class="reasoning-top">${icon('spark')}<button class="reasoning-title" id="reasoning-quick-title" data-action="sheet:reasoning" aria-label="${label}，打开详细思考强度选择"><strong>${label}</strong>${icon('chevron')}<small>${explicit?'当前会话覆盖':'跟随模型配置'} · 查看全部档位</small></button>${ib('reasoning-reset','恢复跟随模型配置','refresh')}</div>
        <div class="reasoning-slider ${!effective?'indeterminate':''}" style="--slider-pct:${shown/Math.max(opts.length-1,1)*100}%">
            <div class="reasoning-track"><div class="reasoning-ticks">${opts.map((v,i)=>`<i style="left:${i/Math.max(opts.length-1,1)*100}%" title="${effortLabels[v]} · ${v}"></i>`).join('')}</div></div>
            <input id="effort-slider" type="range" min="0" max="${opts.length-1}" step="1" value="${shown}" aria-label="思考强度" aria-valuetext="${explicit?'':'跟随模型配置，'}${label}" />
        </div><div class="reasoning-scale"><span>${effortLabels[opts[0]]} · <code>${opts[0]}</code></span><span>${effortLabels[opts.at(-1)]} · <code>${opts.at(-1)}</code></span></div>
        <p class="reasoning-wire">${esc(wireField(effective))}</p><p class="fine">${state.screen==='running'&&!state.runStopped?'当前请求不变，下一次模型请求生效。':'拖动后松手应用；点上方文字查看全部字段值。'}</p>
    </section>`;
}
function reasoningSheet() {
    const m=activeModel();const opts=m.efforts||[];
    return sheet('思考强度',`<p>显示名称与字段值放在一起。档位只来自当前模型支持列表，不把不同协议强行补齐。</p>
        <button class="effort-option follow" data-action="reasoning-value:follow"><span class="stretch"><strong>跟随模型配置 <code>null</code></strong><small>清除会话覆盖${m.modelDefault?'；当前生效 '+m.modelDefault:'；配置也为空时不发送思考字段'}。</small></span><span class="radio ${state.effortValue===null?'selected':''}"></span></button>
        ${opts.map(v=>`<button class="effort-option" data-action="reasoning-value:${v}"><span class="stretch"><strong>${effortLabels[v]} <code>${v}</code></strong><small>${esc(wireField(v,m))}</small></span><span class="radio ${state.effortValue===v?'selected':''}"></span></button>`).join('')}
        ${!opts.length?'<div class="notice">当前模型不支持可确认的思考控制；不自动注入参数。</div>':''}
        <div class="notice mt16">${m.reasoningProtocol==='ANTHROPIC_ADAPTIVE'?'非关闭档位还包含 thinking.type = adaptive；自动／手动预算模式以现有适配器为准。':m.reasoningProtocol==='GEMINI_THINKING_LEVEL'?'此示例采用 thinkingLevel；需要 thinkingBudget 的模型必须改由现有适配器映射预算。':'界面列出的原始值交给现有适配器；兼容网关是否接受仍由上游验证。'}</div>
        <p class="fine mt16">执行中调整只影响下一次模型请求；不会中断在途请求。跟随配置不是 none。</p>`,action('sheet:reasoning-quick','返回滑块','btn full','back'));
}
function commitEffort(value, closeAfter=false) {
    const v=value==='follow'?null:value;
    if(v!==null&&!activeModel().efforts?.includes(v))return;
    state.effortValue=v;state.effortDraft=null;
    state.reasoning=v===null?'跟随配置':effortLabels[v];
    if(closeAfter)close();
    const scroll=document.querySelector('.content')?.scrollTop||0;
    render();const content=document.querySelector('.content');if(content)content.scrollTop=scroll;
    if(closeAfter)toast(state.screen==='running'?'思考强度已更新，将在下一次模型请求生效。':'已更新本会话的思考强度。');
}

function providersPage() {
    const items=providers.filter(matches);
    return titlebar(state.manage?'管理模型配置':'模型配置','drawer',state.manage?action('manage:providers','完成','text-btn'):ib('list-menu:providers','列表操作','more'))+`<div class="content"><div class="page-intro"><h1>模型配置</h1><p>${state.manage?'勾选要导出或删除的配置，不改变全局默认。':'选择默认连接，或点开配置调整模型。'}</p></div>${searchbox('providers')}<p class="fine mb16">${state.manage?'方形多选 · 管理当前列表':'圆形单选 · 全局默认 Provider'}　<span class="demo-label">示例数据</span></p><div id="list-results" ${state.manage?'':'role="radiogroup" aria-label="全局默认 Provider"'}>
        ${items.map(p=>`<article class="provider-card ${!state.manage&&p.id===state.selectedProviderId?'is-default':''}">
        <label class="provider-pick" title="${state.manage?'选择':'设为默认：'}${esc(p.name)}"><input type="${state.manage?'checkbox':'radio'}" ${state.manage?`data-provider-select="${p.id}" ${state.selected.has(p.id)?'checked':''}`:`name="default-provider" value="${p.id}" ${p.id===state.selectedProviderId?'checked':''}`} aria-label="${state.manage?'选择配置':'设为默认'} ${esc(p.name)}"><span class="${state.manage?'check-visual':'radio-visual'}" aria-hidden="true">${state.manage?icon('check'):''}</span></label>
        <button class="provider-main" data-action="${state.manage?'select:'+p.id:'edit:providers:'+p.id}" aria-label="${state.manage?'选择':'编辑配置'} ${esc(p.name)}"><span class="provider-identity"><span class="provider-glyph">${p.glyph}</span><span class="stretch"><strong>${esc(p.name)}</strong><small>${esc(p.description)}</small></span>${!state.manage?icon('chevron'):''}</span><span class="provider-model">${icon('model')}<span>${esc(p.model)}</span>${!state.manage&&p.id===state.selectedProviderId?'<span class="default-caption">默认</span>':''}</span></button></article>`).join('')||'<p class="inline-note">没有匹配的配置。</p>'}</div>
        ${!state.manage?action('create:providers','添加模型配置','btn full mt24','plus'):''}<p class="fine mt24">切换全局默认不会覆盖已有会话的模型 override。</p></div>`+listFooter('providers');
}

const toolFixtures = {
    read:{name:'read_file',title:'读取项目说明',path:'project-notes.md',args:{path:'project-notes.md'},result:'# 项目说明\n\nAgentApp 是一个原生 Android harness。\n\n包含会话、Provider、Agent、工具与文件工作区。\n保留权限边界、单层子代理和上下文估算。',time:'11:42:08',status:'已完成'},
    child:{name:'run_subagent',title:'子代理梳理功能',path:'独立上下文 · 单层委派',args:{task:'阅读项目说明，整理已经实现的功能和页面清单。'},result:'已创建子代理会话 demo-child-01。\n结果：整理了聊天、执行控制与配置管理三类能力。',time:'11:42:09',status:'已完成'},
    write:{name:'write_file',title:'生成迭代计划',path:'iteration-plan.md',args:{path:'iteration-plan.md',content:'# AgentApp 迭代计划\n\n先统一布局，再细化交互。'},result:'已写入 iteration-plan.md。\n本示例仅展示原型，不修改任何真实文件。',time:'11:42:12',status:'已完成'},
    verify:{name:'read_file',title:'核对文件内容',path:'iteration-plan.md',args:{path:'iteration-plan.md'},result:'已核对计划：包含三个阶段和关键状态验收点。',time:'11:42:13',status:'已完成'},
    command:{name:'run_command',title:'等待命令审批',path:'pwd · 应用工作区',args:{command:'pwd'},result:null,time:'11:42:12',status:'待确认'}
};
function toolItemV2(key, running=false) {
    const t=toolFixtures[key], id='tool-'+key;
    const open=!!state.detailOpen[id];
    const result=t.result;
    return `<div class="timeline-item ${key==='command'?'running':''}"><details class="tool-call" data-detail-key="${id}" ${open?'open':''}><summary><span class="stretch"><strong>${t.title}</strong><span class="tool-subline"><code>${t.name}</code> · ${t.path}</span></span><span class="tool-toggle-label">${open?'收起':'详情'}</span>${icon('down','chev')}</summary><div class="tool-detail">
        <div class="tool-meta"><span>${tag(t.status,key==='command'?'accent':'success')}</span><span>${t.time} · 演示记录</span></div>
        <p class="fine">调用 ID <code>demo-call-${key}</code>${key==='child'?' · 子会话 demo-child-01':''}</p>
        ${codeBlock(JSON.stringify(t.args,null,4),'JSON','args-'+key,'调用参数')}
        ${result!==null?codeBlock(result,'TEXT','result-'+key,'工具返回'):'<div class="notice warning mt16">尚未执行，没有工具返回。只有明确批准后才会运行。</div>'}
        ${key==='child'?action('go:child','查看只读子会话','text-btn','tree'):''}
        ${key==='write'?action('go:file-view','查看产物与变更记录','text-btn','file'):''}
        ${key==='command'?action('sheet:approval','审阅执行权限','btn full mt16','shield'):''}
        <p class="fine mt16">显示工具实际记录，不把界面摘要当作完整原始返回。</p>
    </div></details></div>`;
}
function activityV2(running=false) {
    const id='activity-'+(running?'running':'done');
    return `<details class="activity tool-activity" data-detail-key="${id}" ${state.detailOpen[id]??running?'open':''}><summary>${running?'<span class="spinner"></span>':icon('check','success')}<span class="stretch">${running?'正在执行计划':'完成了 4 个步骤'}</span><span class="muted">${running?'2 / 4':'查看过程'}</span>${icon('down','chev')}</summary><div class="activity-body"><div class="timeline">${(running?['read','child','command']:['read','child','write','verify']).map(k=>toolItem(k,running)).join('')}</div></div></details>`;
}
function chatBody(running=false) {
    let html=chatBodyV1(running);
    html=html.replace('<div class="assistant-message">','<div class="assistant-message markdown-chat">');
    return html;
}

function codeBlock(text, language='TEXT', id='block', title='') {
    codeRegistry.set(id,text);
    const wrap=state.codeWrap[id]??false;
    return `<section class="md-code" data-code-id="${id}"><header><span class="stretch"><strong>${title||esc(language.toUpperCase())}</strong>${title?`<small>${esc(language)}</small>`:''}</span><button class="code-action" data-action="code-wrap:${id}" aria-pressed="${wrap}" aria-label="切换代码自动换行" title="${wrap?'关闭自动换行':'开启自动换行'}">${icon('wrap')}<span>${wrap?'不换行':'换行'}</span></button><button class="code-action" data-action="code-copy:${id}" aria-label="复制${title||language}代码">${icon(state.codeCopied[id]?'check':'copy')}<span>${state.codeCopied[id]?'已复制':'复制'}</span></button></header><pre class="${wrap?'code-wrapped':''}" tabindex="0" aria-label="${title||language}内容，可横向滚动"><code>${highlightCode(text,language)}</code></pre></section>`;
}
function highlightCode(text,language) {
    // Small presentation-only lexer. Never accepts HTML; unknown languages remain plain text.
    if(!['JSON','KOTLIN','KT','JS','JAVASCRIPT'].includes(language.toUpperCase()))return esc(text);
    const pattern=/("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*|\b(?:val|var|fun|return|if|else|const|true|false|null|data|class|suspend)\b|\b\d+(?:\.\d+)?\b)/g;
    let pos=0,out='';
    for(const m of text.matchAll(pattern)){
        out+=esc(text.slice(pos,m.index));const token=m[0];
        const kind=token.startsWith('//')?'comment':/^["']/.test(token)?'string':/^\d/.test(token)?'number':'keyword';
        out+=`<span class="syntax-${kind}">${esc(token)}</span>`;pos=m.index+token.length;
    }
    return out+esc(text.slice(pos));
}
function inlineMarkdown(text) {
    // Escape before formatting; raw HTML stays text. Links accept only explicit safe schemes.
    const pieces=[];
    const stash=html=>{pieces.push(html);return '\u0001'+(pieces.length-1)+'\u0002';};
    let s=String(text).replace(/`([^`]+)`/g,(_,c)=>stash(`<code class="inline-code">${esc(c)}</code>`));
    s=s.replace(/\[([^\]]+)\]\(([^\s)]+)\)/g,(_,label,url)=>{
        const safe=/^(https?:\/\/|mailto:|tel:)/i.test(url);
        return stash(safe?`<a href="${esc(url)}" target="_blank" rel="noopener noreferrer">${esc(label)}</a>`:esc(label)+' ('+esc(url)+')');
    });
    s=esc(s).replace(/\*\*([^*]+)\*\*/g,'<strong>$1</strong>').replace(/~~([^~]+)~~/g,'<del>$1</del>').replace(/\*([^*]+)\*/g,'<em>$1</em>');
    return s.replace(/\u0001(\d+)\u0002/g,(_,i)=>pieces[Number(i)]);
}
function renderMarkdown(source,prefix='md') {
    const lines=source.replace(/\r/g,'').split('\n');let out='',i=0,block=0;
    const isSpecial=l=>/^\s*(#{1,6}\s|```|>|[-*+]\s|\d+\.\s|---\s*$|\|)/.test(l);
    while(i<lines.length){
        const l=lines[i];if(!l.trim()){i++;continue;}
        if(/^```/.test(l)){const lang=l.slice(3).trim()||'TEXT';let body=[];i++;while(i<lines.length&&!/^```/.test(lines[i]))body.push(lines[i++]);if(i<lines.length)i++;out+=codeBlock(body.join('\n'),lang,prefix+'-'+block++);continue;}
        const head=/^(#{1,6})\s+(.+)$/.exec(l);if(head){out+=`<h${head[1].length}>${inlineMarkdown(head[2])}</h${head[1].length}>`;i++;continue;}
        if(/^---\s*$/.test(l)){out+='<hr>';i++;continue;}
        if(/^>/.test(l)){let body=[];while(i<lines.length&&/^>/.test(lines[i]))body.push(lines[i++].replace(/^> ?/,''));out+=`<blockquote>${renderMarkdown(body.join('\n'),prefix+'-quote')}</blockquote>`;continue;}
        if(l.startsWith('|')&&lines[i+1]?.match(/^\|?\s*:?-+/)){
            const split=t=>t.trim().replace(/^\||\|$/g,'').split('|').map(x=>x.trim());
            const head=split(l),align=split(lines[i+1]).map(c=>c.endsWith(':')?(c.startsWith(':')?'center':'right'):'left');i+=2;const rows=[];
            while(i<lines.length&&lines[i].startsWith('|'))rows.push(split(lines[i++]));
            const cell=(value,j,tag)=>`<${tag} style="text-align:${align[j]}">${inlineMarkdown(value)}</${tag}>`;
            out+=`<div class="md-table-wrap" tabindex="0" role="region" aria-label="数据表格，可横向滚动"><table><thead><tr>${head.map((x,j)=>cell(x,j,'th')).join('')}</tr></thead><tbody>${rows.map(row=>`<tr>${head.map((_,j)=>cell(row[j]||'',j,'td')).join('')}</tr>`).join('')}</tbody></table></div><div class="md-table-hint">${icon('arrow')}宽表格可横向查看</div>`;continue;
        }
        if(/^\s*([-*+]|\d+\.)\s/.test(l)){
            const list=[];while(i<lines.length&&/^\s*([-*+]|\d+\.)\s/.test(lines[i]))list.push(lines[i++]);
            const renderList=(start,depth)=>{let x=start;let html='';const first=/^(\s*)([-*+]|\d+\.)\s+(.*)$/.exec(list[x]);const ordered=/\d/.test(first[2]);const startNo=ordered?parseInt(first[2]):1;html+=ordered?`<ol start="${startNo}">`:'<ul>';
                while(x<list.length){const m=/^(\s*)([-*+]|\d+\.)\s+(.*)$/.exec(list[x]);const level=m[1].length;if(level<depth)break;if(level>depth){const nested=renderList(x,level);html+=nested.html;x=nested.next;continue;}
                    const task=/^\[([ xX])\]\s+(.*)$/.exec(m[3]);html+=`<li${task?' class="task-item"':''}>${task?`<span class="task-check ${task[1].toLowerCase()==='x'?'checked':''}" role="img" aria-label="${task[1].toLowerCase()==='x'?'已完成':'未完成'}">${task[1].toLowerCase()==='x'?icon('check'):''}</span>`:''}<span>${inlineMarkdown(task?task[2]:m[3])}</span>`;x++;
                    if(x<list.length&&list[x].match(/^\s*/)[0].length>depth){const nested=renderList(x,list[x].match(/^\s*/)[0].length);html+=nested.html;x=nested.next;}html+='</li>';
                }html+=ordered?'</ol>':'</ul>';return {html,next:x};};
            out+=renderList(0,list[0].match(/^\s*/)[0].length).html;continue;
        }
        let paragraph=[l];i++;while(i<lines.length&&lines[i].trim()&&!isSpecial(lines[i]))paragraph.push(lines[i++]);out+=`<p>${paragraph.map(inlineMarkdown).join('<br>')}</p>`;
    }
    return out;
}
const markdownSample = `# 让内容更好读

同一份回答，也可以有清楚的呼吸感。正文使用 **清晰的强调**，配合 *补充说明*、~~已废弃的方案~~ 和行内代码 \`ContextUsageBar\`。

> 不是给所有文字套卡片，而是让标题、正文与结构自然区分。
>
> 文件与工具细节需要时再展开，阅读不被打断。

## 01 · 信息有层次

普通段落保持稳定行高。中文不靠增大字距制造“设计感”；长链接和路径允许合理折行。

- 保留模型切换、工具执行与授权边界。
    - 复杂参数放入详细面板。
    - 工具结果在原地展开，不跳出对话。
- 已完成内容不反复淡入，流式输出也不闪烁。

### 一份可执行的清单

1. 先统一阅读排版。
2. 再补充组件与交互反馈。
3. 最后检查长内容与小屏。

- [x] 浅色与深色采用同一套层级
- [x] 代码块有独立复制与换行
- [ ] 在真实设备验证大字体与手势返回

## 02 · 代码更清楚

代码默认局部横向滚动，可以切换自动换行；复制保留原始文本，不带语法高亮标记。

\`\`\`kotlin
// 原型示意；真正实现复用仓库的数据源
val selected = conversation.reasoningEffortOverride
val effective = selected ?: provider.reasoningEffort

fun label(value: String?): String {
    return value ?: "跟随模型配置"
}
\`\`\`

长行也不撑开整个页面：

\`\`\`json
{"model":"demo-model","reasoning_effort":"high","messages":[{"role":"user","content":"Keep the original message and tool execution boundaries unchanged."}]}
\`\`\`

## 03 · 表格不挤成细条

| 区域 | 状态 | 原生实现入口 | 验收重点 |
| --- | :---: | --- | --- |
| 聊天 | 已完成 | ChatScreen.kt | 正文选择与滚动 |
| 上下文 | 已完成 | ContextUsageUi.kt | 分类比例与容量未知 |
| Markdown | 待实机验证 | MarkdownContent.kt | 长代码、宽表、大字体 |

## 04 · 阅读的边界

安全链接示例：[Compose 文本与排版](https://developer.android.com/develop/ui/compose/text)。原始 HTML 只当文本，不执行脚本。

\`\`\`text
/data/user/0/com.Ling.actant/files/workspace/notes/long-document-name-for-layout-verification.md
\`\`\`

---

这是一份 **排版测试样本**。勾选框只表示文档状态，不会改变任务或授权。`;
function documentMarkup() {
    return `<article class="document markdown"><div class="label">WORKSPACE / DOCUMENT</div>${renderMarkdown(planText,'document')}</article>`;
}
function markdownPage() {
    const source=state.streamingMarkdown?state.streamingText:markdownSample;
    return titlebar('Markdown 排版','go:styles',ib('markdown-stream',state.streamingMarkdown?'停止流式演示':'模拟流式输出',state.streamingMarkdown?'stop':'spark'))+`<div class="content markdown-screen"><div class="tabs">${action('markdown-tab:preview','预览',state.markdownTab==='preview'?'active':'')}${action('markdown-tab:source','源文',state.markdownTab==='source'?'active':'')}</div><div class="notice mb16"><span class="demo-label">排版样本</span> 标题、列表、引用、任务项、代码、表格与链接。</div><div id="markdown-live" class="markdown">${state.markdownTab==='source'?codeBlock(markdownSample,'MARKDOWN','markdown-source'):renderMarkdown(source,'showcase')}</div></div>`;
}
function styles() {
    return stylesV1().replace('</div><div class="section-title">文本层次</div>', '</div><div class="section-title">文本层次</div>').replace('<div class="section-title">控件</div>',`${action('go:markdown','打开完整 Markdown 排版样本','btn full mt24','file')}<div class="section-title">动效与状态</div><div class="row">${action('motion-replay','重播交互动效','btn','refresh')}${action('banner-demo','顶部横幅示例','btn')}</div><div class="section-title">上下文分类</div>${contextBarMarkup()}<div class="section-title">控件</div>`);
}
function pageV2() { return state.screen==='markdown'?markdownPage():pageV1(); }
function overlay() {
    const o=state.overlay;
    if(o==='context')return contextSheet();
    if(o==='reasoning-quick')return reasoningQuick();
    if(o==='reasoning')return reasoningSheet();
    if(['model','agent-model','subagent-model','provider-models'].includes(o))return modelSheet(o);
    return overlayV1();
}

function renderV2(keepScroll=true) {
    const content=document.getElementById('phone-content');
    const routeChanged=!!lastRoute&&lastRoute!==state.screen;
    const themeChanged=!!lastTheme&&lastTheme!==state.theme;
    const oldScroll=content.querySelector('.content');const y=oldScroll&&keepScroll&&!routeChanged?oldScroll.scrollTop:0;
    document.querySelectorAll('.page-snapshot').forEach(el=>el.remove());
    let ghost;
    if(routeChanged&&canAnimate()){
        ghost=content.cloneNode(true);ghost.removeAttribute('id');ghost.classList.add('page-snapshot');ghost.inert=true;ghost.setAttribute('aria-hidden','true');ghost.querySelectorAll('[id]').forEach(el=>el.removeAttribute('id'));ghost.querySelector('.feedback-lane')?.remove();document.getElementById('phone').append(ghost);
        const old=oldScroll?.scrollTop||0;const gs=ghost.querySelector('.content');if(gs)gs.scrollTop=old;
    }
    document.documentElement.dataset.theme=state.theme;document.getElementById('phone').dataset.theme=state.theme;
    content.innerHTML=page();
    const c=content.querySelector('.content');if(c)c.scrollTop=y;
    content.querySelectorAll('[data-detail-key]').forEach(d=>{if(d.dataset.detailKey in state.detailOpen)d.open=state.detailOpen[d.dataset.detailKey];});
    lastRoute=state.screen;lastTheme=state.theme;
    renderOverlay();renderReview();renderFeedback();
    if(routeChanged){
        content.getAnimations().forEach(a=>a.cancel());
        animateElement(content,[{opacity:0,transform:`translateX(${motionDirection*20}px)`},{opacity:1,transform:'translateX(0)'}],motionTokens.route,'route-enter');
        if(ghost){const a=animateElement(ghost,[{opacity:1,transform:'translateX(0)'},{opacity:0,transform:`translateX(${-motionDirection*12}px)`}],motionTokens.exit,'route-exit');if(a)a.onfinish=()=>ghost.remove();else ghost.remove();}
    } else if(themeChanged) {
        const p=document.getElementById('phone');animateElement(p,[{opacity:.82},{opacity:1}],motionTokens.theme,'theme-fade');
    }
    document.title=screenMap[state.screen][2]+' · AgentApp UI 原型 v2';
}
function renderOverlayV2() {
    const host=document.getElementById('overlay'), epoch=++overlayEpoch;
    const next=state.overlay,previous=overlayRendered;
    const was=host.querySelector('[role=dialog]');
    if(next===null&&was){
        overlayRendered=null;host.classList.add('closing');
        const frames=was.classList.contains('drawer')?[{transform:'translateX(0)',opacity:1},{transform:'translateX(-36px)',opacity:0}]:[{transform:'translateY(0)',opacity:1},{transform:'translateY(24px)',opacity:0}];
        const a=animateElement(was,frames,motionTokens.exit,'overlay-exit');
        animateElement(host.querySelector('.overlay-scrim'),[{opacity:1},{opacity:0}],motionTokens.exit,'scrim-exit');
        const finish=()=>{if(epoch!==overlayEpoch)return;host.innerHTML='';host.classList.remove('closing');document.getElementById('phone-content').inert=false;renderFeedback();restoreFocus();};
        if(a)a.onfinish=finish;else finish();return;
    }
    if(next===null){host.innerHTML='';host.classList.remove('closing');document.getElementById('phone-content').inert=false;overlayRendered=null;return;}
    const oldY=host.querySelector('.sheet-content')?.scrollTop||0;
    host.classList.remove('closing');host.innerHTML=overlay();document.getElementById('phone-content').inert=true;overlayRendered=next;
    const dialog=host.querySelector('[role=dialog]');
    host.querySelectorAll('[data-detail-key]').forEach(d=>{if(d.dataset.detailKey in state.detailOpen)d.open=state.detailOpen[d.dataset.detailKey];});
    if(previous===next){const s=host.querySelector('.sheet-content');if(s)s.scrollTop=oldY;}
    else{
        const frames=dialog?.classList.contains('drawer')?[{transform:'translateX(-36px)',opacity:.5},{transform:'translateX(0)',opacity:1}]:dialog?.classList.contains('reasoning-popover')?[{transform:'translateY(12px) scale(.97)',opacity:0},{transform:'translateY(0) scale(1)',opacity:1}]:[{transform:'translateY(38px)',opacity:0},{transform:'translateY(0)',opacity:1}];
        animateElement(dialog,frames,motionTokens.sheet,'overlay-enter');
        if(!previous)animateElement(host.querySelector('.overlay-scrim'),[{opacity:0},{opacity:1}],motionTokens.banner,'scrim-enter');
    }
    renderFeedback();dialog?.focus({preventScroll:true});
}
function restoreFocus() {
    if(focusBefore?.isConnected&&!focusBefore.closest('[inert]'))focusBefore.focus({preventScroll:true});
    else document.querySelector('#phone-content [data-action="sheet:reasoning-quick"]')?.focus({preventScroll:true});
    focusBefore=null;
}
function renderReviewV2() {
    renderReviewV1();
    document.getElementById('review-info').innerHTML=document.getElementById('review-info').innerHTML.replace('24 个页面','25 个页面').replace('DESIGN NOTE /','DESIGN V2 /');
    document.getElementById('review-info').insertAdjacentHTML('beforeend',`<div class="rule"></div><div class="eyebrow">MOTION / INTERACTION</div><p>页面、弹层和内联内容都有过渡。流式正文不重复进场。</p><div class="column">${action('motion-replay','重播交互动效','review-btn')}${action('motion-toggle',canAnimate()?'动效开启 · 点击关闭':'动效关闭 / 系统减少动态效果','review-btn')}${action('go:markdown','Markdown 排版样本','review-btn')}</div>`);
}
function navigateV2(route,push=true) {
    if(!screenMap[route])return;if(state.overlay==='compacting'){toast('请先等待或取消上下文压缩。');return;}
    if(state.streamingMarkdown){clearInterval(markdownTimer);state.streamingMarkdown=false;}
    motionDirection=push?1:-1;
    if(route!==state.screen)state.banner=null;
    navigateV1(route,push);
}
function show(o) {
    if(state.overlay==='compacting'&&o!=='compacting')return;
    if(!state.overlay)focusBefore=document.activeElement;
    state.overlay=o;state.query='';renderOverlay();renderReview();
}
function close() {
    if(state.overlay==='compacting'){toast('请使用“取消压缩”停止操作。');return;}
    state.overlay=null;state.query='';renderOverlay();renderReview();
}
function renderFeedbackV2(animate=false) {
    document.querySelectorAll('.feedback-lane').forEach(el=>el.remove());document.getElementById('toast')?.remove();
    if(!state.banner)return;
    const dialog=document.querySelector('#overlay [role=dialog]');
    const header=dialog?.querySelector('.sheet-header,.drawer-header,.reasoning-top')||document.querySelector('#phone-content .topbar');
    if(!header)return;
    const lane=document.createElement('div');lane.className='feedback-lane';
    lane.innerHTML=`<div class="top-feedback ${state.banner.type||'info'}"><span class="feedback-icon">${icon(state.banner.type==='error'?'info':'check')}</span><span class="stretch feedback-text" role="${state.banner.type==='error'?'alert':'status'}" aria-live="${state.banner.type==='error'?'assertive':'polite'}">${esc(state.banner.text)}</span>${ib('banner-close','关闭消息提示','close')}</div>`;
    header.after(lane);if(animate)animateElement(lane,[{opacity:0,transform:'translateY(-8px)'},{opacity:1,transform:'translateY(0)'}],motionTokens.banner,'top-banner');
    lane.addEventListener('mouseenter',()=>clearTimeout(toastTimer));lane.addEventListener('focusin',()=>clearTimeout(toastTimer));
    lane.addEventListener('mouseleave',()=>scheduleBanner());lane.addEventListener('focusout',()=>scheduleBanner());
}
function scheduleBanner() {clearTimeout(toastTimer);if(state.banner&&state.banner.type!=='error')toastTimer=setTimeout(dismissBanner,4500);}
function toast(text,type='info') {clearTimeout(toastTimer);state.banner={text,type};renderFeedback(true);scheduleBanner();}
function dismissBannerV2() {
    clearTimeout(toastTimer);state.banner=null;
    const lane=document.querySelector('.feedback-lane');if(!lane)return;
    const a=animateElement(lane,[{opacity:1,height:lane.offsetHeight+'px'},{opacity:0,height:'0px'}],motionTokens.exit,'banner-exit');
    if(a)a.onfinish=()=>lane.remove();else lane.remove();
}
function updateSliderPreview(input) {
    const opts=activeModel().efforts||[],value=opts[Number(input.value)];if(!value)return;
    state.effortDraft=value;const popup=input.closest('.reasoning-popover');
    popup.querySelector('.reasoning-slider').style.setProperty('--slider-pct',Number(input.value)/Math.max(opts.length-1,1)*100+'%');
    popup.querySelector('.reasoning-slider').classList.remove('indeterminate');
    popup.querySelector('.reasoning-title strong').textContent=effortLabels[value]+' · '+value;
    popup.querySelector('.reasoning-title small').textContent='松手应用 · 点击查看全部档位';
    popup.querySelector('.reasoning-wire').textContent=wireField(value);
    input.setAttribute('aria-valuetext',effortLabels[value]+' · '+value);
}
function runMarkdownStreamV2() {
    if(state.streamingMarkdown){clearInterval(markdownTimer);state.streamingMarkdown=false;render();return;}
    state.streamingMarkdown=true;state.streamingText='';state.markdownTab='preview';render();let i=0;
    markdownTimer=setInterval(()=>{
        if(state.screen!=='markdown'){clearInterval(markdownTimer);return;}
        i+=27;state.streamingText=markdownSample.slice(0,i);
        const node=document.getElementById('markdown-live');if(node)node.innerHTML=renderMarkdown(state.streamingText,'showcase');
        if(i>=markdownSample.length){clearInterval(markdownTimer);state.streamingMarkdown=false;render();}
    },120);
}
function replayMotionV2() {
    replayTimers.forEach(clearTimeout);replayTimers=[];
    state.banner=null;state.overlay=null;state.detailOpen['activity-done']=false;state.detailOpen['tool-read']=false;
    navigate('home');
    const later=(ms,fn)=>replayTimers.push(setTimeout(fn,ms));
    later(600,()=>navigate('chat'));
    later(1200,()=>show('reasoning-quick'));
    later(2400,()=>show('reasoning'));
    later(3600,()=>close());
    later(4200,()=>{const d=document.querySelector('[data-detail-key="activity-done"]');mutateDetails(d,true);});
    later(4800,()=>{const d=document.querySelector('[data-detail-key="tool-read"]');mutateDetails(d,true);d?.scrollIntoView({block:'center',behavior:canAnimate()?'smooth':'auto'});});
    later(6000,()=>toast('动效演示完成。工具详情在原位置展开，底部操作始终可用。'));
}
function handleV2(act) {
    const [verb,...parts]=act.split(':');const value=parts.join(':');
    if(verb==='reasoning-value'){commitEffort(value,true);return;}
    if(verb==='reasoning'){commitEffort(value==='跟随配置'?'follow':Object.keys(effortLabels).find(k=>effortLabels[k]===value)||value,true);return;}
    if(verb==='model'){
        state.model=value;
        const unsupported=state.effortValue!==null&&!activeModel().efforts?.includes(state.effortValue);
        if(unsupported){state.effortValue=null;state.reasoning='跟随配置';}
        close();render();toast(unsupported?'新模型不支持原思考档位，已恢复跟随配置。':'已更新当前会话模型，不改变全局默认。');return;
    }
    if(act==='reasoning-reset'){commitEffort('follow');return;}
    if(act==='banner-close'){dismissBanner();return;}
    if(act==='banner-demo'){toast('更改已保存。横幅位于标题栏下方，不遮挡输入、发送或底部确认按钮。');return;}
    if(act==='motion-replay'){replayMotion();return;}
    if(act==='motion-toggle'){state.motion=!state.motion;if(!canAnimate())document.getAnimations().forEach(a=>a.finish());document.documentElement.dataset.motion=canAnimate()?'on':'off';renderReview();return;}
    if(act==='markdown-stream'){runMarkdownStream();return;}
    if(verb==='markdown-tab'){state.markdownTab=value;render();return;}
    if(verb==='code-wrap'){
        state.codeWrap[value]=!(state.codeWrap[value]??false);
        const node=document.querySelector(`[data-code-id="${CSS.escape(value)}"]`), pre=node?.querySelector('pre');
        pre?.classList.toggle('code-wrapped',state.codeWrap[value]);const b=node?.querySelector('[data-action^="code-wrap:"]');
        if(b){b.setAttribute('aria-pressed',String(state.codeWrap[value]));b.innerHTML=icon('wrap')+'<span>'+(state.codeWrap[value]?'不换行':'换行')+'</span>';}
        return;
    }
    if(verb==='code-copy'){
        const text=codeRegistry.get(value)||'';
        const copied=()=>{state.codeCopied[value]=true;const b=document.querySelector(`[data-action="code-copy:${CSS.escape(value)}"]`);if(b)b.innerHTML=icon('check')+'<span>已复制</span>';};
        if(navigator.clipboard?.writeText)navigator.clipboard.writeText(text).then(copied).catch(()=>toast('剪贴板被浏览器阻止，可在代码区域选择并复制。'));
        else {const box=document.createElement('textarea');box.value=text;box.style.position='fixed';box.style.opacity='0';document.body.append(box);box.select();let ok=false;try{ok=document.execCommand('copy');}catch{}box.remove();if(ok)copied();else toast('请选中代码区域手动复制；浏览器未开放剪贴板。');}
        return;
    }
    if(act==='unknown-toggle'){state.unknown=!state.unknown;render();return;}
    handleV1(act);
}
// Native range is keyboard and touch operable. Apply only on change, not on every input frame.
document.addEventListener('input',e=>{if(e.target.id==='effort-slider')updateSliderPreview(e.target);});
document.addEventListener('change',e=>{
    const el=e.target;
    if(el.id==='effort-slider'){const val=activeModel().efforts[Number(el.value)];commitEffort(val);const slider=document.getElementById('effort-slider');slider?.focus({preventScroll:true});}
    if(el.name==='default-provider')handle('default-provider:'+el.value);
    if(el.dataset.providerSelect)handle('select:'+el.dataset.providerSelect);
});
document.addEventListener('pointercancel',e=>{if(e.target.id==='effort-slider'){state.effortDraft=null;renderOverlay();}});
document.addEventListener('click',e=>{
    const summary=e.target.closest('summary');if(!summary||e.target.closest('[data-action]'))return;
    const d=summary.parentElement;if(d.tagName!=='DETAILS')return;
    e.preventDefault();const open=!(d.dataset.detailKey in state.detailOpen ? state.detailOpen[d.dataset.detailKey] : d.open);mutateDetails(d,open);
    const label=summary.querySelector('.tool-toggle-label');if(label)label.textContent=open?'收起':'详情';
});
reduceMedia.addEventListener('change',()=>{document.documentElement.dataset.motion=canAnimate()?'on':'off';if(!canAnimate())document.getAnimations().forEach(a=>{if(a.effect?.getTiming().iterations===Infinity)a.cancel();else try{a.finish();}catch{a.cancel();}});renderReview();});
document.documentElement.dataset.motion=canAnimate()?'on':'off';
