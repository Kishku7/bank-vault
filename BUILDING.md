# Bank Vault - Build Guide (`minecraft-1.20-26.3` branch)

The single cross-version source for Bank Vault: **every playable Minecraft version from 1.20.0
through the 26.3 snapshot**, built from one shared code base. Client + server mod. Standalone
per-loader builds - **no Architectury** anywhere, at build time or runtime.

Landing page (what the mod is, downloads): [main branch](https://github.com/Kishku7/bank-vault).
Report issues / support: [mod_support](https://github.com/Kishku7/mod_support/issues).

## What you need installed

- **JDKs** by era: JDK 17 (1.20.x), JDK 21 (1.21.x), JDK 25 (26.x). The build cells select their
  own toolchain; have the ones you intend to build available.
- **Python 3** plus **Cog**: `pip install cogapp` (drives the version-drift codegen).
- **PowerShell 7** (`pwsh`) to run the build walkers.

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
and their plain 26 twins, and also runs the manifest metadata gate (`_metadata.py check`) - run it
before committing.

## Coverage (1.4.1)

| Loader   | Jars | Serves |
|----------|------|--------|
| Fabric   | 12   | 1.20 - 1.20.4, 1.20.5/6, 1.21 - 1.21.11 (every step), 26.1 - 26.3-snapshot |
| NeoForge | 11   | 1.20.1 (via the Forge jar), 1.20.4, 1.20.5/6, 1.21 - 1.21.11, 26.1.2, 26.2 |
| Forge    | 8    | 1.20.1, 1.20.6, 1.21 - 1.21.1, 1.21.5, 1.21.6 - 1.21.8, 1.21.9 - 1.21.10, 1.21.11 |

Forge runs on ForgeGradle 6, whose real ceiling is **1.21.11** (forge 61.x) - the 1.21.10 cell
(forge 60.x) serves 1.21.9 + 1.21.10 and the 1.21.11 cell (forge 61.x) serves 1.21.11.

Known honest gaps: NeoForge MC 26.1/26.1.1 (the loader there lacks `BreakBlockEvent`, added in
26.1.2); NeoForge 1.20.2/1.20.3 (loader era not covered); Forge 1.21.2 / 1.21.3 / 1.21.4 (orphan FG6
builds - pending a check of whether the mod needs the overlay-registration API those drop); 26.x is
Fabric + NeoForge only (FG6 cannot build unobfuscated 26.x). Every claimed (version, loader) pair is
dedicated-server boot-gated before release.

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

`docs/MATRIX.md` (internal, not tracked) records the campaign state and per-era decisions;
`_codegen/compat_core.py` documents every version boundary the build machinery knows about.
