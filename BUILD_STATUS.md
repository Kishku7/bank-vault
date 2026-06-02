# Bank Vault — Build Status

**v0.4 — single-block vault; builds green, load-tested, deployed to Fabric_Testing.** (2026-06-01)

- Toolchain: Fabric Loom 1.16, MC 26.1.2, Java 25, Fabric API ≥0.145.
- Deploy target during dev: `C:\ModrinthApp\profiles\Fabric_Testing\mods` (production `Fabric 26.1.2` + servers only when perfected).
- Build workspace: `Local_Research\Minecraft\mods\bank-vault`; source mirrored to OneDrive via `mc-sync.ps1`.

## Architecture (current)

- **ONE block: `bankvault:bank_vault`** (replaced corner/edge/center). Place nine in a 3×3 vertical wall.
- **Form/unform:** blocks are plain riveted steel until the 9th completes the 3×3; the form manager then stamps each block a `part` (corner/edge/center, by position) + a shared facing + the owner UUID, so the door appears. Break one → reverts to plain. Door shows on front AND back faces; steel on the four sides.
- Every block has a BlockEntity; any formed block is a hopper sink and an access point. Item icon = the wheel; creative tab "Bank Vault"; recipe 2×2 iron blocks → 2 (tunable).
- Bank data, capacity, `/bank` commands, hopper deposit, and the GUI (13 tabs, grid, click-to-withdraw, upgrade rail, spinning wheel) all carry over unchanged.

## Working

Blocks/multiblock (single-block, form/unform, per-position part) · bank data (banks/index/invites JSON, Ender-Chest model, capacity) · hopper deposit · `/bank` suite (perms/merge/succession/list/withdraw/upgrade) · GUI · 5 Gemini textures wired (vault_metal + corner/edge/center fronts + animated wheel) + wheel item icon.

## Remaining / polish

1. **Per-corner/edge rotation** — corners/edges currently show their art in one orientation; rotating per position (or slicing one full door image into nine tiles) makes the frame trace the border seamlessly. Pairs with a single straight-on door image from Gemini.
2. **Search box** in the GUI.
3. **Container extractors** (shulker/bundle/Traveler's Backpack shift-click + recursion + return slot).
4. **POM/labPBR** normal+specular maps (depth under shaders).
5. **Polish:** wheel-only-while-open (block-entity renderer), sounds, recipe tuning.
