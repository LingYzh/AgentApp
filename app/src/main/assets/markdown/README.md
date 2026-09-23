# Offline Markdown renderers

- KaTeX 0.18.7 (MIT): https://katex.org/ — distributed JavaScript, CSS and fonts.
- Mermaid 12.0.0 (MIT): https://mermaid.js.org/ — bundled with esbuild 0.25.12, including its default diagram modules. Bundled dependency notices are in mermaid.min.js.LEGAL.txt.
- Rebuild from the repository root with `.Codex/tools/markdown-vendor/build.ps1`; the adjacent npm lock pins the source packages.
- Only formula paragraphs and Mermaid blocks use these assets. CommonMark text remains native Compose.
- The WebView has no native JavaScript bridge, network, file or content-provider access. It serves these bundled assets through a private HTTPS origin; Mermaid uses strict mode and KaTeX has trust disabled. Raw Markdown HTML is never injected.
- Unsupported formula/diagram syntax retains readable source. Platform-private Markdown extensions are not a universal standard.
