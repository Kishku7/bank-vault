# Changelog

All notable changes to Bank Vault are documented here. This project follows
[Keep a Changelog](https://keepachangelog.com/).
Versioning policy is universal across all mods and is NOT restated here -- see Memory/minecraft/mod-rules.md.

## [1.4.6] - 2026-07-27

### Changed
- NeoForge 26 cells rebuilt against the now-PUBLISHED NeoForge builds: 26.1 -> 26.1.2.87, 26.2 -> 26.2.0.35-beta (previously 26.1.2.30-beta / 26.2.0.1-beta). The [26.1.2.0-beta,) dependency floor is unchanged - Bank Vault needs the 26.1.2 BreakBlockEvent.
- mavenLocal() removed from the NeoForge/26 cell (local-alpha-era leftover).
- README no longer calls Minecraft 26.2 a pre-release; it shipped stable on 2026-06-16.
- No source or behaviour change. Server-boot smoketested on NeoForge 26.1.2 and 26.2.

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
