# Bank Vault 1.2 — Backport Checklist (1.1.0 -> 1.2.0)

Everything added or changed in the 1.2 cycle, organized by feature, for porting to the other
version families (Fabric 1.20.1, 1.20.4, and the rest, per family list in the repo / memory).
No alpha/beta attribution — this is the WHAT, not the when. Where 26.1.2-specific API was used,
it is flagged; older families need the equivalent mapped per the family's API level.

## 1. Category button grid (replaces the tab rail)

- Settings-driven button grid: `config/bankvault/buttons.json` defines numbered rows of buttons
  (entries per row = columns, ragged rows OK). Live-editable; config polled ONCE per screen init
  (see perf rules below), reload by reopening the vault.
- Icon-only buttons render a representative vanilla item; per-tab `icon` field in
  `categories.json` (pre-1.2 configs get icons backfilled from bundled defaults).
- `icon` also accepts `texture:<id>` (PNG rendered via GuiGraphics.blit with the GUI_TEXTURED
  pipeline — 26.1.2 API name; map per family). Shipped composite: `diamond_netherite.png`
  (opaque diamond back, netherite ingot 75% alpha front).
- Tooltip = button name + item count (counts CACHED per vault sync, never per frame).
- Buttons with no smart-sort record are hidden; Uncategorized only shows while the vault holds
  uncategorized items. Wheel-scrolls by row on overflow; pixel-true scroll clamp.
- Rail sections: rows accept `{"gap": true}` markers (6px break) and `{"section": "text"}`
  header rows (small subtle text, 0.75 scale). Final sections: Materials / Building & Decor /
  Gear & Combat / Food & Nature / Tech & Sources / Other. 8-column rail (clamp 170; item grid
  drops 12 -> 11 cols at design width).

## 2. Keyword + dynamic buttons

- `buttons.json` schema v2: an entry is a category-id string (v1 form) OR
  `{label, icon, words[]}` keyword button matching ANY listed word from `keywords.json`
  (new data file; config > bundled; restoreMissingDefaults ships it).
- Buttons may combine `words[]` + `dynamic` (union).
- `{dynamic: "trinkets"}` button: TrinketCompat.isTrinket(stack, player) asks every trinket
  slot's validator live (cached per stack key, Throwable-guarded) — any trinkets-backed mod
  works with zero data inventory. BACKPACKS ARE NOT TRINKETS: items carrying the backpack
  keyword are excluded even though they ride a trinket slot.
- Selection keyed by button identity (`kw:<label>` / `dyn:<id>` / category id).

## 3. Per-button smart sorts

- Keyword/dynamic buttons get curated ranked orders keyed by button key in
  `sort_family` / `sort_type` + tabSort chains; categorical sorting stays the fallback for
  unconfigured buttons and old configs.
- CAVEAT: renaming a button label orphans its `kw:<label>` sort/group entries — rerun the
  generator and mirror on ANY button/data edit.
- Generator: `Local_Research\Minecraft\bv-sort-analysis\gen_sorts.py` — re-runnable, wipes and
  regenerates `kw:`/`dyn:` keys only, validates coverage, emits `button_members.json`
  (membership dump) and `sort_groups.json` (section-label spans). REGENERATE PER FAMILY by
  swapping the registry dump.
- A-Z: per-tab opt-in `alpha` step chains in tabSort (lastword/firstword on shape/material
  tabs); plain hover-name default everywhere else.
- Design rules: family = material/kind runs (wood species, color runs, tier runs); type =
  form/slot/kind first; potion tabs = potion/potionpower chains; Trinkets/Patterns = name.

## 4. Per-player last-use memory (server-side)

- `config/bankvault/user_settings/{a..z,other}.json` — 27 bucket files by FIRST LETTER of
  player name; records keyed by UUID inside (rename-proof: record moves bucket on rename, UUID
  key preserved). Atomic per-bucket writes (tmp + move). loadAll on SERVER_STARTING.
- Rec stores: name, lastTab, sorts{tabKey -> family|type|alpha|count_asc|count_desc},
  showSections, pins{tab -> [item ids]}.
- Net: UiStatePayload (c2s on tab/sort/sections click) + UiStateSyncPayload (s2c, sent BEFORE
  the menu-open packet) -> ClientUiState static cache -> screen init restores last tab +
  per-tab sort (incl. Count direction); tab click applies that tab's remembered sort
  (default Smart Family).

## 5. Section titles ("Titles" checkbox)

- Checkbox on the SEARCH ROW (left of go, next to the Pin box; auto-hide only if the search
  box would drop under 64px). Persisted per user (showSections).
