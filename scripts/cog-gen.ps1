# cog-gen.ps1 -- materialize a pre-26 build cell's gen/ tree from the one shared source.
# Usage: pwsh -File scripts\cog-gen.ps1 -Cell Fabric/1.21.8   [-SrcLoader forge]
# D16 (2026-07-10): ALL cells use cog-gen; shared_minecraft eliminated (shared code -> cog_sources).
# gen/ is disposable build output (gitignored). Edit ONLY _codegen/cog_sources + shared_minecraft.
param(
    [Parameter(Mandatory)][string]$Cell,
    [string]$SrcLoader,                      # override source flavour (e.g. NeoForge/1.20.1 is forge-shaped)
    [string]$Ver                             # version override for the parameterized 26 cells (e.g. -Cell Fabric/26 -Ver 26.3)
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$parts = $Cell -split '[/\\]'
$LoaderDir = $parts[0]; $CellDir = $parts[1]; $McVer = $parts[1]
if ($Ver) { $McVer = $Ver }
$Loader = $LoaderDir.ToLower()
if ($SrcLoader) { $Loader = $SrcLoader.ToLower() }
$cg = Join-Path $repoRoot '_codegen'
$cs = Join-Path $cg 'cog_sources'
$cell = Join-Path $repoRoot ($LoaderDir + '\' + $CellDir)
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
# D16: shared java now lives in cog_sources/shared (laid down by step 2); shared_minecraft eliminated

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
$shR = Join-Path $repoRoot '_codegen\cog_sources\shared_resources'  # D16: shared resources relocated
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

# ---- 4b. recipe/advancement JSON era schemas (shared files are 26/new-schema shaped) ----
# < 1.21.2: ingredient strings must be objects/arrays ("#": "id" -> {"item": id};
#           inventory_changed "items": "id" -> ["id"]).
# < 1.20.5: shaped-recipe result uses "item", not "id".
$oldIngredients = $v -lt [version]'1.21.2'
$oldResult      = $v -lt [version]'1.20.5'
if ($oldIngredients) {
    $recipeDir = if ($pluralData) { 'recipes' } else { 'recipe' }
    $advDir    = if ($pluralData) { 'advancements' } else { 'advancement' }
    $rp = Join-Path $genR ('data\bankvault\' + $recipeDir + '\bank_vault.json')
    if (Test-Path $rp) {
        $r = Get-Content $rp -Raw | ConvertFrom-Json
        $newKey = @{}
        foreach ($k in $r.key.PSObject.Properties) {
            if ($k.Value -is [string]) { $newKey[$k.Name] = @{ item = $k.Value } }
            else { $newKey[$k.Name] = $k.Value }
        }
        $r.key = [pscustomobject]$newKey
        if ($oldResult -and $r.result.id) {
            $r.result = [pscustomobject]@{ item = $r.result.id; count = $r.result.count }
        }
        $r | ConvertTo-Json -Depth 10 | Set-Content $rp -Encoding UTF8
    }
    $ap = Join-Path $genR ('data\bankvault\' + $advDir + '\recipes\bank_vault.json')
    if (Test-Path $ap) {
        $a = Get-Content $ap -Raw | ConvertFrom-Json
        foreach ($c in $a.criteria.PSObject.Properties) {
            $cond = $c.Value.conditions
            if ($cond -and $cond.items) {
                foreach ($it in $cond.items) {
                    if ($it.items -is [string]) { $it.items = @($it.items) }
                }
            }
        }
        $a | ConvertTo-Json -Depth 12 | Set-Content $ap -Encoding UTF8
    }
}

# ---- 4c. 26.3-snapshot-5: recipe_unlocked advancement trigger takes a LIST "recipes" (was "recipe") ----
if ($v -ge [version]'26.3') {
    $advDir2 = if ($pluralData) { 'advancements' } else { 'advancement' }
    $ap2 = Join-Path $genR ('data\bankvault\' + $advDir2 + '\recipes\bank_vault.json')
    if (Test-Path $ap2) {
        $a2 = Get-Content $ap2 -Raw | ConvertFrom-Json
        foreach ($c in $a2.criteria.PSObject.Properties) {
            $cond = $c.Value.conditions
            if ($cond -and $cond.PSObject.Properties['recipe']) {
                $rv = $cond.recipe
                $cond.PSObject.Properties.Remove('recipe')
                $cond | Add-Member -NotePropertyName 'recipes' -NotePropertyValue (@($rv)) -Force
            }
        }
        $a2 | ConvertTo-Json -Depth 12 | Set-Content $ap2 -Encoding UTF8
    }
}

# ---- 5. pack.mcmeta (plain int pre-26; table lives in compat_core.PACK_FORMATS) ----
Push-Location $cg
$pf = & python (Join-Path $cg 'print_pf.py') $McVer
Pop-Location
if ($LASTEXITCODE -ne 0 -or -not $pf) { throw "no pack_format for $McVer -- extend compat_core.PACK_FORMATS" }
if ($v -ge [version]'26.0') {
    # 26.x: pack_format > 81 -> the strict codec demands the exact-single range form
    ('{"pack":{"description":"Bank Vault resources","pack_format":' + $pf + ',"min_format":' + $pf + ',"max_format":' + $pf + '}}') |
        Set-Content (Join-Path $genR 'pack.mcmeta') -Encoding UTF8
} elseif ([int]$pf -gt 64) {
    # DEAD ZONE (resource major 65-81: 1.21.9/1.21.10=69, 1.21.11=75). The client resource codec and
    # the server data codec disagree on one file, so make BOTH work (knowledge/pack-formats.md RESOLVED
    # 2026-07-12; mod-audit-doctrine D4): Fabric + NeoForge ship NO pack.mcmeta (each loader synthesises
    # the correct per-type metadata); Forge ships the exact range on the DATA major (both codecs new-era).
    if ($Loader -eq 'forge') {
        $dataMajors = @{ '1.21.9' = 88; '1.21.10' = 88; '1.21.11' = 94 }
        $dm = $dataMajors[$McVer]
        if (-not $dm) { throw "no dead-zone data-major for $McVer -- extend the table (knowledge/pack-formats.md)" }
        ('{"pack":{"description":"Bank Vault resources","pack_format":' + $dm + ',"min_format":' + $dm + ',"max_format":' + $dm + '}}') |
            Set-Content (Join-Path $genR 'pack.mcmeta') -Encoding UTF8
    } else {
        Remove-Item (Join-Path $genR 'pack.mcmeta') -Force -ErrorAction SilentlyContinue
    }
} else {
    ('{"pack":{"description":"Bank Vault resources","pack_format":' + $pf + '}}') |
        Set-Content (Join-Path $genR 'pack.mcmeta') -Encoding UTF8
}

# ---- 5c. parameterized 26 cells: fold the cell's own static resources into gen (the gen
# pack.mcmeta wins -- it already carries the era-correct range form) ----
if ($Ver) {
    Get-ChildItem (Join-Path $cell 'src\main\resources') -Recurse -File | Where-Object { $_.Name -ne 'pack.mcmeta' } | ForEach-Object {
        $rel = $_.FullName.Substring((Join-Path $cell 'src\main\resources').Length + 1)
        $t = Join-Path $genR $rel
        New-Item -ItemType Directory -Force -Path (Split-Path $t) | Out-Null
        Copy-Item $_.FullName $t -Force
    }
}

# ---- 6. run cog on every marker file in gen ----
$env:PYTHONDONTWRITEBYTECODE = '1'
Get-ChildItem (Join-Path $gen 'src\main\java') -Recurse -File -Filter *.java |
    Where-Object { (Get-Content $_.FullName -Raw) -match '\[\[\[cog' } | ForEach-Object {
        & cog -r -I $cg -D loader=$Loader -D ver=$McVer -D codegen=$cg $_.FullName | Out-Null
        if ($LASTEXITCODE -ne 0) { throw ("cog failed: " + $_.FullName) }
    }
# ---- 7f. forge-47 deprecation hygiene: Forge 1.20.1 deprecates the vanilla BuiltInRegistries
# fields (ForgeRegistries preferred); they remain the correct cross-loader access and work at
# runtime, so the DISPOSABLE gen tree gets a class-level suppression with this justification. ----
if ($Loader -eq 'forge' -and $v -lt [version]'1.20.2') {
    Get-ChildItem (Join-Path $gen 'src\main\java') -Recurse -File -Filter *.java | ForEach-Object {
        $t = Get-Content $_.FullName -Raw
        if ($t -match 'BuiltInRegistries\.' -and $t -notmatch '@SuppressWarnings\("deprecation"\)\r?\npublic') {
            $t = $t -replace '(?m)^(public (final )?(class|record|interface) )', ('@SuppressWarnings("deprecation") // Forge 47 deprecates vanilla BuiltInRegistries; still the correct cross-loader access' + [char]10 + '$1')
            Set-Content $_.FullName $t -NoNewline
        }
    }
}

# ---- 7e. ResourceLocation factory -> ctor below 1.21 (Forge backported the factory at 1.20.4) ----
$rlFactory = ($v -ge [version]'1.21') -or ($Loader -eq 'forge' -and $v -ge [version]'1.20.4')
if (-not $rlFactory) {
    Get-ChildItem (Join-Path $gen 'src\main\java') -Recurse -File -Filter *.java | ForEach-Object {
        $t = Get-Content $_.FullName -Raw
        if ($t -match '(ResourceLocation|Identifier)\.fromNamespaceAndPath\(') {
            $t = $t -replace '(ResourceLocation|Identifier)\.fromNamespaceAndPath\(', 'new ResourceLocation('
            Set-Content $_.FullName $t -NoNewline
        }
    }
}

# ---- 7d. registry getValue -> get rename below 1.21.2 (Registry.getValue introduced 1.21.2) ----
if ($v -lt [version]'1.21.2') {
    Get-ChildItem (Join-Path $gen 'src\main\java') -Recurse -File -Filter *.java | ForEach-Object {
        $t = Get-Content $_.FullName -Raw
        if ($t -match 'BuiltInRegistries\.[A-Z_]+(\s|\r|\n)*\.getValue\(') {
            $t = $t -replace '(?s)(BuiltInRegistries\.[A-Z_]+(\s*\r?\n\s*)?)\.getValue\(', '$1.get('
            Set-Content $_.FullName $t -NoNewline
        }
    }
}

# ---- 7c. GameProfile record-accessor rename: name() from 1.21.9; getName() before ----
if ($v -lt [version]'1.21.9') {
    Get-ChildItem (Join-Path $gen 'src\main\java') -Recurse -File -Filter *.java | ForEach-Object {
        $t = Get-Content $_.FullName -Raw
        if ($t -match 'Profile\(\)\.name\(\)') {
            $t = $t -replace 'getGameProfile\(\)\.name\(\)', 'getGameProfile().getName()'
            $t = $t -replace 'getProfile\(\)\.name\(\)', 'getProfile().getName()'
            Set-Content $_.FullName $t -NoNewline
        }
    }
}

# ---- 7b. mojmap rename pass: Identifier -> ResourceLocation below 1.21.11 (same class, pure
# mojmap rename at 1.21.11; a word-boundary tree rename in the disposable gen/ is exactly correct) ----
if ($v -lt [version]'1.21.11') {
    Get-ChildItem (Join-Path $gen 'src\main\java') -Recurse -File -Filter *.java | ForEach-Object {
        $t = Get-Content $_.FullName -Raw
        if ($t -match '\bIdentifier\b') {
            $t = $t -replace '\bIdentifier\b', 'ResourceLocation'
            Set-Content $_.FullName $t -NoNewline
        }
    }
}

Write-Host ("cog-gen OK: {0} (loader={1} pluralData={2} itemDefs={3} pf={4})" -f $Cell, $Loader, $pluralData, $itemDefs, $pf)
