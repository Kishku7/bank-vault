# Bank Vault

*Retire the chest room. Build one vault instead.*

**Bank Vault** is a Fabric mod for Minecraft **26.1.2** that replaces the sprawling wall of double
chests with a single multiblock **bank vault** — a 3×3 iron structure that forms a working vault
door, complete with a spinning wheel, and opens into a creative-menu-style interface holding
everything you own, sorted and searchable.

---

## The cool stuff

### One vault, (almost) everything

Open the vault and you get a **full-screen, creative-style browser** of your entire stock, split
across **category tabs** with live counts and a scrollable item grid. Each tab carries its own
**smart sorting** — sort by family, by type (all the planks together, all the slabs together…),
alphabetically, or by quantity. Tools fall into line by material tier, food sorts by its core name
("Raw Beef" and "Cooked Beef" both file under **B**) — the ordering actually understands what
it's looking at.

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

A freshly-formed vault holds a humble stockpile. Feed **chests** into the upgrade slot and the
ceiling climbs an accelerating curve — only the *count* of chests matters, never their position.
How many upgrades do you think will be enough?

### Shared vaults — guild and team ready

Invite other players with `/bank invite <name|UUID>`. Four permission levels (Owner, Master, Member,
Deposit-only), vault **merging**, owner **succession**, and the usual kick/leave/transfer/disband.
Run a guild bank, a team stockpile, or a shared base supply — all command-driven.

---

## The mundane (but necessary) stuff

- **Hopper input on every face** — point a hopper at any block of the vault and it feeds straight in.
  Automate your sorting once and forget it.
- **Affordable early** — it's a 3×3 of iron. Anyone with iron gear can gather what they need, and if
  you've built an iron farm you'll have the vault standing long before you set foot in the Nether.
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

*By Kishku7.*
