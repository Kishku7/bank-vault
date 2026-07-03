# Bank Vault -- Unification Campaign MATRIX (authoritative state)

Campaign start: 2026-07-03 ~00:45. Branch: `minecraft-1.20-26.3`. Target version: **1.4.0**.
Runbook: Memory projects/mod-consolidation-guide.md. Copy-from: lava-boats minecraft-1.20-26.3.
Coverage model: M1's full-coverage segmentation (every playable MC 1.20.0 -> 26.3-snapshot)
PLUS NeoForge/1.20.4 (BV already published that line; M1 skipped it).

## Locked decisions
- Version 1.4.0 (minor bump over highest line 1.3.0; unification + new features).
- Build order: VERSION-MAJOR (one MC version across all loaders, then next) -- Dave 2026-07-03.
- Architectury: TOTAL removal (Dave explicit + standing hard rule). No arch at build or runtime.
- New feature scope: hidden AI/M1-friendly surface (machine-readable outputs, stable id#hash keys,
  query commands). Additive only -- 1.2.x/1.3.0 clients stay protocol-compatible.
- Publish HELD. Old branches retired only after their line passes the exhaustive boot gate.
- Dot injector ON (id 7434851a) -- DISABLE at campaign end.

## Cell matrix (dist jar = X; "26" = matrix cell building 26.1/26.2/26.3)

| MC cell | Fabric | NeoForge | Forge | Notes |
|---------|--------|----------|-------|-------|
| 1.20    | [ ]    | --       | --    | new; covers 1.20.0-1.20.4 era per M1 pattern |
| 1.20.1  | --     | --       | [ ]   | new; Forge jar also serves NeoForge <=1.20.1 (mod-deploy cutover) |
| 1.20.4  | --     | [ ]      | --    | keep published NeoForge 1.20.4 line (ex 1.20.4-gap) |
| 1.20.6  | [ ]    | [ ]      | [ ]   | existing line (arch -> rebuild) |
| 1.21    | [ ]    | [ ]      | [ ]   | new |
| 1.21.1  | [ ]    | [ ]      | [ ]   | existing (arch -> rebuild; Forge cell already FG6) |
| 1.21.2  | [ ]    | [ ]      | --    | new |
| 1.21.5  | [ ]    | [ ]      | [ ]   | existing |
| 1.21.6  | [ ]    | --       | --    | new (M1 pattern) |
| 1.21.8  | [ ]    | [ ]      | [ ]   | existing; Forge ceiling (FG6) |
| 1.21.11 | [ ]    | [ ]      | --    | existing |
| 26      | [ ] x3 | [ ] x2   | --    | existing non-arch matrix cells (moved to <Loader>/26/) |

Jar count: Fabric 13, NeoForge 10, Forge 6 = **29 jars**.
(M1 reference = 27; +1 NeoForge/1.20.4, +1 because M1's NeoForge lacks 1.20.4 -- verify exact M1
cell ranges when building; if a listed new cell proves a loader gap (loader never shipped for that
MC), record it in mod-version-gates.md and mark `--` here instead.)

## Era model (initial; refine as compiler enumerates drift)
- 1.20-1.20.4: JDK 17, old toml/fabric.mod.json era, pre-component ItemStack NBT era
- 1.20.6-1.21.1: JDK 21, components era, pre-1.21.2 mixin-level JAVA_17 on forge/neoforge
- 1.21.2-1.21.11: JDK 21, modern loaders
- 26.x: mojmap-shipped, matrix-templated cells, pack_format range form

## Stage checklist
- [x] Stage 0.1 context loaded (guide, bank-vault.md, repo survey, M1 matrix)
- [x] Stage 0.2 pre-unify/* tags (1.20.x, 1.21.x, 26, main, tooling)
- [x] Stage 0.3 branch minecraft-1.20-26.3 from 26 + worktree
- [x] Stage 0.4 restructure: <Loader>/26/ cells, srcDirs fixed, scripts/ (walkers to be rewritten)
- [x] Stage 0.5 MATRIX.md (this file)
- [ ] Stage 0.6 project file + handoff updated with kickoff (handoff done 00:55; bank-vault.md pending)
- [ ] Stage 1 brains + arch Common merge + cog-gen.ps1 + check-sync.ps1
- [ ] Stage 2 cells version-major 1.20 -> 26.3 (pilot 26 line first to prove moved cells still build)
- [ ] Stage 2b M1 AI-feature layer (design after m1.md + m1-agent.md read)
- [ ] Stage 3 exhaustive boot gate + branch retirement
- [ ] Stage 4 cleanup + docs + push; injector OFF; publish HELD

## Session notes (append-only)
- 2026-07-03 00:58 Stage 0 restructure done. dist/ was untracked in old 26 worktree -- mod-deploy
  staging_dirs still points at `26\dist`; repoint to this worktree's dist/ at Stage 2.6.
- 26.3 matrix entry in build-fabric.ps1 pins 26.3-snapshot-1; bump to current snapshot at Stage 2.
- 2026-07-03 01:25 26-line convergence: 8 loader-duplicated classes -> shared_minecraft via new Platform + ClientNet seams (config-dir/isLoaded/client-send drift). Both 26 cells rebuilt green (Fabric+NeoForge 26.2 pilot). Remaining per-loader 26 files: entrypoints, ModNetworking, TrinketCompat, registry seams, BankVaultClient.
- 2026-07-03 01:50 M1 AI surface added: api/VaultApi.java (snapshot/list/count/find/withdraw/deposit, single-line BV|op|OK pipe format) + hidden /bank api subcommand in BankCommand. Version bumped 1.4.0 both 26 cells. Fabric+NeoForge 26.2 build green.
- 2026-07-03 02:05 _codegen skeleton in place: compat_core.py (BV drift axes: renamed@1.21.11, components@1.20.5, perms26, pluralData<1.21, itemDefs@1.21.4, PACK_FORMATS, SLOT_CHANGED_SIG per-era AW/AT table), cog-gen.ps1 (BV era rules: data-dir renames, items/ strip, pack.mcmeta), check-sync.ps1 (auto-discovering twins). Next: first pre-26 cell pair (Fabric+NeoForge 1.21.11) via M1 cell wiring; let the compiler enumerate drift.
