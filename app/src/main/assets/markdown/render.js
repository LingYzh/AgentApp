/* Only bundled libraries run here. Document text is always supplied as data. */
async function renderMarkdownPayload(payload) {
    const root = document.getElementById('content');
    const source = payload.source || '';
    try {
        if (payload.kind === 'mermaid') {
            mermaid.initialize({
                startOnLoad: false,
                securityLevel: 'strict',
                theme: payload.dark ? 'dark' : 'default',
                suppressErrorRendering: true,
                maxTextSize: 100000,
                maxEdges: 1000,
                secure: ['securityLevel', 'startOnLoad', 'maxTextSize', 'maxEdges', 'secure'],
                flowchart: { htmlLabels: false }
            });
            const result = await mermaid.render('diagram', source);
            root.innerHTML = result.svg;
        } else if (payload.kind === 'math') {
            katex.render(source, root, { displayMode: true, throwOnError: true, trust: false, maxSize: 20, maxExpand: 1000 });
        } else {
            for (const run of payload.runs) {
                let node = document.createElement(run.math ? 'span' : run.code ? 'code' : 'span');
                if (run.math) {
                    try {
                        katex.render(run.text, node, { throwOnError: true, trust: false, maxSize: 20, maxExpand: 1000 });
                    } catch (_) {
                        node.textContent = run.text;
                        node.title = '公式无法解析，保留原文';
                    }
                } else {
                    node.textContent = run.text;
                }
                if (run.bold) node.style.fontWeight = '600';
                if (run.italic) node.style.fontStyle = 'italic';
                if (run.strike) node.style.textDecoration = 'line-through';
                if (run.highlight) node.classList.add('highlight');
                if (run.superscript) node.style.verticalAlign = 'super';
                if (run.subscript) node.style.verticalAlign = 'sub';
                if (run.superscript || run.subscript) node.style.fontSize = '.8em';
                if (run.link) {
                    const anchor = document.createElement('a');
                    anchor.href = run.link;
                    anchor.append(node);
                    node = anchor;
                }
                root.append(node);
            }
        }
    } catch (_) {
        root.textContent = '无法渲染，已保留原文：\n' + source;
        root.style.whiteSpace = 'pre-wrap';
    }
    const measure = () => {
        document.title = 'height:' + Math.ceil(root.getBoundingClientRect().height + 8);
    };
    new ResizeObserver(measure).observe(root);
    document.fonts.ready.then(measure);
    measure();
}
