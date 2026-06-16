# Bank Vault

Retire the chest room. Build one vault instead.

Bank Vault is a multiblock storage mod: a 3x3 iron "vault door" that opens into a
full-screen, creative-style browser of everything you own - category tabs, smart
sorting, search, recursive container unloading, shared/guild vaults, and capacity you
upgrade by feeding it chests. Per-player data is plain JSON under config/bankvault/.
Client + server mod.

[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/2ZxzbCzAHe)

## Branches

Source is organized by Minecraft line. Inside each branch the code is grouped
**loader-on-top**: `Common/` (shared Architectury code, one folder per MC version),
then `Fabric/`, `Forge/`, `NeoForge/`, each with a subfolder per Minecraft version.
`main` (this branch) is the overview.

- [1.20.x](https://github.com/Kishku7/bank-vault/tree/1.20.x) — Minecraft 1.20 – 1.20.6
- [1.21.x](https://github.com/Kishku7/bank-vault/tree/1.21.x) — Minecraft 1.21 – 1.21.11
- [26.1](https://github.com/Kishku7/bank-vault/tree/26.1) — Minecraft 26.1 – 26.1.2
- [26.2](https://github.com/Kishku7/bank-vault/tree/26.2) — Minecraft 26.2 (pre-release)

## Supported platforms

| MC line | Fabric / Quilt | Forge | NeoForge |
| --- | --- | --- | --- |
| `1.20.x` (1.20 – 1.20.6)  | 1.20 – 1.20.6  | 1.20.1 | 1.20.1 (via the Forge jar), 1.20.2 – 1.20.4, 1.20.5 – 1.20.6 |
| `1.21.x` (1.21 – 1.21.11) | 1.21 – 1.21.11 | —      | 1.21 – 1.21.11 |
| `26.1` (26.1 – 26.1.2)    | 26.1 – 26.1.2  | —      | 26.1 – 26.1.2 |
| `26.2` (pre-release)      | 26.2           | —      | 26.2 |

Quilt runs the Fabric build. Classic Forge ends with the 1.20.x line; newer lines are
Fabric + NeoForge (plus Quilt). 26.x is standalone (no Architectury). Requires Fabric API
on Fabric / Quilt.

## Building from source

Each loader+version folder is its own build root. Check out a branch and build the one you want:

    # e.g. on the 1.20.x branch:
    cd Fabric/1.20.6   && ./gradlew build
    cd NeoForge/1.20.4-gap && ./gradlew build

Architectury families pull their shared `common` from `../../Common/<version>` automatically.
Each folder has a README describing what it builds.

## Downloads

- Releases: https://github.com/Kishku7/bank-vault/releases
- Modrinth: https://modrinth.com/mod/bank-vault

By Kishku7. All Rights Reserved.
