# Bank Vault

Retire the chest room. Build one vault instead.

Bank Vault is a multiblock storage mod: a 3x3 iron **vault door** that opens into a full-screen,
creative-style browser of everything you own - category tabs, smart sorting, search, recursive
container unloading, shared / guild vaults, and capacity you upgrade by feeding it chests. Per-player
data is plain JSON under `config/bankvault/`. Client + server mod.


## Branches

Source is organized by Minecraft line. Inside each branch the code is grouped **loader-on-top**:
`Common/` (shared Architectury code, one folder per MC version) on the 1.20.x / 1.21.x lines, then
`Fabric/`, `Forge/`, `NeoForge/`, each with a sub-folder per Minecraft version. The 26.x branches are
standalone (no Architectury, no `Common/`). `main` (this branch) is the overview.

- [1.20.x](https://github.com/Kishku7/bank-vault/tree/1.20.x) - Minecraft 1.20 - 1.20.6
- [1.21.x](https://github.com/Kishku7/bank-vault/tree/1.21.x) - Minecraft 1.21 - 1.21.11
- [26](https://github.com/Kishku7/bank-vault/tree/26) - Minecraft 26.1 -> 26.3-snapshot-1 (unified line; Fabric + NeoForge)

Open a branch and read its README for loaders, versions, and version exclusions in that line.

## Supported platforms

| MC line | Fabric / Quilt | Forge | NeoForge |
| --- | --- | --- | --- |
| `1.20.x` (1.20 - 1.20.6)  | 1.20 - 1.20.6 (+ Quilt)  | 1.20.1, 1.20.5 - 1.20.6 | 1.20.1 (via the Forge jar), 1.20.2 - 1.20.6 |
| `1.21.x` (1.21 - 1.21.11) | 1.21 - 1.21.11 (+ Quilt) | 1.21 - 1.21.8 | 1.21 - 1.21.11 |
| `26` (unified 26.x) | 26.1 -> 26.3-snapshot-1 | - | 26.1.2 / 26.2 |

- **Forge** is supported through **1.21.8** (the ForgeGradle 6 ceiling - there is no FG7). 1.21.9+ and
  all of 26.x are Fabric + NeoForge.
- **Quilt** runs the Fabric jar on the 1.20.x / 1.21.x lines, but **not on 26.x**: Quilt retired Quilted
  Fabric API at 26.1, so the Fabric API path Bank Vault relies on is no longer provided on Quilt there.
- **26.x** is standalone (no Architectury). Fabric / Quilt builds require **Fabric API**.

## Using Bank Vault

1. **Build the vault door.** Place the iron vault-door block to form the 3x3 multiblock (the in-game
   structure validates itself). Adds one block; no other blocks or items.
2. **Open it** to get the full-screen browser: category tabs, smart sorting, search, and one-click
   recursive unloading of held containers (shulkers, etc.) into the vault.
3. **Grow capacity** by feeding the vault chests (or `/bank upgrade`).
4. **Share it.** Create or join a shared / guild vault and manage membership from the `/bank` command.

### Commands (`/bank`)

- `list` - show your bank / membership.
- `invite <player>`, `accept`, `decline`, `leave`, `disband` - shared / guild vault membership.
- `upgrade` - increase capacity.
- `withdraw ...` - pull items out from the command line.
- `fillall`, `clearall`, `reload` - operator only (permission level 2).

Per-player and shared-vault data is stored as plain JSON under `config/bankvault/`.

## Building from source

Each loader+version folder is its own build root. Check out a branch and build the one you want:

```
# e.g. on the 1.20.x branch:
cd Fabric/1.20.6        && ./gradlew build
cd NeoForge/1.20.4-gap  && ./gradlew build
```

Architectury families (1.20.x / 1.21.x) pull their shared `common` from `../../Common/<version>`
automatically. Each folder has a README describing exactly what it builds.

## Downloads

- Releases: https://github.com/Kishku7/bank-vault/releases
- Modrinth: https://modrinth.com/mod/bank-vault

By Kishku7. All Rights Reserved.
