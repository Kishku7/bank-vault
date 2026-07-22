# Changelog

All notable changes to Bank Vault are documented here. This project follows
[Keep a Changelog](https://keepachangelog.com/) and Semantic Versioning.

## [1.4.5] - 2026-07-21

### Added
- Support for Minecraft 26.3-snapshot-5 (Fabric).

### Fixed
- 26.3-snapshot-5 compatibility: the resource pack_format is now 93 (was 92 on snapshot-4), and the
  bundled recipe-unlock advancement uses the new `recipes` list form of the `minecraft:recipe_unlocked`
  trigger (snapshot-5 replaced the singular `recipe` key). Without these the mod's datapack failed to
  load and the world would not open on the snapshot. Verified in-world on the headless client harness.

## [1.4.4] - 2026-07-16

### Added
- Support for Minecraft 26.3-snapshot-4 (Fabric).
- The 16 dyed cushions and the straw bed (new 26.3 decorative blocks) are now
  recognised and grouped in the vault's categorized view.

### Fixed
- Resource-pack metadata on Minecraft 1.21.9-1.21.11: the mod's bundled data
  (item categories) and textures could be silently dropped on these versions.
  The pack now loads correctly on Fabric, NeoForge, and Forge.
