# Bank Vault keyword-table generation rules (v1.2)

You are generating descriptive keyword tags for Minecraft items (Java 26.1.2 era).
This is NOT the category system — categories answer "which drawer"; keywords answer
"what IS this thing". Example targets set by Dave:

  minecraft:wooden_sword -> ["wood","weapon","combat","hand2hand","breakable","earlyGame"]
  minecraft:trident      -> ["ocean","mobDrop","weapon","combat","flying","lightning","loyalty","hand2hand","midGame"]

## Output format

Write ONE file: keywords_batch_NN.json (NN = your batch number) in this same folder.
Strict JSON, UTF-8, no comments, no trailing commas:

{
  "batch": NN,
  "newWords": ["wordA", "wordB"],
  "items": {
    "<item id>": ["word", "word", ...],
    ...every id from your chunk file, in the same order...
  }
}

## Rules

1. Every item id from your chunk file MUST appear exactly once. No extras, no skips.
2. 4-9 words per item. Quality over quantity — every word must be TRUE for that item
   in current Minecraft (26.1.2 era). Do not guess mechanics you are unsure of.
3. Use ONLY words from the vocabulary below, except rule 4.
4. If no existing word fits a real, important property, you may coin a new word:
   camelCase, singular, reusable (think "would 5+ other items use this?"). Every
   coined word MUST be listed in your "newWords" array. Reuse before you invent —
   check the vocabulary twice before coining.
5. Word style: camelCase combos (hand2hand, earlyGame, mobDrop), singular forms.
6. Suggested ordering inside each item's list: material -> kind -> combat -> game
   stage -> obtainment -> mechanics -> place/flavor -> mob association -> misc.
   Ordering is cosmetic; correctness matters more.
7. Game stage (exactly one per item where meaningful): earlyGame (wood/stone/pre-iron
   reachable), midGame (iron through pre-Nether-fortress), lateGame (Nether/brewing/
   enchanting tier), endGame (End, Wither, netherite, trial chambers' heavy loot).
   Purely decorative/technical items may skip game stage.
8. Spawn eggs, command blocks, jigsaw/structure/test blocks, debug stick, barrier,
   light: tag with "technical" and "creativeOnly" plus whatever else applies (egg,
   mob word). Keep them short (3-5 words is fine for these).
9. travelersbackpack:* items are the Travelers Backpack mod: backpacks come in mob/
   material themes (the id says which), sleeping bags are colored beds, upgrades are
   container upgrades. Tag sensibly: container/backpack/sleepingBag/upgrade + theme
   words + "modded". bankvault:bank_vault is our own vault block: container, block,
   modded, multiblock, lateGame.
10. An item's iconic enchant affinities may appear as words ONLY where the enchant is
    part of the item's identity (trident -> loyalty/channeling/riptide; elytra ->
    mending-ish is NOT iconic, skip it; fishing rod -> luckOfTheSea is fine).
11. Colored variants (16 wool colors etc.): tag the family words + "colored". Do not
    coin a word per color.

## Vocabulary

Material: wood, stone, copper, iron, gold, diamond, netherite, leather, glass, wool,
bone, slime, honey, amethyst, quartz, obsidian, bamboo, clay, ice, coral, paper,
string, prismarine, terracotta, concrete, brick

Kind: weapon, tool, armor, food, drink, potion, block, plant, seed, flower, dye,
redstoneComp, rail, minecart, boat, container, decoration, lightSource, music, book,
map, banner, head, egg, ore, ingot, gem

Combat: combat, hand2hand, ranged, thrown, projectile, ammo, shield, explosive, trap

Game stage: earlyGame, midGame, lateGame, endGame

Obtainment: craftable, smeltable, mineable, farmable, fishable, brewable, mobDrop,
bossDrop, lootOnly, tradeable, bartering, archaeology, sniffing, creativeOnly

Mechanics: breakable, enchantable, wearable, edible, placeable, throwable, rideable,
growable, renewable, burnable, fuel, compostable, flammable, waterloggable, gravity,
flying, lightning, teleport, loyalty, channeling, riptide, luckOfTheSea

Place/flavor: overworld, nether, end, ocean, cave, sky, village, jungle, desert,
swamp, mountain, snow, mushroom, ancientCity, trialChamber

Mob association: zombie, skeleton, creeper, spider, enderman, blaze, ghast, wither,
dragon, villager, piglin, allay, sniffer, axolotl, frog, bee, turtle, copperGolem

Misc: colored, stackable, unstackable, rare, unique, technical, jukebox,
smithingTemplate, trim, pottery, bannerPattern, modded, multiblock, backpack,
sleepingBag, upgrade
