# Bank Vault — Architecture

## Block Registration

Three registered block types:

```
BankVaultCornerBlock     — 4 horizontal rotation states
BankVaultEdgeBlock       — 2 states: HORIZONTAL, VERTICAL
BankVaultCenterBlock     — no rotation, has BlockEntity, is the interactive block
```

All three have corresponding **BlockEntity** types that store:
- `ownerUUID: UUID` — set when multiblock validates, cleared when it breaks
- `vaultActive: boolean` — whether this block is part of a live vault

The center block's BlockEntity additionally stores:
- `vaultData` reference (or loads from file on demand)

## Multiblock Validation

Triggered when any of the 9 block types is placed or removed. The validator:
1. Checks the 3×3 pattern centered on (or containing) the changed block
2. Confirms correct block types in all 9 positions with correct orientations
3. On **form**: sets `ownerUUID` and `vaultActive = true` on all 9 block entities, loads vault data, marks structure active
4. On **break**: sets `vaultActive = false` on all remaining block entities, saves vault data, marks vault inert

The multiblock must handle detecting which player interacted last (to assign ownership on first formation).

## Vault Data — File Storage

**Path:** `config/bankvault/vaults/{owner_UUID}.json`

One file per player. The file is the source of truth — not block NBT.

```json
{
  "owner": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "trusted": [
    "yyyyyyyy-yyyy-yyyy-yyyy-yyyyyyyyyyyy"
  ],
  "items": {
    "minecraft:stone": 48293,
    "minecraft:oak_log": 1024,
    "minecraft:diamond": 7
  },
  "stats": {
    "uniqueItemCount": 3,
    "totalItemCount": 49324
  },
  "upgradeCount": 12
}
```

Stats (`uniqueItemCount`, `totalItemCount`) are recomputed and written on every insert/remove — never derived lazily.

Item counts are stored as JSON numbers. Deserialize as `long` (not `int`). Use Gson's `setLongSerializationPolicy` or explicit type adapters to handle longs > Integer.MAX_VALUE correctly.

## Hopper Integration — Inventory Interface

All 9 block entities implement `Inventory` (or `SidedInventory`).

Hopper push behavior:
- Hopper calls `setStack(slot, itemStack)` on the block entity
- Block entity looks up vault by `ownerUUID`
- If vault is active: add item to vault data (increment count), return empty stack, mark slot empty
- If vault is inert: reject push (return false / don't consume item)

The "slot" exposed to hoppers is a single virtual slot — always empty, always accepts items when vault is active.

`canInsert(slot, stack, direction)` — return `true` for all directions on all 9 blocks when vault is active.

## Container Extractor System

```java
public interface ContainerExtractor {
    boolean canExtract(ItemStack stack);
    List<ItemStack> extract(ItemStack container, ServerPlayerEntity player);
}
```

Registry: `ContainerExtractorRegistry` — a simple ordered list of registered extractors checked in registration order.

Built-in extractors:
- `ShulkerBoxExtractor` — reads NBT BlockEntityTag items list
- `BundleExtractor` — reads `bundle.items` NBT list
- `TravelersBackpackExtractor` — uses TB API (same pattern as YIAS `TravelersBackpackCollector`)

Extraction flow (recursive):
```
extractAll(ItemStack container, int depth) -> List<ItemStack>:
  if depth > 16: throw ExtractionDepthException
  items = registry.findExtractor(container).extract(container)
  result = []
  for item in items:
    if registry.hasExtractor(item):
      result += extractAll(item, depth + 1)
      result += [emptyContainer(item)]   // add the emptied container as an item
    else:
      result += [item]
  return result
```

On any exception: catch at the outermost call site, return the container in current state to player.

## Screen Architecture

- `BankVaultScreenHandler extends ScreenHandler` — server-side, manages slot logic, handles shift-click events, communicates with vault data
- `BankVaultScreen extends HandledScreen` — client-side, custom rendering, large creative-menu UI

Packet flow:
- Client shift-clicks container → sends `ExtractContainerC2SPacket` to server
- Server runs extraction, updates vault data, sends `VaultDataS2CPacket` back
- Client re-renders grid from updated data

Tab filtering and search happen client-side on a cached item list (sent from server on open).

## Upgrade Capacity

Total item capacity (the ceiling on `totalItemCount`) is a pure function of `upgradeCount` — the number of chests present in the upgrade grid. Slot position is irrelevant; only the count matters. Capacity is recomputed live whenever a chest is added or removed.

The curve is a back-loaded exponential (exponent k = 2) built on a floor of `2048 + 1728·c` (base 2,048 plus one real chest's worth — 27 slots × 64 — per upgrade), so capacity always exceeds simply placing the same chests. Endpoints: **0 chests = 2,048 (base floor)**, 64 chests = 20,000,000. The last 8 chests carry ~74% of total capacity.

```java
public final class VaultCapacity {
    private static final long BASE_ITEMS   = 2_048L;      // capacity with 0 upgrade chests
    private static final long MAX_ITEMS    = 20_000_000L; // capacity at 64 chests
    private static final int  CHEST_SLOTS  = 27 * 64;     // 1728 — a real chest's item ceiling
    private static final int  MAX_UPGRADES = 64;

    /** floor(c) = base + one real chest's worth per upgrade chest */
    private static long floor(int chests) {
        return BASE_ITEMS + (long) CHEST_SLOTS * chests;
    }

    public static long capacityFor(int chests) {
        if (chests <= 0)            return BASE_ITEMS;
        if (chests >= MAX_UPGRADES) return MAX_ITEMS;
        double r = (double) MAX_ITEMS / floor(MAX_UPGRADES); // growth factor ≈ 177.557
        double t = (double) chests / MAX_UPGRADES;
        return Math.round(floor(chests) * Math.pow(r, t * t));
    }
}
```

Full per-chest table lives in `features.md` → §7. Inserts that would push `totalItemCount` over `capacityFor(upgradeCount)` are rejected (treated as "vault at capacity" — see Container Extractor error handling).

## Sharing — `/bank` Command

`/bank invite <name|UUID>` adds a player to the vault's `trusted` list (resolution: raw UUID stored directly; name resolved via `GameProfileCache`/usercache, Mojang fallback online-mode, deterministic offline UUID in offline-mode). Membership is command-driven only for now — a dedicated shared-vault UI is deferred to a later screen, not part of the main vault UI.

## Config / Settings

`config/bankvault/bankvault.json` (server-side):
- `maxUpgrades: 64` — cap on upgrade slots
- `maxDepth: 16` — container extractor recursion limit
- Capacity endpoints (`2048` / `20000000`) and the k=2 curve are defined in `VaultCapacity`; expose as config later if tuning is needed.

## Key Implementation Order (Suggested)

1. Block registration (3 types + block entities)
2. Crafting recipes
3. Multiblock validator
4. Vault data file I/O (load/save/create)
5. `Inventory` interface on all 9 block entities (hopper support)
6. Basic screen handler + screen (open vault, close vault)
7. Item grid rendering (scrollable, category tabs, search)
8. Container extractor registry + built-in extractors
9. Shift-click extraction flow + return slot
10. Sharing / trusted player invite system
11. Upgrade slots UI + upgrade count tracking
12. Polish: animations, sounds, textures
