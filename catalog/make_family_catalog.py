#!/usr/bin/env python3
"""Generate per-family Bank Vault catalogs from the 26.x master judgments.

Derives categories.json / sort_family.json / sort_type.json for an MC version
family by filtering the 26.x bundled defaults (the master judgment set) down to
the family's real item registry (union across the family's registry dumps),
applying an id rename map (26.x id -> family id).

tabs / colors / sortLists / tabSort carry over verbatim from the master
(same approach that produced family-1.20.4 on 2026-06-04).

Usage:
  python make_family_catalog.py <family> <ver1> [<ver2> ...] [--rename a=b ...]
Example:
  python make_family_catalog.py 1.20.6 1.20.5 1.20.6 --rename minecraft:iron_chain=minecraft:chain
"""
import json, sys, os

MASTER = r"C:\Users\user\Local_Research\Minecraft\mods\bank-vault\src\main\resources\data\bankvault"
DUMPS = r"C:\Users\user\Local_Research\Minecraft\registry-dump"
OUT_ROOT = r"C:\Users\user\OneDrive\Projects\Minecraft\mods\bank-vault\catalog"


def main():
    args = sys.argv[1:]
    renames = {}
    if "--rename" in args:
        i = args.index("--rename")
        for pair in args[i + 1:]:
            a, b = pair.split("=")
            renames[a] = b
        args = args[:i]
    family, versions = args[0], args[1:]

    union = set()
    for v in versions:
        reg = json.load(open(os.path.join(DUMPS, v, "generated", "reports", "registries.json"), encoding="utf-8"))
        ids = set(reg["minecraft:item"]["entries"].keys())
        print(f"{v}: {len(ids)} item ids")
        union |= ids
    print(f"family union: {len(union)}")

    cats = json.load(open(os.path.join(MASTER, "categories.json"), encoding="utf-8"))
    out_items, dropped = {}, []
    for mid, tabs in cats["items"].items():
        fid = renames.get(mid, mid)
        if fid in union:
            out_items[fid] = tabs
        else:
            dropped.append(mid)
    print(f"items: {len(out_items)} kept, {len(dropped)} dropped (not in family registry)")

    out_cats = {"tabs": cats["tabs"], "items": out_items, "colors": cats["colors"],
                "sortLists": cats["sortLists"], "tabSort": cats["tabSort"]}

    out_dir = os.path.join(OUT_ROOT, f"family-{family}")
    os.makedirs(out_dir, exist_ok=True)

    def filt_sort(name):
        src = json.load(open(os.path.join(MASTER, name), encoding="utf-8"))
        out, total = {}, 0
        for tab, ranked in src.items():
            kept = [renames.get(i, i) for i in ranked if renames.get(i, i) in union]
            out[tab] = kept
            total += len(kept)
        print(f"{name}: {len(out)} tabs, {total} ranked ids")
        return out

    for name, data in [("categories.json", out_cats),
                       ("sort_family.json", filt_sort("sort_family.json")),
                       ("sort_type.json", filt_sort("sort_type.json"))]:
        p = os.path.join(out_dir, name)
        with open(p, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(data, fh, indent=2, sort_keys=False)
            fh.write("\n")
        print(f"wrote {p}")

    rep = os.path.join(out_dir, "dropped-ids.txt")
    with open(rep, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(sorted(dropped)) + "\n")
    print(f"dropped list -> {rep}")


if __name__ == "__main__":
    main()
