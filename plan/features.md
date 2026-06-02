# Bank Vault — Feature Requirements

Captured from planning session 2026-05-31. These are confirmed design decisions.

---

## 1. Multiblock Structure

- 3×3 grid of blocks forming a bank vault door
- Positions numbered left-to-right, top-to-bottom:
  ```
  1 2 3
  4 5 6
  7 8 9
  ```
- Three registered block types:
  - **Corner** (positions 1, 3, 7, 9) — 4 rotation states
  - **Edge** (positions 2, 4, 6, 8) — 2 states: horizontal or vertical
  - **Center** (position 5) — no rotation, the interactive block
- Vault wheel on center block animates (spins) when opened
- If any block is removed, vault goes **inert** (UI locked, items preserved in JSON)
- Vault repairs when all 9 blocks are restored

## 2. Crafting Recipes

Each recipe shape mirrors the block's position in the 3×3:

| Position | Block Type | Recipe Shape |
|----------|-----------|--------------|
| 1, 3, 7, 9 | Corner | 3 iron blocks in an L matching the corner orientation |
| 4, 6 | Vertical edge | 3 iron blocks in a vertical column |
| 2, 8 | Horizontal edge | 3 iron blocks in a horizontal row |
| 5 | Center | 9 iron blocks (full 3×3) |

**Total cost:** 33 iron blocks = 297 iron ingots (intentionally late-game)

## 3. Vault Sharing

- Owner can invite other players by **name or UUID**
- Invited players are added to a trusted list stored in the vault's JSON file
- Any trusted player (or the owner) can open the vault by interacting with the multiblock
- **Invitation command (decided):** `/bank invite <name|UUID>`
  - A raw UUID is accepted and stored directly
  - A name is resolved to a UUID server-side via the `GameProfileCache` / usercache. In online-mode this falls back to a Mojang lookup if not cached; in offline-mode Minecraft's deterministic offline UUID (derived from the name) is used.
- **No membership management in the main vault UI.** A dedicated shared-vault UI is planned as a separate screen later; for now sharing is command-driven only.

## 4. File Storage

- Vault data lives in: `config/bankvault/vaults/{owner_UUID}.json`
- Mod settings: `config/bankvault/`
- **One vault per player** (owner UUID is the key — intentional, no multi-vault support planned)
- Data persists even if the multiblock is destroyed (vault goes inert, not deleted)

## 5. Item Storage Format

- Items stored as `{ "minecraft:item_id": count }` map
- Count type: **`long`** (Java 64-bit signed integer, max 9,223,372,036,854,775,807)
- Category membership is derived at runtime from the item ID, not stored per-item

## 6. Vault Stats

Every vault always maintains two stats, updated on every insert/remove:
- `uniqueItemCount` — number of distinct item IDs in the vault
- `totalItemCount` — sum of all counts across all items

These stats are the basis for future upgrade gating.

## 7. Upgrade System

- **64 upgrade slots** (8 columns × 8 rows, each 18×18px — fixed, does not scale)
- Located in the lower-left rail of the vault UI
- Current upgrade item: **vanilla chest** (1 chest = 1 upgrade). A dedicated upgrade item may replace it later; chest is the simple stand-in for now.
- Max upgrades: 64
- **Slot-agnostic:** only the *count* of chests present in the 8×8 grid matters. Position is irrelevant — the player need not fill top-to-bottom or left-to-right. Adding or removing a chest anywhere recomputes the max live (pull a chest, watch the ceiling drop).

### Upgrade effect — storage capacity curve (DECIDED)

The single upgrade effect for now is **total item capacity** (the cap on `totalItemCount`). Capacity is a pure function of chest count (slot position irrelevant).

**Design constraint:** capacity must exceed what the same number of chests would hold if simply placed on the ground — `1728 × c` items (27 slots × 64). The curve beats this baseline at *every* level (smallest margin ~1.24× around 7–8 chests, widening to 180× at 64).

**Endpoints:** 0 chests = 2,048 items (base floor) · 64 chests = 20,000,000 items.
**Shape:** back-loaded exponential (k = 2). The last 8 chests deliver ~74% of total capacity.
**Base:** a freshly-formed vault with no upgrade chests holds the 2,048 floor. Each chest adds at least one real chest's worth (1,728) up front, with the exponential dominating later. All 64 upgrade slots contribute.

**Formula:**
```
capacity(c) = floor(c) · R^((c/64)²)
  floor(c) = 2048 + 1728·c           (base 2048 + one chest's worth per upgrade)
  R        = 20,000,000 / floor(64)  ≈ 177.557
  c = 0 → 2048 ;  c ≥ 64 → 20,000,000 (clamped)
```

Reference Java helper: `VaultCapacity.capacityFor(int chests)` (see architecture.md → Upgrade Capacity).

**Full table (c chests → max items, ×ch = multiplier vs real chests):**

