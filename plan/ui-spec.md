# Bank Vault — UI Specification

Extracted from `docs/Bank Vault UI (standalone).html` — Claude.ai Design mockup, 2026-05-31.

## Target Dimensions

| | Value |
|-|-------|
| Preferred size | 1480 × 940 px |
| Minimum size | 1004 × 720 px |
| Target resolution | 1024×768 minimum monitor |

## Color Palette

| Variable | Hex | Role |
|----------|-----|------|
| `--face` | `#34343a` | Raised surface background |
| `--light` | `#63636d` | Bevel highlight (top/left edges) |
| `--dark` | `#131316` | Bevel shadow (right/bottom edges) |
| `--accent` | `#e0a92e` | Gold — selected tab, stats, return slot, active states |
| `--accent-d` | `#9c7416` | Darker gold (sparingly used) |
| `--well` | `#191920` | Inset/sunken surface background |
| `--pbg` | `#2a2a30` | Panel body background |
| Overlay bg | `#0c0d10` | Page background |
| Overlay dimmer | `rgba(6,7,9,0.74)` | Backdrop behind vault panel |
| Body text | `#d6d6db` | Default text |
| Title text | `#f1f1f4` | Title bar text |
| Slot bg | `#2a2a31` | Item grid slot background |
| Slot border dark | `#131318` | Slot bevel shadow |
| Slot border light | `#4a4a53` | Slot bevel highlight |
| Slot hover | `#4a4a55` | Slot hover background |
| Upgrade filled | `#232a22` | Filled upgrade slot (slight green tint) |
| Tooltip bg | `#160e22` | Deep purple |
| Tooltip border | `#25154a` | |
| Close button | `#6b2b27` | Red |

## Bevel System

All surfaces use a 2-tone bevel (no rounded corners — pixel aesthetic):
- **Raised:** `--light` on top/left, `--dark` on right/bottom
- **Inset:** `--dark` on top/left, `#45454c` on right/bottom

## Typography

**DECIDED (2026-05-31): ship NO custom fonts — render everything with Minecraft's built-in `minecraft:default` font** via `Minecraft.getInstance().font`. The Pixelify Sans / Silkscreen / VT323 fonts in the HTML mockup were only for browser-preview fidelity. In-game, visual hierarchy comes from **scale + color + drop shadow**, not font family.

Size mapping (mockup px → vanilla font, scaled with `PoseStack`):

| Mockup role | Mockup font / size | Vault rendering |
|-------------|--------------------|-----------------|
| Tiny labels, count badges, footer, hints | Silkscreen 8–11px | vanilla font at **native 1.0 scale** (glyphs ~7px, line height 9) — no scaling |
| Body, tab names, search input | Pixelify Sans 15–17px | vanilla font scaled **~1.75–2×** |
| Title | Pixelify 26px | vanilla font scaled **~2.5–3×** |
| Large stat / count numbers | VT323 26–30px | vanilla font scaled **~3–3.5×** |

Rendering notes:
- The mockup's count-badge "white + 1px black shadow" = vanilla shadowed text (`drawString` with shadow) — exactly how MC renders it.
- Measure with `font.width(text)`; render via the GuiGraphics text methods (`drawString` / `text` / `centeredText`). In 26.1.2 the render-state object is `GuiGraphicsExtractor` (confirmed against Collective source) — pin the exact type at scaffold time.
- Prefer near-integer scale factors for crispness; the default font softens slightly under fractional scaling.
- Fallback for any tiny text that looks muddy: `minecraft:uniform` (Unifont) — still built-in, nothing shipped.

## Layout Structure

```
┌─────────────────────────────────────────────────────┐
│  TITLE BAR (vault emblem | title + subtitle | stats | X) │
├──────────────┬──────────────────────────────────────┤
│              │  SEARCH BAR                          │
│  TAB BAR     ├──────────────────────────────────────┤
│  (13 tabs)   │                                      │
│              │  ITEM GRID           │ SCROLLBAR     │
│  UPGRADE     │  (auto-fill slots)   │ (16px wide)   │
│  SLOTS       │                                      │
│  (8×8)       ├──────────────────────────────────────┤
│              │  FOOTER STRIP                        │
│  RETURN SLOT ├──────────────────────────────────────┤
└──────────────┴──────────────────────────────────────┘
Left rail: 224px fixed width
```

## Title Bar

- Height: ~64px (40px emblem + 12+12px padding)
- **Vault emblem:** 40×40px, `--well` background, gold accent glow
- **Title text:** Pixelify Sans 26px bold, `letter-spacing: 2px`, color `#f1f1f4`
- **Subtitle:** Silkscreen 9px, `letter-spacing: 0.5px`, color `#8b8b93`
- **Stats display:** right-aligned, inset box style, `padding: 7px 18px`
  - Label: Silkscreen 8px, color `#7d7d86`
  - Value: VT323 30px, color `#e7e7ec` (accent values get gold glow)
  - Divider lines: 2px wide × 34px tall gradient
  - Shows: **Unique Items** | **Total Items** (and later: upgrade count)
- **Close button:** 34×34px, red (`#6b2b27`), bevel border, hover `#8a3531`, press reverses bevel

## Left Rail (224px)

### Tab Bar

