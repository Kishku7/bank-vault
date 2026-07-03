# Bank Vault - branch `minecraft-1.20-26.3`

The single cross-version source for Bank Vault: **every playable Minecraft version from 1.20.0
through the 26.3 snapshot**, built from one shared code base. Client + server mod. Standalone
per-loader builds - **no Architectury** anywhere, at build time or runtime.

## Layout

```
shared_minecraft/        ONE business-code source (blocks, vault, menu, screen, commands, payloads)
_codegen/                the version-drift brain: compat_*.py + cog_sources/ (era-emitted files)
Fabric/<ver>/            thin build cells (loom); Fabric/26 = parameterized 26.x matrix cell
NeoForge/<ver>/          thin build cells (ModDevGradle; 1.20.4 = NeoGradle 7); NeoForge/26 = matrix
Forge/<ver>/             thin build cells (ForgeGradle 6)
scripts/                 build-fabric / build-neoforge / build-forge walkers, cog-gen, check-sync
dist/                    every release jar (line-keyed names), written by the walkers
```

Pre-26 cells build from a cog-materialized `gen/` tree (`scripts/cog-gen.ps1`); the 26 cells build
straight from `shared_minecraft` except 26.3+, which also materializes (26.3-snapshot-2 removed the
block `MapCodec` surface). `scripts/check-sync.ps1` is the drift tripwire between the cog sources
and their plain 26 twins - run it before committing.

## Coverage (1.4.0)

| Loader   | Jars | Serves |
|----------|------|--------|
| Fabric   | 12   | 1.20 - 1.20.4, 1.20.5/6, 1.21 - 1.21.11 (every step), 26.1 - 26.3-snapshot |
| NeoForge | 11   | 1.20.1 (via the Forge jar), 1.20.4, 1.20.5/6, 1.21 - 1.21.11, 26.1.2, 26.2 |
| Forge    | 6    | 1.20.1, 1.20.6, 1.21 - 1.21.1, 1.21.5, 1.21.6 - 1.21.8 |

Known honest gaps: NeoForge MC 26.1/26.1.1 (the loader there lacks `BreakBlockEvent`, added in
26.1.2); Forge 1.21.9+ (no FG7); NeoForge 1.20.2/1.20.3 (loader era not covered). Every claimed
(version, loader) pair is dedicated-server boot-gated before release.

## Build

```
pwsh -File scripts/build-fabric.ps1     [cells...]   # all Fabric cells -> dist/
pwsh -File scripts/build-neoforge.ps1   [cells...]
pwsh -File scripts/build-forge.ps1      [cells...]
```

## Automation surface (1.4.0)

A hidden machine-readable command layer for automation clients (e.g. the M1 agent):
`/bank api snapshot|list [page]|count <key>|find <query>|withdraw <n> <key>|deposit <n> <hand|id>`.
Replies are single plain-ASCII lines: `BV|<op>|OK|...` / `BV|<op>|ERR|<reason>`. Keys are the
bank's native keys (plain item ids, or `id#hash` for component-bearing stacks). Additive only -
older clients are unaffected.

MATRIX.md records the campaign state and per-era decisions; `_codegen/compat_core.py` documents
every version boundary the build machinery knows about.
