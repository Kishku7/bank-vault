# Bank Vault — Project Overview

## Identity

| Field | Value |
|-------|-------|
| Mod name | Bank Vault |
| Mod ID | `bankvault` |
| Package | `com.kishku7.bankvault` |
| Type | Fabric mod (client + server) |
| Target | Minecraft 1.21.5 (data version 4790), Fabric |
| GitHub | Kishku7 |
| Modrinth | Name confirmed available as of 2026-05-31 |

## Concept

A Fabric mod that replaces traditional hundreds-of-double-chests storage with a single **Bank Vault** multiblock structure. Opening the vault presents a creative-menu-style UI showing only items the player has collected, organized by category. Storage is virtual — items are tracked by ID and count server-side, not as physical items in physical chests.

## Design Files

- `docs/Bank Vault UI (standalone).html` — full standalone UI mockup from Claude.ai Design
- `docs/Bank Vault UI.html` — original (non-standalone) version

## Related Projects

- `your-items-are-safe` mod — same developer, same server. TravelersBackpack and Trinkets integration patterns carry over to Bank Vault's container extractor system.
- MyChests schematic — the physical chest room Bank Vault is intended to replace.
