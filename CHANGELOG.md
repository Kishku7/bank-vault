# Changelog

All notable changes to Bank Vault are documented here. This project follows
[Keep a Changelog](https://keepachangelog.com/) and Semantic Versioning.

## [1.4.4] - 2026-07-16

### Added
- Support for Minecraft 26.3-snapshot-4 (Fabric).
- The 16 dyed cushions and the straw bed (new 26.3 decorative blocks) are now
  recognised and grouped in the vault's categorized view.

### Fixed
- Resource-pack metadata on Minecraft 1.21.9-1.21.11: the mod's bundled data
  (item categories) and textures could be silently dropped on these versions.
  The pack now loads correctly on Fabric, NeoForge, and Forge.
