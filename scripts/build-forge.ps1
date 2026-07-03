# build-forge.ps1 -- walk every Forge cell (all pre-26; Forge has no 26 line) into dist/.
# Usage: pwsh -File scripts\build-forge.ps1 [cell ...]   (none = all)
param([Parameter(ValueFromRemainingArguments)][string[]]$Only)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $repo "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$cells = @('1.20.1', '1.20.6', '1.21', '1.21.1', '1.21.5', '1.21.8')
$targets = if ($Only -and $Only.Count -gt 0) { $Only } else { $cells }

foreach ($cell in $targets) {
    if ($cells -notcontains $cell) { throw "Unknown Forge cell: $cell" }
    Write-Host "=== BV Forge cell $cell ==="
    & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'cog-gen.ps1') -Cell "Forge/$cell"
    if ($LASTEXITCODE -ne 0) { throw "cog-gen FAILED Forge/$cell" }
    $dir = Join-Path $repo "Forge\$cell"
    Push-Location $dir
    & .\gradlew.bat clean build --no-daemon
    $rc = $LASTEXITCODE; Pop-Location
    if ($rc -ne 0) { throw "Forge FAILED $cell" }
    $jar = Get-ChildItem (Join-Path $dir "build\libs") -Filter "bank-vault-*.jar" |
        Where-Object { $_.Name -notmatch 'sources' } | Sort-Object LastWriteTime | Select-Object -Last 1
    $modver = (Select-String -Path (Join-Path $dir "gradle.properties") -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value
    Copy-Item $jar.FullName (Join-Path $dist ("bank-vault-{0}+{1}-forge.jar" -f $modver, $cell)) -Force
    Write-Host "  -> dist bank-vault-$modver+$cell-forge.jar"
}
Write-Host "Forge builds complete."
