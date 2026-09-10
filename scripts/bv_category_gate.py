#!/usr/bin/env python3
"""
bv_category_gate.py -- assert that Bank Vault's SHIPPED data places every explorer
map where it should, and that renamed Minecraft ids are carried under BOTH names.

Reads the four data files out of a built jar (or a data dir), so it checks the
ARTIFACT rather than the working tree. Cheap, deterministic, offline; meant to run
pre-publish alongside the other metadata checks.

  python bv_category_gate.py <jar-or-data-dir> [--expect-category materials]

WHY THIS EXISTS
  Minecraft renamed eight explorer maps at 26.3-pre-1. Bank Vault had all eight
  curated under their 26.3-snapshot-7 names, so on every 26.3 build after
  snapshot-7 they silently fell out of categorization: no compile error, no log
  line, and nothing in the bank JSON to notice. Set-subtraction against the
  version's own item list is the only thing that finds that class of bug, and
  nothing was running one.

  It also enforces the cross-version rule (mod-rules.md): BV's data ships to every
  cell from 1.20.0 to 26.3, so a renamed id must be present under BOTH names.
  Swapping fixes the newest cell and breaks every older one, the same way round.

WHAT IT PROVES, AND WHAT IT DOES NOT
  It proves the shipped DATA maps these ids to the expected category -- the exact
  file Catalog loads. It does NOT prove the vault GUI renders them there. BV's only
  per-item category readout today is a hover tooltip in BankVaultScreen
  ("Found under: <section> -> <button>"), which is a poor thing to scrape: one
  frame, mouse-position dependent. If a live gate is wanted, the durable fix is to
  expose the category on the machine-readable VaultApi channel instead.

  Note also that a config-dir categories.json WINS over the bundled copy
  (Catalog.ensureLoaded). Since 1.4.10 fillMissingItems() backfills ids the bundle
  knows and the config lacks, so added ids do reach existing installs -- but any
  live test must still account for the cell's own config copy.
"""
import argparse
import io
import json
import os
import sys
import zipfile

DATA = ("categories.json", "keywords.json", "sort_family.json", "sort_type.json")

# Minecraft id renames BV must carry under both names.
# old (valid up to and including its last version) -> new (from the rename on)
RENAME_PAIRS = [
    # renamed at 26.3-pre-1; the old names are valid at 26.3-snapshot-7 and below
    ("abandoned_campsite_map", "abandoned_camp_map"),
    ("ancient_city_map", "buried_ancient_city_map"),
    ("jungle_explorer_map", "jungle_pyramid_map"),
    ("mineshaft_map", "buried_mineshaft_map"),
    ("ocean_explorer_map", "ocean_monument_map"),
    ("swamp_explorer_map", "swamp_hut_map"),
    ("trial_explorer_map", "buried_trial_chambers_map"),
    ("woodland_explorer_map", "woodland_mansion_map"),
]

# Explorer maps whose ids Mojang did NOT change. Half the family staying stable is
# what made the rename easy to miss, so they are asserted too.
STABLE_MAPS = [
    "buried_treasure_map", "desert_pyramid_map", "desert_village_map",
    "plains_village_map", "savanna_village_map", "snowy_village_map",
    "taiga_village_map", "warm_ocean_ruins_map",
]


def die(msg):
    print("ABORT: %s" % msg)
    sys.exit(2)


def load_data(src):
    """Return {filename: parsed json} from a jar or a data dir."""
    out = {}
    if os.path.isdir(src):
        for f in DATA:
            p = os.path.join(src, f)
            if not os.path.exists(p):
                die("missing %s in %s" % (f, src))
            out[f] = json.load(io.open(p, encoding="utf-8"))
        return out
    if not zipfile.is_zipfile(src):
        die("not a jar or a data dir: %s" % src)
    with zipfile.ZipFile(src) as z:
        names = set(z.namelist())
        for f in DATA:
            entry = "data/bankvault/" + f
            if entry not in names:
                die("jar has no %s" % entry)
            out[f] = json.loads(z.read(entry).decode("utf-8"))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("target", help="built jar, or a data/bankvault dir")
    ap.add_argument("--expect-category", default="materials")
    a = ap.parse_args()

    d = load_data(a.target)
    cats = d["categories.json"].get("items", {})
    kws = d["keywords.json"].get("items", {})
    famset = set(d["sort_family.json"].get(a.expect_category, []))
    typset = set(d["sort_type.json"].get(a.expect_category, []))

    fails = []

    def check(name, ok, detail=""):
        print("  [%s] %s %s" % ("PASS" if ok else "FAIL", name, detail))
        if not ok:
            fails.append(name)

    def cat_of(item_id):
        v = cats.get("minecraft:" + item_id)
        if v is None:
            return None
        if isinstance(v, list):
            return v[0] if v else None
        return v if isinstance(v, str) else None

    print("target: %s" % a.target)
    print("expect category: %s" % a.expect_category)

    # A -- every explorer map, renamed or not, lands in the expected category
    every = STABLE_MAPS + [n for _, n in RENAME_PAIRS] + [o for o, _ in RENAME_PAIRS]
    wrong = [m for m in every if cat_of(m) != a.expect_category]
    check("A_all_maps_categorized(%d)" % len(every), not wrong,
          "misplaced=%s" % (", ".join("%s->%s" % (m, cat_of(m)) for m in wrong[:6]) or "none"))

    # B -- both halves of every rename pair exist, and agree on category
    missing, disagree = [], []
    for old, new in RENAME_PAIRS:
        co, cn = cat_of(old), cat_of(new)
        if co is None:
            missing.append(old)
        if cn is None:
            missing.append(new)
        if co is not None and cn is not None and co != cn:
            disagree.append("%s(%s)!=%s(%s)" % (old, co, new, cn))
    check("B_rename_pairs_both_present(%d)" % (len(RENAME_PAIRS) * 2), not missing,
          "missing=%s" % (", ".join(missing[:6]) or "none"))
    check("B2_rename_pairs_same_category", not disagree,
          "disagree=%s" % (", ".join(disagree[:4]) or "none"))

    # C -- both halves keyworded identically, so search and the keyword buttons
    #      behave the same whichever id the running version resolves
    kwbad = []
    for old, new in RENAME_PAIRS:
        ko = kws.get("minecraft:" + old)
        kn = kws.get("minecraft:" + new)
        if ko is None or kn is None:
            kwbad.append("%s/%s missing" % (old, new))
        elif sorted(ko) != sorted(kn):
            kwbad.append("%s!=%s" % (old, new))
    check("C_keywords_match", not kwbad, "bad=%s" % (", ".join(kwbad[:4]) or "none"))

    # D -- both halves present in the curated sort orders, or the sort silently
    #      drops one on whichever version resolves it
    sortbad = []
    for old, new in RENAME_PAIRS:
        for i in (old, new):
            k = "minecraft:" + i
            if k not in famset:
                sortbad.append("family:" + i)
            if k not in typset:
                sortbad.append("type:" + i)
    check("D_in_curated_sort_orders", not sortbad,
          "missing=%s" % (", ".join(sortbad[:6]) or "none"))

    print("BV_CATEGORY_GATE=" + ("FAIL" if fails else "PASS"))
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
