# Changelog

All notable changes to Bank Vault are documented here. This project follows
[Keep a Changelog](https://keepachangelog.com/).
Versioning policy is universal across all mods and is NOT restated here -- see Memory/minecraft/mod-rules.md.

## [1.4.13] - 2026-09-10

Eight explorer maps were being sorted under item ids Minecraft no longer uses, and the 26.3 target
jumps four builds to `26.3-rc-1`. Only the 26.3 cell is rebuilt.

### Fixed

- **Eight explorer maps were invisible to categorization and sorting on any 26.3 build newer than
  snapshot-7.** They were curated under the ids they had at 26.3-snapshot-7, and Minecraft renamed
  every one of them at 26.3-pre-1:

  | was (snapshot-7) | is (26.3-pre-1 onward) |
  |---|---|
  | `abandoned_campsite_map` | `abandoned_camp_map` |
  | `ancient_city_map` | `buried_ancient_city_map` |
  | `jungle_explorer_map` | `jungle_pyramid_map` |
  | `mineshaft_map` | `buried_mineshaft_map` |
  | `ocean_explorer_map` | `ocean_monument_map` |
  | `swamp_explorer_map` | `swamp_hut_map` |
  | `trial_explorer_map` | `buried_trial_chambers_map` |
  | `woodland_explorer_map` | `woodland_mansion_map` |

  **Both sets of ids are now carried, not swapped.** The data files ship to every cell from 1.20.0
  to 26.3, so an id that is right on one version and absent on another has to be present either
  way -- the old names stay valid on 26.3-snapshot-7 and below, the new ones from 26.3-pre-1 up,
  and on any given version the other half is simply inert. Each old id sits immediately beside its
  replacement in the curated `materials` order, so the sort reads the same whichever one the
  running version resolves. The curation itself was already right and is untouched: same keywords,
  same `materials` category, same position.

### Changed

- **The 26.3 target moves from `26.3-snapshot-7` to `26.3-rc-1`**, skipping pre-1, pre-2 and pre-3.
  `pack_format` 95 -> **97**, fabric-api `0.156.2+26.3` -> `0.160.3+26.3`, fabric-loader 0.19.3 ->
  0.19.5, and the exclusive window becomes `>=26.3-rc.1 <26.3-rc.2`. The 26.3 pin names one exact
  build because `pack_format` moves on most 26.3 releases, so a jar is wrong on every other one.

### Notes

- **Every item added to Minecraft between 26.2 and 26.3-rc-1 was checked, not just the ones that
  broke.** 121 items arrived and none were removed: the poplar wood set and its three leaf colours,
  16 cushions, wool and concrete slabs and stairs in all 16 colours, 16 explorer maps, plus
  `red_shrub`, `shelf_mushroom` and `straw_bed`. 113 were already classified; the 8 above were the
  only gap. Spawn eggs and creative-only blocks remain excluded, as they always have been -- zero of
  either is classified, so that is the standing rule and not an oversight.
- fabric-api must be `0.160.3+26.3` or newer on rc-1: earlier builds on the 26.3 line install and
  then crash the client, because they wrap a lambda that rc-1 deleted.

## [1.4.12] - 2026-08-09

Dead-asset removal. Product-wide -- all 32 cells rebuilt. No rendering change: everything removed
here was already unreachable.

### Removed
- **`textures/block/corner_front.png` and `textures/block/edge_front.png` are gone.** Both were
  referenced by nothing and shipped in every jar. The eight `bank_vault_corner{0,90,180,270}` and
  `bank_vault_edge{0,90,180,270}` models that were supposed to use them were bare
  `{"parent": "bankvault:block/bank_vault_plain"}` aliases, so the blockstate's 40 variants had
  only ever resolved to two real models. Those eight alias files are removed too and the blockstate
  now points at `bank_vault_plain` directly -- 40 variants, 2 models, identical rendering.
- Both orphan textures carried the SAME stray cross mark near their bottom-right corner that 1.4.11
  removed from `vault_metal.png` (peaks 172 and 163 against a ~75 background), so the defect was
  set-wide rather than a one-off. Deleting them settles it without needing a second repair.

## [1.4.11] - 2026-08-09

Texture fix. Product-wide -- the block texture is shared by every cell, so all 32 cells are
rebuilt and carry 1.4.11.

### Fixed
- **A stray cross-shaped mark sat where the vault block's bottom-right corner rivet should be.**
  `textures/block/vault_metal.png` carried a 6x6 px bright plus sign at (118-123, 118-123) instead
  of a rivet, leaving the riveted border visibly broken at one corner while the other three were
  complete. The corner now carries a normal rivet, copied from the adjacent right-column rivet so
  the highlight, shadow and plate-edge shading match the rest of the border exactly.

## [1.4.10] - 2026-08-04

Catalog completeness release. Every cell rebuilt: the vault catalog is shared across all cells, so
the classification work below applies product-wide, not just to 26.3.

### Added
- **The 48 items Minecraft 26.3-snapshot-7 introduced are now classified**, not dumped in
  Uncategorized. 32 concrete slabs + stairs (all 16 dye colours) -> **Colored Blocks**, ranked
  directly beside the concrete they are cut from. 16 new per-target map items -- `ancient_city_map`,
  `buried_treasure_map`, the six `*_village_map`s, the explorer maps and the rest, which Mojang split
  out of `filled_map` -> **Materials & Utility**, ranked beside `filled_map`.
- **The pre-existing classification backlog is closed too.** 16 cushions and `straw_bed` ->
  **Fabric & Color**; the 7 infested blocks -> **Stone & Masonry**, each beside its base block;
  `player_head` -> **Magic & Treasure** with the other mob heads. 73 items classified in total,
  with keyword-search entries for the 48 new ids.
- The remaining 21 net-new registry ids are creative/technical only (command blocks, jigsaw,
  structure/test blocks, spawners, bedrock, `budding_amethyst`, `suspicious_sand`, spawn eggs, ...)
  and are **deliberately excluded** from the vault, consistent with how `barrier` and the spawn eggs
  have always been treated. They are now recorded as such in the taxonomy, so the
  "needs classification" list is **empty** and stays empty instead of re-surfacing every version.

### Fixed
- A new Minecraft version used to add its items to the vault as storable-but-Uncategorized. Nothing
  reported this: the load-time log prints the catalog's own item count, which does not change when
  the registry grows, so the gap read as healthy. Catalog completeness is now an explicit release
  gate -- `tooling/bv_new_items.py` must return zero before a version ships.

## [1.4.9] - 2026-08-04

Minecraft 26.3-snapshot-7 support. 26.3 cell only -- every other cell is unchanged and keeps
1.4.8.

### Changed
- **Retargeted the 26.3 cell from snapshot-6 to snapshot-7.** Fabric API 0.156.2+26.3, loader
  0.19.3, resource pack_format 95, dependency window `[26.3-alpha.7, 26.3-alpha.8)`.

### Fixed
- **`Player.drop` gained a trailing `Prediction` argument in snapshot-7.** Mojang added
  `net.minecraft.util.Prediction` (`PREDICTED` / `SERVER_ONLY`) and threaded it through
  `Player.drop` and `Inventory.placeItemBackInInventory`. Every Bank Vault call site is
  server-side overflow handling reached from a player action -- withdraw, deposit, grid moves,
  `/bank` -- so all 14 now pass `PREDICTED`, matching what vanilla's own
  `AbstractContainerMenu`, `CraftingMenu` and `ResultSlot` pass at the equivalent sites. Without
  this the 26.3 cell does not compile at all.

## [1.4.8] - 2026-07-29

Hardening release: every message Bank Vault accepts from a client is now bounded and then
checked against real server state before it is acted on. Product-wide -- all 28 cells rebuilt.

### Security
- **New `BvWire`: one untrusted-input gate for the whole mod.** All server-side payload handling
  routes through a single validator holding the named server-policy ceilings and the state checks,
  instead of each loader's `ModNetworking` deciding for itself. Compiles unchanged on every cell
  from 1.20 to 26.x.
- **Collection reads are count-gated before they allocate.** A declared element count that is
  negative or over its ceiling is rejected as a malformed packet (`DecoderException`) before a
  single element is read. Previously the grid-view message sized its list straight from the count
  on the wire, so one packet claiming a huge number of entries could exhaust server memory. Applies
  to every list in every payload, on both the modern `StreamCodec` cells (via `ByteBufCodecs.list`
  with an explicit max) and the pre-1.20.5 raw-buffer cells (via the code generator, which now
  refuses to emit an uncapped list read at all).
- **Withdraw amounts are clamped to policy and to real stock.** `withdraw` took any positive
  amount; a request for a huge quantity drained the vault in one go and could turn into hundreds of
  thousands of dropped item entities on the server thread. The amount is now capped per request and
  never exceeds what the bank actually holds.
- **The grid map is verified, not trusted.** The client tells the server which bank key sits in each
  visible cell so a slot click maps back to stored stock -- which makes it a withdraw handle. Cells
  are now bounded and every key checked against stock the bank really holds; anything unknown or
  malformed becomes an empty cell.
- **Single-slot deposit honours the armor/offhand exclusion.** The slot index was only bounds-checked
  against the full 41-slot container, so a crafted packet could reach armor (36-39) or offhand (40),
  which the v1.1 spec calls untouchable. It is now checked against the hotbar + main rows (0-35),
  matching what the bulk-deposit path already enforced by construction.
- **UI-memory tokens are sanitized and capped.** The remembered tab/sort strings are persisted to
  the player's settings file, and each write rewrites that file. They are now shape-checked and
  length-limited, and the number of distinct remembered per-tab sorts is capped, so the values can
  no longer grow the file without bound.

### Fixed
- **Fabric 1.21.9 / 1.21.10 crashed the integrated server on world join.** The Fabric
  `1.21.11` jar claimed `>=1.21.9 <1.21.12` but compiles the permissions API that only
  arrived in 1.21.11, so `/bank`'s operator-gate predicate hit a `NoSuchMethodError` while
  the command tree was being built for a joining player -- a hard crash on two of the three
  versions that jar advertised. This shipped in 1.4.4 and every release before it.
  Fixed by splitting the cell the way the NeoForge and Forge lines already were: a new
  Fabric `1.21.9` cell (classic integer permission levels) now serves 1.21.9-1.21.10, and
  the `1.21.11` jar is narrowed to 1.21.11 only. Found by a full every-claimed-version
  client run, which is the only thing that would have caught it -- the jar's own build
  version was always fine.

### Changed
- All source, resources and code-generator files are now pure ASCII. Glyphs that the GUI actually
  draws (sort and scrollbar arrows, the close marker) and the section-sign colour codes are written
  as `\uXXXX` escapes rather than literal characters -- the compiled output is unchanged and the
  GUI looks exactly the same. Translation files keep their native characters.

## [1.4.7] - 2026-07-28

### Changed
- **Fabric 26.3 cell moved to MC 26.3-snapshot-6** (from snapshot-5): fabric-api
  `0.155.3+26.3` -> `0.156.1+26.3`, `pack_format` `93` -> `94` (bumped in BOTH
  `scripts/build-fabric.ps1` and `_codegen/compat_core.py PACK_FORMATS`, since the
  cog-materialized pack.mcmeta reads the latter and overrides the templated value),
  and the exclusive snapshot window `[26.3-alpha.5, 26.3-alpha.6)` ->
  `[26.3-alpha.6, 26.3-alpha.7)`.

### Notes
- **No source change required.** Snapshot-6's breaking surfaces were checked against the
  whole tree: Bank Vault's only `SharedSuggestionProvider` use is the static
  `suggest(Iterable<String>, SuggestionsBuilder)` helper, which is unchanged (the filter
  parameter was added to `suggestRegistryElements`/`listSuggestions`, which Bank Vault does
  not use). It touches none of the block-entity loot helpers that moved from
  `AbstractFurnaceBlockEntity` to `BaseContainerBlockEntity`, and none of the worldgen,
  input, screen or render changes.
- The snapshot-5 `recipe` -> `recipes` advancement fix is applied at gen time by
  `scripts/cog-gen.ps1` step 4c for every cell >= 26.3, so it carries into snapshot-6
  automatically; the shared cog source deliberately keeps the pre-26.3 singular shape.

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
