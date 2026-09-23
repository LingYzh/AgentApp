$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    npm ci --ignore-scripts --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'Dependency install failed' }
    $assetDirectory = Join-Path $PSScriptRoot '../../../app/src/main/assets/markdown'
    New-Item -ItemType Directory -Force -Path $assetDirectory | Out-Null
    & './node_modules/.bin/esbuild.cmd' mermaid-entry.js --bundle --minify --legal-comments=external --format=iife --target=es2020 "--outfile=$assetDirectory/mermaid.min.js"
    if ($LASTEXITCODE -ne 0) { throw 'Mermaid bundle failed' }
    Copy-Item -LiteralPath 'node_modules/katex/dist/katex.min.js', 'node_modules/katex/dist/katex.min.css' -Destination $assetDirectory
    Copy-Item -LiteralPath 'node_modules/katex/dist/fonts' -Destination $assetDirectory -Recurse -Force
    Copy-Item -LiteralPath 'node_modules/katex/LICENSE' -Destination "$assetDirectory/LICENSE-katex.txt"
    Copy-Item -LiteralPath 'node_modules/mermaid/LICENSE' -Destination "$assetDirectory/LICENSE-mermaid.txt"
} finally {
    Pop-Location
}
