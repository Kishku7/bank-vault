# Bank Vault - branch `26.2`

Source for the Minecraft **26.2 (pre-release)** line of Bank Vault, organized **loader-on-top**. Client + server mod.
These are standalone builds - no Architectury, no `Common/`.

> **Pre-release line.** Published to Modrinth as **beta** only; no GitHub release is cut until 26.2 is stable. The NeoForge build targets a local NeoForge 26.2 alpha (no public NeoForge 26.2 yet).

## Platforms

- [`Fabric/`](Fabric) - 1 build(s); see its README for versions and exclusions.
- [`NeoForge/`](NeoForge) - 1 build(s); see its README for versions and exclusions.

## Not supported on this line

- **Forge** is not built for the 26.x line - ForgeGradle 6 cannot build unobfuscated Minecraft 26.x and there is no FG7.
- **Quilt** is not supported on the 26.x line - Quilt retired Quilted Fabric API at 26.1, so the Fabric API path Bank Vault uses on Fabric is no longer provided on Quilt for 26.x. (Quilt remains supported on the 1.20.x and 1.21.x branches.)

## Build

Each loader+version folder is its own standalone Gradle build root:

```
cd <Loader>/<version>
./gradlew build      # Windows: .\gradlew.bat build
```

Output: `build/libs/bank-vault-*.jar`. Requires Java 25.

## Links

- Other branches: [`1.20.x`](https://github.com/Kishku7/bank-vault/tree/1.20.x), [`1.21.x`](https://github.com/Kishku7/bank-vault/tree/1.21.x), [`26.1`](https://github.com/Kishku7/bank-vault/tree/26.1)
- Overview: [`main`](https://github.com/Kishku7/bank-vault/tree/main)
- Modrinth: https://modrinth.com/mod/bank-vault
- Releases: https://github.com/Kishku7/bank-vault/releases

By Kishku7. All Rights Reserved.
