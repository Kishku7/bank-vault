# check-sync.ps1 -- drift tripwire between the cog sources and their PLAIN 26-cell twins.
# The 26 cells never run cog, so shared_minecraft + the 26 loader cells keep plain copies of
# files that also exist as cog sources. This materializes each cog source at 26.1 and compares
# CODE (comments/blank/package lines ignored) against the plain twin. Exit 1 on drift.
# Twins are AUTO-DISCOVERED: cog_sources/shared/<rel> -> shared_minecraft/.../<rel>;
# cog_sources/<loader>/<rel> -> <Loader>/26/src/main/java/com/kishku7/bankvault/<rel>.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$cg = Join-Path $repoRoot '_codegen'
$cs = Join-Path $cg 'cog_sources'
$pkg = 'com\kishku7\bankvault'
$tmp = Join-Path $env:TEMP ('bv-checksync-' + [guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

function Normalize($path) {
    $out = New-Object System.Collections.Generic.List[string]
    $inBlock = $false
    foreach ($ln in (Get-Content $path)) {
        $t = $ln.Trim()
        if ($inBlock) { if ($t -match '\*/') { $inBlock = $false }; continue }
        if ($t -match '^/\*' ) { if ($t -notmatch '\*/') { $inBlock = $true }; continue }
        if ($t -eq '' -or $t.StartsWith('//') -or $t.StartsWith('*') -or $t.StartsWith('package ')) { continue }
        $out.Add($t)
    }
    return $out
}

$loaderDirName = @{ fabric = 'Fabric'; forge = 'Forge'; neoforge = 'NeoForge' }
$pairs = @()
foreach ($flavour in 'shared', 'fabric', 'neoforge') {
    $root = Join-Path $cs $flavour
    if (-not (Test-Path $root)) { continue }
    Get-ChildItem $root -Recurse -File -Filter *.java | ForEach-Object {
        $rel = $_.FullName.Substring($root.Length + 1)
        if ($flavour -eq 'shared') {
            $plain = Join-Path $repoRoot ('shared_minecraft\src\main\java\' + $pkg + '\' + $rel)
            $ldr = 'neoforge'   # shared files materialize loader-neutrally; neoforge emits no @Environment
        } else {
            $plain = Join-Path $repoRoot ($loaderDirName[$flavour] + '\26\src\main\java\' + $pkg + '\' + $rel)
            $ldr = $flavour
        }
        if (Test-Path $plain) {
            $pairs += @{ src = $_.FullName; loader = $ldr; plain = $plain; rel = "$flavour/$rel" }
        }
        # forge cog sources have no 26 twin (no Forge 26 cell) -- nothing to check there
    }
}

$env:PYTHONDONTWRITEBYTECODE = '1'
$fail = 0
foreach ($p in $pairs) {
    $mat = Join-Path $tmp ([IO.Path]::GetFileName($p.src))
    Copy-Item $p.src $mat -Force
    if ((Get-Content $mat -Raw) -match '\[\[\[cog') {
        & cog -r -I $cg -D ("loader=" + $p.loader) -D ver=26.1 -D codegen=$cg $mat | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Host ("COG FAIL " + $p.rel); $fail = 1; continue }
    }
    $a = Normalize $mat
    $b = Normalize $p.plain
    $diff = Compare-Object $a $b
    if ($diff) {
        Write-Host ("DRIFT " + $p.rel + " (" + @($diff).Count + " lines):")
        $diff | Select-Object -First 10 | ForEach-Object { Write-Host ("  " + $_.SideIndicator + " " + $_.InputObject) }
        $fail = 1
    } else {
        Write-Host ("ok    " + $p.rel)
    }
}
Remove-Item $tmp -Recurse -Force
if ($pairs.Count -eq 0) { Write-Host 'no cog-source twins yet (nothing to check)' }
if ($fail) { exit 1 }
Write-Host 'check-sync PASSED'
