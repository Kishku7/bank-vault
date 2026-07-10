# Bank Vault

Retire the chest room. Build one vault instead.

Bank Vault is a multiblock storage mod: a 3x3 iron vault door that opens into a full-screen,
creative-style browser of everything you own. One block to place, and the chest walls are gone.

## Why Bank Vault

- **One screen for everything.** Category tabs, smart sorting, and search across your whole
  inventory - no more digging through rows of chests.
- **Suck in containers in one click.** Recursively unload shulkers and other held containers
  straight into the vault.
- **Capacity you grow.** Feed the vault chests (or `/bank upgrade`) to expand it over time.
- **Share it.** Create or join a shared / guild vault and manage membership from `/bank`.
- **Your data stays yours.** Per-player and shared-vault data is plain JSON under
  `config/bankvault/`.

## Usage

1. Place the iron vault-door block to form the 3x3 multiblock - it validates itself in-world.
2. Open it for the full-screen browser: tabs, sorting, search, and one-click container unloading.
3. Grow capacity by feeding it chests, or `/bank upgrade`.
4. Share it: create or join a shared / guild vault and manage members from `/bank`.

Commands (`/bank`): `list`, `invite <player>`, `accept`, `decline`, `leave`, `disband`,
`upgrade`, `withdraw ...`, and the operator-only `fillall` / `clearall` / `reload`.

## Get it

- **Source code:** [`minecraft-1.20-26.3` branch](https://github.com/Kishku7/bank-vault/tree/minecraft-1.20-26.3)
- **Report issues / support:** [mod_support](https://github.com/Kishku7/mod_support/issues)

Ships for Fabric, NeoForge, and Forge (plus Quilt on the 1.20 - 1.21 lines), covering Minecraft
1.20 through the 26.x line. Fabric / Quilt builds require Fabric API.

By Kishku7. All Rights Reserved.
