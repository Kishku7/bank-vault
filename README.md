# Bank Vault

Retire the chest room. Build one vault instead.

Bank Vault is a multiblock storage mod: a 3x3 iron "vault door" that opens into a
full-screen, creative-style browser of everything you own - category tabs, smart
sorting, search, recursive container unloading, shared/guild vaults, and capacity you
upgrade by feeding it chests. Per-player data is plain JSON under config/bankvault/.

## Supported platforms

Source for each Minecraft version lives on its own branch, named for the version.
`main` (this branch) is just the overview.

| Branch    | Minecraft        | Fabric | Quilt | Forge | NeoForge |
| ---       | ---              | :---:  | :---: | :---: | :---:    |
| `1.20.4`  | 1.20 - 1.20.4    | Yes    | Yes   | Yes   | Yes      |
| `1.20.6`  | 1.20.5 - 1.20.6  | Yes    | Yes   | -     | Yes      |
| `1.21.1`  | 1.21 - 1.21.1    | Yes    | Yes   | -     | Yes      |
| `1.21.5`  | 1.21.2 - 1.21.5  | Yes    | Yes   | -     | Yes      |
| `1.21.8`  | 1.21.6 - 1.21.8  | Yes    | Yes   | -     | Yes      |
| `1.21.11` | 1.21.9 - 1.21.11 | Yes    | Yes   | -     | Yes      |
| `26.1.2`  | 26.1.2           | Yes    | Yes   | -     | Yes      |

Quilt runs the Fabric build. Classic Forge ends at the 1.20.x family; newer families
are Fabric + NeoForge (plus Quilt). Requires Fabric API on Fabric/Quilt.

## Building from source

Check out the branch for your Minecraft version, then:

    ./gradlew :fabric:build        # or :neoforge:build / :forge:build
    # 26.1.2 branch: cd fabric && ./gradlew build   (and cd neoforge && ./gradlew build)

Each branch carries a BUILD.md with the exact loader tasks and universal-jar merge info.

## Downloads

- Releases: https://github.com/Kishku7/bank-vault/releases
- Modrinth: https://modrinth.com/mod/bank-vault

By Kishku7. All Rights Reserved.
