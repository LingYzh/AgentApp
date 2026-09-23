param(
    [string]$Serial = 'emulator-5554',
    [string]$Adb = 'D:\SDK\platform-tools\adb.exe'
)
$ErrorActionPreference = 'Stop'
if (-not $Serial.StartsWith('emulator-')) { throw 'This helper only reads an emulator.' }
# Read only; credentials go directly into the ignored local backup, never into console output.
$configText = & $Adb -s $Serial exec-out run-as com.Ling.actant cat files/config.json
if ($LASTEXITCODE -ne 0) { throw 'Could not read the debug app configuration.' }
$config = ($configText -join "`n") | ConvertFrom-Json
$archive = [ordered]@{
    format = 'actant.providers'
    version = 1
    includesApiKeys = $true
    providers = @($config.providers)
    selectedProviderId = $config.selectedProviderId
}
$backupDirectory = Join-Path $PSScriptRoot '../local'
New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
$backupPath = Join-Path $backupDirectory 'emulator-providers-with-keys.json'
$archive | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $backupPath -Encoding utf8NoBOM
Write-Output "Saved provider archive: $([System.IO.Path]::GetFullPath($backupPath))"
Write-Output "Provider count: $(@($config.providers).Count)"
