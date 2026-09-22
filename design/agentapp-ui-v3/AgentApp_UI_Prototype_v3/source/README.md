# 可编辑原型源文件

`index.html`已内联源代码，不需要依赖或网络。三层按顺序加载：

1. prototype.js / styles.css：基础页面与演示模型。
2. refinements.js / refinements.css：v2能力、Markdown、上下文、滑块与单多选。
3. iteration-v3.js / iteration-v3.css：当前有效的工具呈现、局部DOM保留、动效、按钮对齐与新草稿首发。

旧函数显式命名V1/V2仅供复用；最终页面、handle、render和初始化由v3接管。修改后运行：

```text
python tests/build_prototype.py
```

该脚本将三层CSS/JS重新写入index.html，标准库即可执行。原型源便于迭代设计，不是生产端移植方向；不要把浏览器DOM reconciler、fixture LCS或简化Markdown parser搬进Android。

测试脚本需要Playwright和Chromium。设置CHROMIUM_PATH可指定浏览器可执行文件。默认从本目录的上一层读取index.html，经set_content运行，无外部站点和真实账户访问。
