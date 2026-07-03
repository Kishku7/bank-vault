# build-fabric.ps1 -- walk every Fabric cell (pre-26 cog cells + the 26 matrix) into dist/.
# Usage: pwsh -File scripts\build-fabric.ps1 [cell ...]   (e.g. 1.21.8 26.2 ; none = all)
param([Parameter(ValueFromRemainingArguments)][string[]]$Only)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $repo "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$pre26 = @('1.20', '1.20.6', '1.21', '1.21.1', '1.21.2', '1.21.5', '1.21.6', '1.21.8', '1.21.11')
$matrix = [ordered]@{
  "26.1" = @{mc = "26.1.2";           api = "0.152.1+26.1.2"; loader = "0.18.6"; lo = "26.1-";  hi = "26.2"; pf = 84 }
  "26.2" = @{mc = "26.2";             api = "0.152.1+26.2";   loader = "0.19.3"; lo = "26.2-";  hi = "26.3"; pf = 88 }
  "26.3" = @{mc = "26.3-snapshot-2";  api = "0.153.1+26.3";   loader = "0.19.3"; lo = "26.3-";  hi = "26.4"; pf = 89 }
}

$targets = if ($Only -and $Only.Count -gt 0) { $Only } else { $pre26 + @($matrix.Keys) }

foreach ($cell in $targets) {
    if ($pre26 -contains $cell) {
        Write-Host "=== BV Fabric cell $cell ==="
        & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'cog-gen.ps1') -Cell "Fabric/$cell"
        if ($LASTEXITCODE -ne 0) { throw "cog-gen FAILED Fabric/$cell" }
        $dir = Join-Path $repo "Fabric\$cell"
        Push-Location $dir
        & .\gradlew.bat clean build --no-daemon
        $rc = $LASTEXITCODE; Pop-Location
        if ($rc -ne 0) { throw "Fabric FAILED $cell" }
        $jar = Get-ChildItem (Join-Path $dir "build\libs") -Filter "bank-vault-*.jar" |
            Where-Object { $_.Name -notmatch 'sources' } | Sort-Object LastWriteTime | Select-Object -Last 1
        $modver = (Select-String -Path (Join-Path $dir "gradle.properties") -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value
        Copy-Item $jar.FullName (Join-Path $dist ("bank-vault-{0}+{1}-fabric.jar" -f $modver, $cell)) -Force
        Write-Host "  -> dist bank-vault-$modver+$cell-fabric.jar"
    } elseif ($matrix.Contains($cell)) {
        $m = $matrix[$cell]
        Write-Host "=== BV Fabric 26-matrix $cell (mc=$($m.mc)) ==="
        $fabric = Join-Path $repo "Fabric\26"
        $modver = (Select-String -Path (Join-Path $fabric "gradle.properties") -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value
        $env:PACK_FORMAT = "$($m.pf)"
        Push-Location $fabric
        & .\gradlew.bat clean build "-Pminecraft_version=$($m.mc)" "-Pfabric_api_version=$($m.api)" "-Ploader_version=$($m.loader)" "-Pmc_lower=$($m.lo)" "-Pmc_upper=$($m.hi)" --no-daemon
        $rc = $LASTEXITCODE; Pop-Location
        Remove-Item Env:PACK_FORMAT -ErrorAction SilentlyContinue
        if ($rc -ne 0) { throw "Fabric FAILED $cell" }
        $jar = Get-ChildItem (Join-Path $fabric "build\libs") -Filter "bank-vault-*.jar" |
            Where-Object { $_.Name -notmatch 'sources' } | Sort-Object LastWriteTime | Select-Object -Last 1
        Copy-Item $jar.FullName (Join-Path $dist ("bank-vault-{0}+{1}-fabric.jar" -f $modver, $cell)) -Force
        Write-Host "  -> dist bank-vault-$modver+$cell-fabric.jar"
    } else {
        throw "Unknown Fabric cell: $cell"
    }
}
Write-Host "Fabric builds complete."
