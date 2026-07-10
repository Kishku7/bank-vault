# build-neoforge.ps1 -- walk every NeoForge cell (pre-26 cog cells + the 26 matrix) into dist/.
# Usage: pwsh -File scripts\build-neoforge.ps1 [cell ...]   (none = all)
param([Parameter(ValueFromRemainingArguments)][string[]]$Only)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $repo "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$pre26 = @('1.20.4', '1.20.6', '1.21', '1.21.1', '1.21.2', '1.21.5', '1.21.8', '1.21.9', '1.21.11')
$matrix = [ordered]@{
  "26.1" = @{neo = "26.1.2.30-beta"; pf = 84; range = "[26.1.2,26.2)"; neoRange = "[26.1.2.0-beta,)"; mc = "26.1.2" }
  "26.2" = @{neo = "26.2.0.1-beta";  pf = 88; range = "[26.2,26.3)"; neoRange = "[26.2.0-alpha,)"; mc = "26.2" }
}

$targets = if ($Only -and $Only.Count -gt 0) { $Only } else { $pre26 + @($matrix.Keys) }

foreach ($cell in $targets) {
    if ($pre26 -contains $cell) {
        Write-Host "=== BV NeoForge cell $cell ==="
        & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'cog-gen.ps1') -Cell "NeoForge/$cell"
        if ($LASTEXITCODE -ne 0) { throw "cog-gen FAILED NeoForge/$cell" }
        $dir = Join-Path $repo "NeoForge\$cell"
        Push-Location $dir
        & .\gradlew.bat clean build --no-daemon
        $rc = $LASTEXITCODE; Pop-Location
        if ($rc -ne 0) { throw "NeoForge FAILED $cell" }
        $jar = Get-ChildItem (Join-Path $dir "build\libs") -Filter "bank-vault-*.jar" |
            Where-Object { $_.Name -notmatch 'sources' } | Sort-Object LastWriteTime | Select-Object -Last 1
        $modver = (Select-String -Path (Join-Path $dir "gradle.properties") -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value
        Copy-Item $jar.FullName (Join-Path $dist ("bank-vault-{0}+{1}-neoforge.jar" -f $modver, $cell)) -Force
        Write-Host "  -> dist bank-vault-$modver+$cell-neoforge.jar"
    } elseif ($matrix.Contains($cell)) {
        $m = $matrix[$cell]
        Write-Host "=== BV NeoForge 26-matrix $cell ==="
        $nf = Join-Path $repo "NeoForge\26"
        # D16 (2026-07-10): cog-materialize gen/ (shared_minecraft eliminated)
        & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'cog-gen.ps1') -Cell "NeoForge/26" -Ver $cell
        if ($LASTEXITCODE -ne 0) { throw "cog-gen FAILED NeoForge/26 @$cell" }
        $modver = (Select-String -Path (Join-Path $nf "gradle.properties") -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value
        $env:PACK_FORMAT = "$($m.pf)"
        Push-Location $nf
        & .\gradlew.bat clean build "-Pminecraft_version=$($m.mc)" "-Pneo_version=$($m.neo)" "-Pmc_range=$($m.range)" "-Pneoforge_range=$($m.neoRange)" --no-daemon
        $rc = $LASTEXITCODE; Pop-Location
        Remove-Item Env:PACK_FORMAT -ErrorAction SilentlyContinue
        if ($rc -ne 0) { throw "NeoForge FAILED $cell" }
        $jar = Get-ChildItem (Join-Path $nf "build\libs") -Filter "bank-vault-*.jar" |
            Where-Object { $_.Name -notmatch 'sources' } | Sort-Object LastWriteTime | Select-Object -Last 1
        Copy-Item $jar.FullName (Join-Path $dist ("bank-vault-{0}+{1}-neoforge.jar" -f $modver, $cell)) -Force
        Write-Host "  -> dist bank-vault-$modver+$cell-neoforge.jar"
    } else {
        throw "Unknown NeoForge cell: $cell"
    }
}
Write-Host "NeoForge builds complete."
