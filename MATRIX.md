# Bank Vault -- Unification Campaign MATRIX (authoritative state)

Campaign start: 2026-07-03 ~00:45. Branch: `minecraft-1.20-26.3`. Target version: **1.4.0**.
Runbook: Memory projects/mod-consolidation-guide.md. Copy-from: lava-boats minecraft-1.20-26.3.
Coverage model: M1's full-coverage segmentation (every playable MC 1.20.0 -> 26.3-snapshot)
PLUS NeoForge/1.20.4 (BV already published that line; M1 skipped it).

## Locked decisions
- Version 1.4.0 (minor bump over highest line 1.3.0; unification + new features).
- Build order: VERSION-MAJOR (one MC version across all loaders, then next) -- Dave 2026-07-03.
- Architectury: TOTAL removal (Dave explicit + standing hard rule). No arch at build or runtime.
- New feature scope: hidden AI/M1-friendly surface (machine-readable outputs, stable id#hash keys,
  query commands). Additive only -- 1.2.x/1.3.0 clients stay protocol-compatible.
- Publish HELD. Old branches retired only after their line passes the exhaustive boot gate.
- Dot injector ON (id 7434851a) -- DISABLE at campaign end.

## Cell matrix (dist jar = X; "26" = matrix cell building 26.1/26.2/26.3)

| MC cell | Fabric | NeoForge | Forge | Notes |
|---------|--------|----------|-------|-------|
| 1.20    | [x]    | --       | --    | new; covers 1.20.0-1.20.4 era per M1 pattern |
| 1.20.1  | --     | --       | [x]   | new; Forge jar also serves NeoForge <=1.20.1 (mod-deploy cutover) |
| 1.20.4  | --     | [x]      | --    | keep published NeoForge 1.20.4 line (ex 1.20.4-gap) |
| 1.20.6  | [x]    | [x]      | [x]   | existing line (arch -> rebuild) |
| 1.21    | [x]    | [x]      | [x]   | new |
| 1.21.1  | [x]    | [x]      | [x]   | existing (arch -> rebuild; Forge cell already FG6) |
| 1.21.2  | [x]    | [x]      | --    | new |
| 1.21.5  | [x]    | [x]      | [x]   | existing |
| 1.21.6  | [x]    | --       | --    | new (M1 pattern) |
| 1.21.8  | [x]    | [x]      | [x]   | existing; Forge ceiling (FG6) |
| 1.21.11 | [x]    | [x]      | --    | existing |
| 26      | [x] x3 | [x] x2   | --    | existing non-arch matrix cells (moved to <Loader>/26/) |

Jar count: Fabric 13, NeoForge 10, Forge 6 = **29 jars**.
(M1 reference = 27; +1 NeoForge/1.20.4, +1 because M1's NeoForge lacks 1.20.4 -- verify exact M1
cell ranges when building; if a listed new cell proves a loader gap (loader never shipped for that
MC), record it in mod-version-gates.md and mark `--` here instead.)

## Era model (initial; refine as compiler enumerates drift)
- 1.20-1.20.4: JDK 17, old toml/fabric.mod.json era, pre-component ItemStack NBT era
- 1.20.6-1.21.1: JDK 21, components era, pre-1.21.2 mixin-level JAVA_17 on forge/neoforge
- 1.21.2-1.21.11: JDK 21, modern loaders
- 26.x: mojmap-shipped, matrix-templated cells, pack_format range form

