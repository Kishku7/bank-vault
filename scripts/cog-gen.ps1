# cog-gen.ps1 -- materialize a pre-26 build cell's gen/ tree from the one shared source.
# Usage: pwsh -File scripts\cog-gen.ps1 -Cell Fabric/1.21.8   [-SrcLoader forge]
# The 26 cells do NOT use cog-gen (they srcDir shared_minecraft directly).
# gen/ is disposable build output (gitignored). Edit ONLY _codegen/cog_sources + shared_minecraft.
param(
    [Parameter(Mandatory)][string]$Cell,
    [string]$SrcLoader                       # override source flavour (e.g. NeoForge/1.20.1 is forge-shaped)
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$parts = $Cell -split '[/\\]'
$LoaderDir = $parts[0]; $McVer = $parts[1]
$Loader = $LoaderDir.ToLower()
if ($SrcLoader) { $Loader = $SrcLoader.ToLower() }
$cg = Join-Path $repoRoot '_codegen'
$cs = Join-Path $cg 'cog_sources'
$cell = Join-Path $repoRoot ($LoaderDir + '\' + $McVer)
if (-not (Test-Path $cell)) { throw "cell not found: $cell" }
$gen = Join-Path $cell 'gen'
$pkg = 'com\kishku7\bankvault'
$genJ = Join-Path $gen ('src\main\java\' + $pkg)
$genR = Join-Path $gen 'src\main\resources'

$v = [version]$McVer
$pluralData = $v -lt [version]'1.21'      # plural datapack dirs era
$itemDefs   = $v -ge [version]'1.21.4'    # assets/<ns>/items/ item model definitions exist

# ---- 1. wipe gen/, copy shared_minecraft java verbatim ----
Remove-Item $gen -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $genJ, $genR | Out-Null
Copy-Item (Join-Path $repoRoot ('shared_minecraft\src\main\java\' + $pkg + '\*')) $genJ -Recurse -Force

# ---- 2. overwrite drift files with the cog-instrumented shared copies ----
if (Test-Path (Join-Path $cs 'shared')) {
    Copy-Item (Join-Path $cs 'shared\*') $genJ -Recurse -Force
}

# ---- 3. loader-specific files (per-loader cog_sources tree mirrors the package layout) ----
$L = Join-Path $cs $Loader
if (Test-Path $L) {
    Copy-Item (Join-Path $L '*') $genJ -Recurse -Force
}

# ---- 4. resources: shared assets/data with era corrections ----
$shR = Join-Path $repoRoot 'shared_minecraft\src\main\resources'
Copy-Item (Join-Path $shR 'assets') (Join-Path $genR 'assets') -Recurse -Force
Copy-Item (Join-Path $shR 'data')   (Join-Path $genR 'data')   -Recurse -Force
if (-not $itemDefs) {
    # item model definitions (assets/<ns>/items/) do not exist before 1.21.4
    Remove-Item (Join-Path $genR 'assets\bankvault\items') -Recurse -Force -ErrorAction SilentlyContinue
}
if ($pluralData) {
    # pre-1.21 datapack layout uses PLURAL folder names
    $d = Join-Path $genR 'data\bankvault'
    if (Test-Path (Join-Path $d 'advancement')) { Rename-Item (Join-Path $d 'advancement') 'advancements' }
    if (Test-Path (Join-Path $d 'loot_table'))  { Rename-Item (Join-Path $d 'loot_table')  'loot_tables' }
    if (Test-Path (Join-Path $d 'recipe'))      { Rename-Item (Join-Path $d 'recipe')      'recipes' }
    $t = Join-Path $genR 'data\minecraft\tags'
    if (Test-Path (Join-Path $t 'block'))       { Rename-Item (Join-Path $t 'block')       'blocks' }
}

# ---- 5. pack.mcmeta (plain int pre-26; table lives in compat_core.PACK_FORMATS) ----
Push-Location $cg
$pf = & python (Join-Path $cg 'print_pf.py') $McVer
Pop-Location
if ($LASTEXITCODE -ne 0 -or -not $pf) { throw "no pack_format for $McVer -- extend compat_core.PACK_FORMATS" }
('{"pack":{"description":"Bank Vault resources","pack_format":' + $pf + '}}') |
    Set-Content (Join-Path $genR 'pack.mcmeta') -Encoding UTF8

# ---- 6. run cog on every marker file in gen ----
$env:PYTHONDONTWRITEBYTECODE = '1'
Get-ChildItem (Join-Path $gen 'src\main\java') -Recurse -File -Filter *.java |
    Where-Object { (Get-Content $_.FullName -Raw) -match '\[\[\[cog' } | ForEach-Object {
        & cog -r -I $cg -D loader=$Loader -D ver=$McVer -D codegen=$cg $_.FullName | Out-Null
        if ($LASTEXITCODE -ne 0) { throw ("cog failed: " + $_.FullName) }
    }
Write-Host ("cog-gen OK: {0} (loader={1} pluralData={2} itemDefs={3} pf={4})" -f $Cell, $Loader, $pluralData, $itemDefs, $pf)
