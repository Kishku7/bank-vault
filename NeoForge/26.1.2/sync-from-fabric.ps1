# Bank Vault NeoForge 26.1.2 â€” sync source from the fabric repo + apply loader transforms.
# Repeatable: run after ANY fabric-side change to re-port. Overlay files always win.
$ErrorActionPreference = 'Stop'
$SRC = Join-Path (Split-Path $PSScriptRoot -Parent) 'fabric\src\main'  # sibling fabric source in this branch
$DST = Join-Path $PSScriptRoot 'src\main'
$J   = Join-Path $DST 'java\com\kishku7\bankvault'

# 1) java + data + assets (resources root files like fabric.mod.json / accesswidener NOT copied)
robocopy "$SRC\java" "$DST\java" /MIR /NFL /NDL /NJH /NJS | Out-Null
robocopy "$SRC\resources\assets" "$DST\resources\assets" /MIR /NFL /NDL /NJH /NJS | Out-Null
robocopy "$SRC\resources\data" "$DST\resources\data" /MIR /NFL /NDL /NJH /NJS | Out-Null

# 2) fabric-only files replaced/removed
Remove-Item "$J\client\BankVaultClient.java" -Force -ErrorAction SilentlyContinue

# 3) transforms
function T($file, $pairs) {
    $t = [IO.File]::ReadAllText($file)
    foreach ($p in $pairs) {
        if (-not $t.Contains($p[0])) { Write-Output ("  MISS in {0}: {1}" -f (Split-Path $file -Leaf), $p[0].Substring(0,[Math]::Min(60,$p[0].Length))) }
        $t = $t.Replace($p[0], $p[1])
    }
    [IO.File]::WriteAllText($file, $t, [Text.UTF8Encoding]::new($false))
}

T "$J\client\BankVaultScreen.java" @(
  ,@('import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;', 'import net.neoforged.neoforge.client.network.ClientPacketDistributor;')
  ,@('ClientPlayNetworking.send(', 'ClientPacketDistributor.sendToServer(')
)

$loaderFiles = @('vault\Catalog.java','vault\Keywords.java','vault\BankManager.java','vault\UserSettings.java','client\ButtonLayout.java','inventory\ContainerExtractor.java')
foreach ($f in $loaderFiles) {
  T "$J\$f" @(
    ,@("import net.fabricmc.loader.api.FabricLoader;", "import net.neoforged.fml.ModList;`nimport net.neoforged.fml.loading.FMLPaths;")
    ,@('FabricLoader.getInstance().getConfigDir()', 'FMLPaths.CONFIGDIR.get()')
    ,@('FabricLoader.getInstance().isModLoaded(', 'ModList.get().isLoaded(')
  )
}

T "$J\inventory\BankVaultMenu.java" @(
  ,@('net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("trinkets_updated")', 'com.kishku7.bankvault.BankVault.TRINKETS')
)

# 4) overlay wins (entry, client, networking, registries, trinket stub, BankVault holder)
robocopy "$PSScriptRoot\overlay\java" "$DST\java" /E /NFL /NDL /NJH /NJS | Out-Null

# 5) gate: no fabric references may remain
$left = Get-ChildItem "$DST\java" -Recurse -Filter *.java | Select-String -Pattern 'net\.fabricmc'
if ($left) { $left | ForEach-Object { Write-Output ("FABRIC LEFT: {0}:{1}" -f $_.Path, $_.LineNumber) }; exit 1 }
Write-Output 'sync clean - no fabric refs remain'