| c | max items | ×ch | c | max items | ×ch |
|--:|----------:|----:|--:|----------:|----:|
| 0 | 2,048 | — | 33 | 234,105 | 4.11 |
| 1 | 3,781 | 2.19 | 34 | 262,257 | 4.46 |
| 2 | 5,532 | 1.60 | 35 | 294,299 | 4.87 |
| 3 | 7,315 | 1.41 | 36 | 330,840 | 5.32 |
| 4 | 9,143 | 1.32 | 37 | 372,590 | 5.83 |
| 5 | 11,031 | 1.28 | 38 | 420,383 | 6.40 |
| 6 | 12,994 | 1.25 | 39 | 475,197 | 7.05 |
| 7 | 15,048 | 1.24 | 40 | 538,186 | 7.79 |
| 8 | 17,210 | 1.24 | 41 | 610,707 | 8.62 |
| 9 | 19,498 | 1.25 | 42 | 694,364 | 9.57 |
| 10 | 21,933 | 1.27 | 43 | 791,056 | 10.65 |
| 11 | 24,537 | 1.29 | 44 | 903,032 | 11.88 |
| 12 | 27,334 | 1.32 | 45 | 1,032,962 | 13.28 |
| 13 | 30,352 | 1.35 | 46 | 1,184,024 | 14.90 |
| 14 | 33,620 | 1.39 | 47 | 1,360,003 | 16.75 |
| 15 | 37,172 | 1.43 | 48 | 1,565,418 | 18.87 |
| 16 | 41,047 | 1.48 | 49 | 1,805,675 | 21.33 |
| 17 | 45,286 | 1.54 | 50 | 2,087,251 | 24.16 |
| 18 | 49,939 | 1.61 | 51 | 2,417,921 | 27.44 |
| 19 | 55,058 | 1.68 | 52 | 2,807,040 | 31.24 |
| 20 | 60,707 | 1.76 | 53 | 3,265,876 | 35.66 |
| 21 | 66,955 | 1.85 | 54 | 3,808,037 | 40.81 |
| 22 | 73,883 | 1.94 | 55 | 4,449,982 | 46.82 |
| 23 | 81,582 | 2.05 | 56 | 5,211,661 | 53.86 |
| 24 | 90,158 | 2.17 | 57 | 6,117,297 | 62.11 |
| 25 | 99,729 | 2.31 | 58 | 7,196,361 | 71.80 |
| 26 | 110,434 | 2.46 | 59 | 8,484,780 | 83.22 |
| 27 | 122,433 | 2.62 | 60 | 10,026,438 | 96.71 |
| 28 | 135,907 | 2.81 | 61 | 11,875,039 | 112.66 |
| 29 | 151,069 | 3.01 | 62 | 14,096,438 | 131.58 |
| 30 | 168,163 | 3.24 | 63 | 16,771,552 | 154.06 |
| 31 | 187,472 | 3.50 | 64 | 20,000,000 | 180.84 |
| 32 | 209,326 | 3.79 | | | |

## 8. Hopper Input

- **All 9 blocks** of the vault structure act as inventory targets
- Any face of any block can accept hopper input
- Behavior: hopper pushes item → block entity accepts → increments count in vault JSON → slot clears immediately (passthrough, always appears empty to hopper)
- All 9 block entities implement `Inventory` (or `SidedInventory`)
- On hopper push: block entity looks up vault by stored owner UUID reference, delegates insert to vault data

## 9. Container Shift-Click Extraction

When the vault UI is open and the player shift-clicks a supported container from their inventory:

### Supported container types
- **Shulker box**
- **Bundle**
- **Travelers Backpack**

### Behavior
1. Vault empties the container's contents into vault storage (recursively — see §10)
2. Empty container is placed in the **return/outbound slot** (36×36px, pulsing gold outline in UI)
3. If player shift-clicks another shulker box or bundle while the return slot is occupied:
   - Previous shulker/bundle → goes into vault storage (stored as empty container item)
   - Previous Travelers Backpack → returned to player's inventory
   - New container begins extraction

### Travelers Backpack special handling
- TB content extraction uses the same API pattern as `TravelersBackpackCollector.java` in the YIAS mod
- At any nesting level, TBs go into the vault (not returned to inventory) — only the outermost TB at extraction time goes to the return slot

## 10. Recursive Container Extraction

- When extracting a container, each item inside is checked against a **ContainerExtractor registry**
- If an item is itself a registered container, it is recursively extracted
- Non-container items are added to the vault by ID + count
- **Max recursion depth: 16** (pathological input guard — not a realistic limit for normal play)
- Empty containers produced by recursion go into the vault as items

### ContainerExtractor Registry
- Interface: `ContainerExtractor` — knows how to pull items out of a specific container type
- Built-in registrations: shulker box, bundle, Travelers Backpack
- Third-party mods can register their own extractors

### Error handling
- On any error during extraction: **stop immediately, return the container in its current state to the player**
- Items already deposited before the error remain in the vault
- Error cases: unidentifiable item, extractor throws, vault write failure, vault at capacity

## 11. UI — Overview

- Large creative-menu-style screen
- Targets 1024×768 minimum resolution
- Full-screen overlay on dimmed backdrop
- Server-authoritative: `ScreenHandler` (server) + `HandledScreen` (client)
- **13 category tabs** (Wood, Copper, Colored, Blocks, Items, Food, Tools, Armor, Redstone, Enchanting, Transportation, Misc, Uncategorized). Tab set + per-item assignment are stored in `catalog/item_catalog.db` (SQLite); see `ui-spec.md` → Tab Bar.
- See `ui-spec.md` for full layout details
