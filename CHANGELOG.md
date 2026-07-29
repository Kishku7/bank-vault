# Changelog

All notable changes to Bank Vault are documented here. This project follows
[Keep a Changelog](https://keepachangelog.com/).
Versioning policy is universal across all mods and is NOT restated here -- see Memory/minecraft/mod-rules.md.

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