## Stage checklist
- [x] Stage 0.1 context loaded (guide, bank-vault.md, repo survey, M1 matrix)
- [x] Stage 0.2 pre-unify/* tags (1.20.x, 1.21.x, 26, main, tooling)
- [x] Stage 0.3 branch minecraft-1.20-26.3 from 26 + worktree
- [x] Stage 0.4 restructure: <Loader>/26/ cells, srcDirs fixed, scripts/ (walkers to be rewritten)
- [x] Stage 0.5 MATRIX.md (this file)
- [ ] Stage 0.6 project file + handoff updated with kickoff (handoff done 00:55; bank-vault.md pending)
- [ ] Stage 1 brains + arch Common merge + cog-gen.ps1 + check-sync.ps1
- [ ] Stage 2 cells version-major 1.20 -> 26.3 (pilot 26 line first to prove moved cells still build)
- [ ] Stage 2b M1 AI-feature layer (design after m1.md + m1-agent.md read)
- [ ] Stage 3 exhaustive boot gate + branch retirement
- [ ] Stage 4 cleanup + docs + push; injector OFF; publish HELD

## Session notes (append-only)
- 2026-07-03 00:58 Stage 0 restructure done. dist/ was untracked in old 26 worktree -- mod-deploy
  staging_dirs still points at `26\dist`; repoint to this worktree's dist/ at Stage 2.6.
- 26.3 matrix entry in build-fabric.ps1 pins 26.3-snapshot-1; bump to current snapshot at Stage 2.
- 2026-07-03 01:25 26-line convergence: 8 loader-duplicated classes -> shared_minecraft via new Platform + ClientNet seams (config-dir/isLoaded/client-send drift). Both 26 cells rebuilt green (Fabric+NeoForge 26.2 pilot). Remaining per-loader 26 files: entrypoints, ModNetworking, TrinketCompat, registry seams, BankVaultClient.
- 2026-07-03 01:50 M1 AI surface added: api/VaultApi.java (snapshot/list/count/find/withdraw/deposit, single-line BV|op|OK pipe format) + hidden /bank api subcommand in BankCommand. Version bumped 1.4.0 both 26 cells. Fabric+NeoForge 26.2 build green.
- 2026-07-03 02:05 _codegen skeleton in place: compat_core.py (BV drift axes: renamed@1.21.11, components@1.20.5, perms26, pluralData<1.21, itemDefs@1.21.4, PACK_FORMATS, SLOT_CHANGED_SIG per-era AW/AT table), cog-gen.ps1 (BV era rules: data-dir renames, items/ strip, pack.mcmeta), check-sync.ps1 (auto-discovering twins). Next: first pre-26 cell pair (Fabric+NeoForge 1.21.11) via M1 cell wiring; let the compiler enumerate drift.
- 2026-07-03 02:55 MILESTONE: Fabric/1.21.11 cell BUILD SUCCESSFUL, 0 errors 0 warnings (first pre-26 cell from unified source). Mechanism proven: Gfx facade (text/item/pose/tooltip/entity-preview era names), era render entries (extractRenderState@26 vs render+renderBg+renderLabels pre-26), ClickType/payload-registry/copy-stream/TrinketCompat-stub cogs, AW namespace named. check-sync green across 15 twins. Commit a11bd7b.
- 2026-07-03 03:20 NeoForge/1.21.11 green 0/0 (MDG 2.0.141, neo 21.11.42). New gates: BreakBlockEvent(26) vs BlockEvent.BreakEvent(pre-26); NeoForge 1.21.x deprecates CreativeModeTab.builder(Row,int) -> no-arg builder(). 1.21.11 ROW COMPLETE (Fabric+NeoForge). Next: 1.21.8 row (adds Forge).
- 2026-07-03 03:55 1.21.8 Fabric+NeoForge green 0/0. New machinery: Ev input shim (input records @1.21.9 vs primitives), perms26 marker (permissions() @1.21.9), cog-gen tree renames (Identifier->ResourceLocation <1.21.11, GameProfile name()->getName() <1.21.9). 26 regression green, check-sync green. WATCH: 1.21.11 jar claims 1.21.9-1.21.11 -- verify GameProfile.name()/permissions() exist at 1.21.9/1.21.10 in the boot gate. Next: Forge/1.21.8 (first Forge cell).
- 2026-07-03 04:35 Forge/1.21.8 green 0/0 -- 1.21.8 ROW COMPLETE (all 3 loaders). Forge glue built in cog_sources/forge (EventBus7/BusGroup entrypoint, PayloadChannel networking w/ sp() double-decode guard, ForgeRegistries seams, FMLClientSetup screen+sinks). Trap re-learned: FG6 ATs need SRG names (gate recorded). Claim [1.21.6,1.21.9) javafml 56. Next rows: 1.21.5 (Fabric+NeoForge+Forge), then 1.21.2/1.21.1/1.21/1.21.6-era gaps per matrix.
- 2026-07-03 05:10 1.21.5 ROW COMPLETE (3 loaders, 0/0). New era machinery: Gfx mid-era branch (PoseStack pose, renderTooltip, blit Function form; blitGuiTextured semantic call), BE save/load era (ValueInput/Output@1.21.6 vs CompoundTag+Provider, getStringOr@1.21.5), NeoForge ClientPacketDistributor boundary (>=1.21.8; PacketDistributor.sendToServer below), Forge EventBus 6/7 whole-file era entrypoint+client (eb7@Forge58/MC1.21.8). Claim adjustments: Forge 1.21.8 jar narrowed to [1.21.8,1.21.9) javafml 58; Forge 1.21.5 jar claims [1.21.5,1.21.8) javafml 55 (EventBus6 span). Next: 1.21.2 row.
- 2026-07-03 05:45 1.21.2 ROW COMPLETE (Fabric+NeoForge 0/0, first GAP-FILL cells -- BV never shipped 1.21.2). New gate: Slot.getNoItemIcon returns Pair<atlas,sprite> <1.21.4 vs single id (emit_no_item_icon_shield). fabric-api for 1.21.2 = 0.106.1+1.21.2 (0.114.0 does not exist). NeoForge 21.2.1-beta. Next: 1.21.1 + 1.21 rows (registry get/getValue boundary @1.21.2, Gfx old-era blit).
- 2026-07-03 06:20 1.21.1 + 1.21 ROWS COMPLETE (6 cells, all 0/0; 1.21 row = gap-fill, first-ever BV Forge 1.21/51). New gates this pass: slotChangedCraftingGrid takes Level (not ServerLevel) <=1.21.1 (per-cell AW/AT descriptor switch), Properties.setId/BlockItem id key @1.21.2+ (emit_block_props_tail/emit_blockitem_*), OMINOUS_BOTTLE_AMPLIFIER component Integer <1.21.2 vs record, BlockEntityType direct ctor @1.21.2+ vs Builder.of().build(null), registry getValue->get rename <1.21.2 (multi-line-aware), FabricBlockEntityTypeBuilder deprecated at 1.21.1 (vanilla Builder used pre-1.21.2). Remaining rows: 1.21.6 (Fabric), 1.20.6 trio, 1.20.4 Neo, 1.20.1 Forge, 1.20 Fabric, 26.3 snapshot bump.
- 2026-07-03 07:05 1.21.6 (Fabric) + 1.20.6 ROW (3 loaders) COMPLETE, all 0/0. New gates: RL factory fromNamespaceAndPath @1.21+ vanilla (Forge backported @1.20.4) -> cog-gen 7e ctor rename (matches Identifier-form pre-7b); ArmorSlot class @1.21+ (plain Slot + AW/AT line dropped below); ItemEnchantments.Mutable.set(Holder) @1.21+ vs raw Enchantment; slotChangedCraftingGrid 5-arg <1.21 (call cog + 5-arg AW descriptor). Fabric range split: 1.21.5 jar <1.21.6, new 1.21.6 jar >=1.21.6- <1.21.8. REMAINING CELLS: NeoForge/1.20.4, Forge/1.20.1, Fabric/1.20 -- ALL pre-components era (NBT stacks, raw-buf networking, no StreamCodec/ItemEnchantments/PotionContents/OminousBottle): the widest drift, next work item. Then 26.3 snapshot bump + walkers + dist + boot gate.
- 2026-07-03 08:35 PRE-COMPONENTS ERA CRACKED: Fabric/1.20 cell green 0/0 -- builds vs 1.20.1, jar claims 1.20-1.20.4. Machinery: BvPayload shim + table-driven raw-buffer payload gen (compat_payloads, buffer-order-safe), fabric raw channels, NBT StackStore/ContainerExtractor(1.2.4 template)/fillAll(PotionUtils+EnchantedBookItem)/sort-keys, block use() era, Ev SharedConstants sub-era, Gfx 7-arg entity preview. Mid-era regression caught+fixed (ContainerExtractor whole-file passthrough now era-corrects copy-stream). Remaining cells: NeoForge/1.20.4 (BvPayload extends CustomPacketPayload plan), Forge/1.20.1 (SimpleChannel, JDK17).
- 2026-07-03 10:05 *** ALL 24 CELLS GREEN 0/0 *** Forge/1.20.1 (SimpleChannel era, forge-47 deprecation pass 7f, SRG AT 5-arg descriptor) + NeoForge/1.20.4 (NeoGradle 7 + Gradle 8.14 -- MDG floor is 20.5; RegisterPayloadHandlerEvent/IPayloadRegistrar era; ctor (IEventBus); BvPayload extends CustomPacketPayload @1.20.2-1.20.4; Ev chat-char boundary corrected to 1.20.5). Remaining Stage 2: walker rewrite (all cells -> dist/), 26.3 snapshot-2 bump, full dist build, mod-deploy repoint+1.4.0. Then Stage 3 boot gate.
- 2026-07-03 12:00 *** STAGE 2 COMPLETE: 28/28 JARS IN dist/ *** Fabric x12 (1.20..26.3), NeoForge x10 (1.20.4..26.2), Forge x6 (1.20.1..1.21.8), all 1.4.0. Late catches: 26.3-snapshot-2 REMOVED block codecs (26 cells now cog-materialize for 26.3+ via -Ver/-PuseGen; gate recorded); CRLF .Replace trap re-bitten once (python patch used). mod-deploy repointed to the new dist + 1.4.0. NEXT: Stage 3 exhaustive boot gate (mod-deploy --smoketest --apply; serial boots per claimed version), then branch retirement, docs, push.
