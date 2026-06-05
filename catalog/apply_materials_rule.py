"""
Materials & Utility ingredient rule + manual tab corrections (Dave, 2026-06-04).

Applies to a categories.json (family or 26.x). Run with:
    python apply_materials_rule.py <categories.json in/out> <registry-dump base> <ver1> [ver2...]

MECHANICAL RULE -- add 'materials' to every catalog item that is:
  - a crafting/brewing input (recipes from the version server jars, item tags expanded;
    brewing ingredient list hardcoded), AND
  - non-placeable (id absent from the block registry; wheat force-included), AND NOT
  - tabbed 'gear' or 'food', a dye (_dye), or travel/combat/trim by name.

MANUAL RULES (always applied):
  +materials (keep existing tabs): item_frame, glow_item_frame, painting, nether_star,
                                   totem_of_undying
  disc_fragment_5      -> treasure only (never materials)
  netherite_upgrade_smithing_template -> ['enchanting']   (with the trims, not gear)
  map, filled_map      -> ['materials']                   (utility, not gear)
  firework_rocket, firework_star -> ['gear']              (fireworks ONLY in Gear & Combat)
"""
import json, zipfile, io, os, re, sys

BREWING = ['nether_wart','redstone','glowstone_dust','fermented_spider_eye','gunpowder','dragon_breath',
           'sugar','rabbit_foot','glistering_melon_slice','spider_eye','golden_carrot','magma_cream',
           'pufferfish','ghast_tear','turtle_helmet','phantom_membrane','blaze_powder']
NAME_X = re.compile(r'(_boat$|_raft$|minecart|_smithing_template$|_dye$|^minecraft:(elytra|saddle|compass|'
                    r'recovery_compass|spyglass|map|arrow|spectral_arrow|shield|trident|bow|crossbow)$)')
MANUAL_ADD = {'minecraft:item_frame','minecraft:glow_item_frame','minecraft:painting',
              'minecraft:nether_star','minecraft:totem_of_undying'}
MANUAL_BLOCK = {'minecraft:disc_fragment_5'}
MANUAL_REMOVE_MATERIALS = {'minecraft:brick', 'minecraft:nether_brick'}   # bricks are Stone, not Materials
# paper/maps/books are a family: contiguous group in the materials curated order, after paper
PAPER_GROUP = ['minecraft:map', 'minecraft:filled_map', 'minecraft:book']
MANUAL_SET = {
    'minecraft:netherite_upgrade_smithing_template': ['enchanting'],
    'minecraft:map': ['materials'],
    'minecraft:filled_map': ['materials'],
    'minecraft:firework_rocket': ['gear'],
    'minecraft:firework_star': ['gear'],
}
FORCE_NONPLACEABLE = {'minecraft:wheat'}   # item shares the crop-block id but is not placeable


def norm(i):
    return i if ':' in i else 'minecraft:' + i


def collect_ingredients(jars):
    tag_defs, raw = {}, set()

    def add_entry(e):
        if isinstance(e, str):
            raw.add(('tag', e[1:]) if e.startswith('#') else ('item', norm(e)))
        elif isinstance(e, dict):
            if 'item' in e: raw.add(('item', norm(e['item'])))
            if 'tag' in e:  raw.add(('tag', norm(e['tag'])))
            if 'id' in e:   raw.add(('item', norm(e['id'])))
        elif isinstance(e, list):
            for x in e: add_entry(x)

    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                # 26.x uses data/minecraft/recipe/ (singular); 1.20.x uses recipes/
                if re.match(r'data/minecraft/recipes?/.*\.json$', n):
                    r = json.loads(z.read(n))
                    for f in ['key','ingredients','ingredient','base','addition','template']:
                        if f in r:
                            v = r[f]
                            if f == 'key' and isinstance(v, dict):
                                for x in v.values(): add_entry(x)
                            else:
                                add_entry(v)
                elif re.match(r'data/minecraft/tags/items?/.*\.json$', n):
                    tid = norm(re.sub(r'^data/minecraft/tags/items?/', '', n)[:-5])
                    tag_defs.setdefault(tid, []).extend(json.loads(z.read(n)).get('values', []))

    def expand(tid, seen=None):
        seen = seen or set()
        if tid in seen: return set()
        seen.add(tid)
        out = set()
        for v in tag_defs.get(norm(tid), []):
            if isinstance(v, dict): v = v.get('id', '')
            if not v: continue
            if v.startswith('#'): out |= expand(v[1:], seen)
            else: out.add(norm(v))
        return out

    out = set()
    for kind, val in raw:
        out |= expand(val) if kind == 'tag' else {val}
    out |= {norm(b) for b in BREWING}
    return out


def main():
    cat_path, base = sys.argv[1], sys.argv[2]
    vers = sys.argv[3:]
    jars = [os.path.join(base, v, 'versions', v, f'server-{v}.jar') for v in vers]
    reg = json.load(io.open(os.path.join(base, vers[-1], 'generated', 'reports', 'registries.json'),
                            encoding='utf-8'))
    blocks = set(reg['minecraft:block']['entries'].keys())

    ingredients = collect_ingredients(jars)
    nonplaceable = {i for i in ingredients if i not in blocks} | (FORCE_NONPLACEABLE & ingredients)

    d = json.load(io.open(cat_path, encoding='utf-8'))
    items = d['items']
    added = []
    for iid, tabs in items.items():
        if iid in MANUAL_SET:
            items[iid] = list(MANUAL_SET[iid]); continue
        if iid in MANUAL_REMOVE_MATERIALS:
            items[iid] = [t for t in tabs if t != 'materials']; continue
        if 'materials' in tabs: continue
        mech = (iid in nonplaceable and 'gear' not in tabs and 'food' not in tabs
                and not NAME_X.search(iid) and iid not in MANUAL_BLOCK)
        if mech or iid in MANUAL_ADD:
            tabs.append('materials'); added.append(iid)
    io.open(cat_path, 'w', encoding='utf-8', newline='\n').write(json.dumps(d, indent=2))
    mat = sum(1 for v in items.values() if 'materials' in v)
    print(f'{cat_path}: +{len(added)} materials adds, materials total {mat}')
    print('added:', ', '.join(sorted(i.split(":")[1] for i in added)))

    # sort_family.json lives next to categories.json: bricks out, paper group in
    sf_path = os.path.join(os.path.dirname(cat_path), 'sort_family.json')
    if os.path.isfile(sf_path):
        sf = json.load(io.open(sf_path, encoding='utf-8'))
        mats = [x for x in sf.get('materials', []) if x not in MANUAL_REMOVE_MATERIALS]
        if 'minecraft:paper' in mats:
            i = mats.index('minecraft:paper')
            for off, item in enumerate(PAPER_GROUP, start=1):
                if item in mats: mats.remove(item)
                mats.insert(i + off, item)
        sf['materials'] = mats
        io.open(sf_path, 'w', encoding='utf-8', newline='\n').write(json.dumps(sf, indent=2))
        print(f'{sf_path}: paper group placed, bricks removed')


if __name__ == '__main__':
    main()