- `padding: 10px`, `gap: 4px` between tabs
- Background: `--face`, raised bevel
- Each tab: Pixelify Sans 15px, `padding: 8px 8px 8px 10px`, bg `--well`, ~31px tall
- Tab layout: `[glyph 18px] [name flex-1] [count VT323 15px] [chevron 11px]`
- **Selected tab:** 3px gold left border, left-to-right gold gradient background, inset gold glow ring

**13 categories (DECIDED 2026-05-31):** tab order + glyphs are the source of truth in the `tabs` table of `catalog/item_catalog.db`. Per-item tab assignment lives in that DB's `items.tab` column (814 items assigned, 470 `skip` rows excluded from the vault).

| ID | Name | Glyph | Source |
|----|------|-------|--------|
| `wood` | Wood | ▤ | block subcategory `wood` |
| `copper` | Copper | ⬢ | block subcategory `copper` (own tab — 96 variants) |
| `colored` | Colored | ▩ | block subcategory `colored` (beds/candles/shulkers/glass) |
| `blocks` | Blocks | ▦ | remaining `blocks` category (natural, stone, ore, light…) |
| `items` | Items | ◆ | general `items` category |
| `food` | Food | ✦ | subcategory `food` |
| `tools` | Tools | ⚒ | subcategories `tool` + `weapon` |
| `armor` | Armor | ⛨ | subcategory `armor` |
| `redstone` | Redstone | ◉ | subcategory `redstone` |
| `enchanting` | Enchanting | ❖ | original `enchanting` category (incl. lapis) |
| `transport` | Transportation | ➤ | subcategory `transport` |
| `misc` | Misc | ✶ | music, archaeology, **spawn eggs** |
| `uncategorized` | Uncategorized | ◌ | runtime catch-all for unknown/modded items |

Glyphs are placeholders pending textures. The tab list may need to scroll in the left rail at minimum (720px) height; fine at the 940px preferred size.

### Upgrade Slots

- `padding: 10px`, raised bevel
- Grid: **8 columns × 8 rows = 64 slots**
- Each slot: **18×18px fixed** (does NOT scale with main grid `--slot`)
- Filled slot bg: `#232a22` (slight green tint)
- Current upgrade item: **chest** icon
- Header: "UPGRADES" label + "12/64" count in Silkscreen

### Return / Outbound Slot

- Single slot: **36×36px**
- Gold outline: `outline: 2px solid --accent`, `outline-offset: 1px`
- Animated: `pulse 1.8s ease-in-out infinite` — outline opacity cycles 100%→40%→100%
- Shows `⤴` glyph at rest (55% accent opacity)
- Hint text below: Silkscreen 8px, `line-height: 1.5`, color `#82828b`

## Main Area

### Search Bar

- `flex: 0 0 auto`, `padding: 9px 12px`, inset style (`--well` bg)
- Search icon: 18px, color `#6f6f79`
- Input: Pixelify Sans 17px, `letter-spacing: 0.5px`, transparent bg
- Placeholder: `#62626b`
- Right side meta text: Silkscreen 9px, `#71717a` (e.g. "412 items")

### Item Grid

- Fills all remaining space
- Layout: `display: grid; grid-template-columns: repeat(auto-fill, var(--slot))`
- **`--slot` sizes:** 18px (scale 1), **36px (scale 2, default)**, 54px (scale 3)
- At 36px with ~780px grid area: ~21 columns
- Slot structure:
  - Background: `#2a2a31`
  - Border: 1px dark (top/left) + 1px light (right/bottom) — inner bevel
  - Icon: 16px centered (scales with `--iscale`)
  - Count badge: Silkscreen 8px, bottom-right, white + 1px black shadow (scales with `--cscale`)
  - Hover: bg `#4a4a55`, inset white border highlight
- Inner scroll area: `padding: 4px`, hidden native scrollbar
- Empty state: Silkscreen 11px, `#6f6f79`, 40px padding — "No items in this category"

### Scrollbar

- **16px wide**, right edge of grid region
- Track: inset style, full height
- Thumb: 14px effective width (1px margin each side), raised bevel
- Thumb hover: `+22%` accent tint
- Cursor: `grab` / `grabbing`

### Footer Strip

- `flex: 0 0 auto`, `padding: 7px 10px`
- Silkscreen 9px, `letter-spacing: 0.5px`, color `#82828b`
- Background: `--face`, `border-top: 2px solid --dark`
- Shows active tab name in accent color, dot separators

## Slot Scale Options

The design exposes a scale control (for debug/accessibility):

| Scale | `--slot` | Use |
|-------|----------|-----|
| 1 | 18px | Dense / small monitor |
| 2 | 36px | **Default** |
| 3 | 54px | Accessibility |

For the mod implementation: expose as a client-side config option (default 36px).

## Implementation Notes

- Full-screen `HandledScreen` — not a fixed-size GUI texture
- All borders use the 2-tone bevel (no textures needed for borders, pure color)
- Fonts: vanilla `minecraft:default` only — no shipped font files (see Typography for the scale mapping)
- Pixel-crisp rendering: avoid anti-aliasing on all elements
- The 224px left rail and 16px scrollbar are fixed; item grid fills the remainder
- Upgrade slots use fixed 18px regardless of main grid scale
