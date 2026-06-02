# Bank Vault

*Retire the chest room. Build one vault instead.*

**Bank Vault** is a Fabric mod for Minecraft **26.1.2** that replaces the sprawling wall of double
chests with a single multiblock **bank vault** — a 3×3 iron structure that forms a working vault
door, complete with a spinning wheel, and opens into a creative-menu-style interface holding
everything you own, sorted and searchable.

---

## The cool stuff

### One vault, (almost) everything

Open the vault and you get a **full-screen, creative-style browser** of your entire stock —
**13 categories** (Wood, Copper, Colored, Blocks, Items, Food, Equipment, Redstone, Enchanting,
Transportation, Dyes, Misc, Uncategorized) with live counts, a scrollable item grid, and
per-category **smart sorting**: sort by item *family*, by *type* (all planks together, all slabs
together…), alphabetically, or by quantity. Tools sort by material tier, food by its core name
("Raw Beef" and "Cooked Beef" file under **B**), wood by species, copper by oxidation, dyes by color.

### Quick-unload, recursively

Drag a **shulker box, bundle, or Traveler's Backpack** into the **Unload** slot and its contents
pour straight into the vault. Containers nested inside containers? They cascade — a backpack full
of shulkers full of bundles empties in one action (depth-capped at 16 for safety). Each emptied
inner container is stored as an item; the outermost shell drops into the **Out** slot for you to
grab back.

### Keeps your one-of-a-kind items distinct

Enchanted books, written books, renamed gear, damaged tools — anything with custom data is stored
as its **own entry**, glint and tooltip intact, and withdrawn exactly as it went in. Plain items
still stack into single tidy counts.

### Real drag-and-drop

The vault is a true container screen: drag and drop with your inventory like any chest, **shift-click
to deposit**, click an item in the grid to **withdraw** a stack.

### Capacity you earn

A freshly-formed vault holds **2,048** items. Feed **chests** into the upgrade slot and the ceiling
climbs a back-loaded exponential curve to a maximum of **20,000,000** — and it always beats simply
placing the same chests on the ground. Only the *count* of chests matters, never their position.

### Shared vaults

Invite other players with `/bank invite <name|UUID>`. Four permission levels (Owner, Master, Member,
Deposit-only), vault **merging**, owner **succession**, and the usual kick/leave/transfer/disband —
all command-driven.

---

## The mundane (but necessary) stuff

- **Hopper input on every face** — point a hopper at any block of the vault and it feeds straight in.
  Automate your sorting once and forget it.
- **Late-game by design** — the structure costs **33 iron blocks** (297 ingots) to build out.
- **Inert when broken** — pull a block and the vault locks; your items are safe in storage until you
  repair the 3×3. Nothing is lost.
- **Per-player data** stored as plain JSON under `config/bankvault/` — readable, backup-friendly,
  `long`-counted (no 2-billion ceiling).
- **Seamless vault door** — corner and edge blocks auto-rotate to their position so the face reads as
  one continuous door, and the center wheel animates when the vault is open.

---

## Compatibility

- **Minecraft:** 26.1.2 · **Loader:** Fabric · **Java:** 25
- **Requires:** [Fabric API](https://modrinth.com/mod/fabric-api)
- **Soft integrations** (optional — detected at runtime, no hard dependency):
  - **Traveler's Backpack** — backpacks unload through the vault like any other container.
  - **Trinkets** — wiring present for future slot support.

If an optional mod isn't installed, Bank Vault simply ignores it.

---

## Building from source

```bash
./gradlew build
```

The built jar lands in `build/libs/`. Drop it (plus Fabric API) into your `mods/` folder.

---

## Status

**Pre-release — v0.1.0.** Under active development toward 1.0; expect rough edges and changing
internals. Bug reports and ideas welcome.

---

*By Kishku7 · built with [TechPro](https://example.invalid).*