- Grid switches to virtual rows: header row | <=cols items, ragged rows top-left aligned.
  Header/blank cells send "" keys in GridViewPayload (server real-slot mapping unchanged).
- Labels per sort mode: Smart F/T from `sort_groups.json` spans (generator-emitted
  [label,count] over each ranked list); categorical tabs -> old category names; potion chains
  -> effect names; A-Z -> letter of the SORTED word (lastword-aware, absent letters skipped);
  Count -> ranges 1-100 / 101-1K / 1K-10K / 10K-100K / 100K+; search regroups stably by
  "Section -> Button" (Found-under), Pinned first.

## 6. Pins

- Button-level `pins[]` in button data: pinned ids sort first within that button in every mode
  (e.g. totem of undying leads Trinkets).
- Per-tab per-player USER pins: drop a carried stack on the "Pin" box (search row). The Pin
  box is backed by a REAL invisible menu slot (PIN_SLOT, directly before the view slots);
  menu.clicked() DEPOSITS the carried stack into the bank AND toggles the pin for the current
  tab inside the vanilla click transaction. Full vault = deny (stack stays on cursor, no
  toggle). Unpin = pick the item back up, drop on Pin again (re-deposits).
- Server learns the current tab from GridViewPayload (tab field, sent on every rebuild/
  scroll). No separate pin packet; no client-side optimistic toggle — server sends UiState
  sync (pins) BEFORE vault sync; the vault sync's updateData drives the rebuild/regroup.
- Screen routes Pin-box click AND drag-release through slotClicked(PIN_SLOT); on release it
  first disarms vanilla quick-craft (accesswidener: isQuickCrafting mutable + quickCraftSlots
  accessible on AbstractContainerScreen) so the release can't scatter the carried stack into
  dragged-over slots.
- User pins lead button pins lead the sort, ALL modes including Count. Cap 54 pins/tab.
- Hover tooltip on the Pin box states the stack is deposited.

## 7. Fixes / hard-won rules (apply during every port)

- PERF: never put filesystem checks (config mtime polls) or item recounts inside per-frame or
  per-item paths — poll once at screen-open, cache counts per vault sync, hoist selected-button
  predicates out of rebuild loops. (This was a visible hover-lag bug.)
- Categorical-tab freeze: config catalogs can carry `"id": []` items entries — tabsFor must
  map EMPTY to uncategorized, or primaryTabOrder's get(0) throws on every rebuild and the grid
  freezes visually (render-thread exception in the client log, no crash).
- Cursor rule (existing, reaffirmed): NEVER mutate the cursor from a custom packet handler —
  stateId desync reverts it. All cursor mutation inside menu.clicked() + setCarried +
  broadcastFullState.
- Drop-target rule (the pin lesson): a painted hot-area is NOT a drop target — a carried item
  only "drops" on a real Slot. Any drop-gesture UI element must be backed by a real slot and
  routed through the vanilla click protocol.
- DEBUG idiom: pins/settings missing server-side = the client gesture never fired; check the
  client log for render-thread exceptions — a throwing rebuild leaves the grid frozen, not
  crashed.

## 8. Data work (regenerate per family, do not hand-port)

- `keywords.json`: 362 keywords, ~9,190 mappings at 1.2.0 (includes 49 Artifacts-mod items
  with trinket/wearable/modded words — Artifacts only where that mod exists). Reachability
  RULE: when button/data edits would orphan items, NOTHING DISAPPEARS SILENTLY — run the
  reachability check and route every orphan to the section's Misc-style button or Odds & Ends.
- ~31 data/layout revisions are baked into the 1.2.0 bundled defaults — port the FINAL
  bundled `buttons.json` / `keywords.json` / `categories.json` / `sort_family` / `sort_type` /
  `sort_groups.json`, then regenerate sorts from that family's registry dump. Older families
  lack some items (e.g. 26.x blocks) — the generator's coverage validation flags them.

## Porting notes (26.1.2-isms to map)

- ContainerInput (26.1.2 rename of ClickType); MouseButtonEvent.hasShiftDown().
- Accesswidener entries: Slot.x/y mutable; AbstractContainerScreen imageWidth/imageHeight
  mutable; isQuickCrafting mutable + quickCraftSlots accessible. Older families use AT/AW per
  loader; obfuscated families need mapped names.
- GuiGraphics.blit(RenderPipelines.GUI_TEXTURED, ...) for texture icons — older versions use
  the drawTexture/blit overloads of that version.
- StreamCodec/CustomPacketPayload networking is 1.20.5+; 1.20.1/1.20.4 use the legacy
  FriendlyByteBuf packet API — payloads (UiState, UiStateSync, GridViewPayload tab field)
  need hand-written read/write there.
