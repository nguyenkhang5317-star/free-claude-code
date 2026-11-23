#!/usr/bin/env python3
"""Data pack generator for the Minecraft 1.16.5 rebuild.

Transcribes the binding tables of MASTER_PROMPT_FULL.md (Sections 4, 6, 7, 8-10,
13, Appendix CH/I2/A) into the JSON files under data/. Re-run after editing:

    python3 tools/gen_data.py

The engine loads these files at boot (Section 2.4); every value here is
tunable without recompiling (Section 1.6).
"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "data")

# ----------------------------------------------------------------------------
# Textures (Appendix F palettes; pixel content is original/procedural)
# ----------------------------------------------------------------------------
TEX = {}
def tex(name, pattern, *colors, **kw):
    d = {"pattern": pattern, "colors": list(colors)}
    d.update(kw)
    TEX[name] = d
    return name

STONE = ["#7F7F7F", "#8C8C8C", "#999999", "#A6A6A6"]
DIRT = ["#79553A", "#866043", "#96714B", "#A57D55"]
GRASS = ["#5B8731", "#6B9B3A", "#7CA94A", "#8FB65C"]
NETHERRACK = ["#6E3535", "#7A3D3D", "#874646", "#9A5050"]

tex("stone", "stone", *STONE)
tex("cobblestone", "cobble", "#6E6E6E", "#7F7F7F", "#909090", "#A3A3A3")
tex("mossy_cobblestone", "mossy", "#6E6E6E", "#7F7F7F", "#909090", "#A3A3A3", accent=["#5B7345", "#6B8747"])
tex("dirt", "noise", *DIRT)
tex("coarse_dirt", "pebble", "#5E4330", *DIRT)
tex("grass_block_top", "noise", *GRASS)
tex("grass_block_side", "grass_side", *DIRT, accent=GRASS)
tex("podzol_top", "noise", "#5A3D1A", "#6A4A22", "#7A572B", "#8A6434")
tex("mycelium_top", "noise", "#6F6265", "#7C6E74", "#8A7B82", "#98898F")
tex("mycelium_side", "grass_side", *DIRT, accent=["#6F6265", "#7C6E74", "#8A7B82"])
tex("snowy_grass_side", "grass_side", *DIRT, accent=["#E6F0F0", "#F0FAFA", "#FFFFFF"])
tex("sand", "noise", "#DBD3A0", "#E6D9A8", "#EFE0B4", "#F6EAC0")
tex("red_sand", "noise", "#A8541C", "#B65E22", "#C4692A", "#D27532")
tex("gravel", "pebble", "#837B7B", "#908A8A", "#9E9696", "#ABA2A2")
tex("bedrock", "bedrock", "#565656", "#616161", "#6E6E6E", "#7B7B7B")
tex("obsidian", "facets", "#120B1B", "#1A1026", "#241733", "#2E1E40")
tex("crying_obsidian", "facets", "#0E0A18", "#1B1230", "#3A1D66", "#5B3AA8")
tex("andesite", "stone", "#7A7A7C", "#858587", "#909092", "#9C9C9E")
tex("diorite", "stone", "#B5B5B8", "#C6C6C9", "#D6D6D8", "#E8E8EA")
tex("granite", "stone", "#8E5E4A", "#9B6A55", "#A87662", "#B6836F")
tex("polished_andesite", "flat", "#808083", "#8A8A8D", "#949497")
tex("polished_diorite", "flat", "#C0C0C3", "#CDCDD0", "#DADADC")
tex("polished_granite", "flat", "#94624E", "#A06D59", "#AC7965")
tex("smooth_stone", "flat", "#9A9A9A", "#A3A3A3", "#ACACAC")
tex("stone_bricks", "stone_bricks", "#6F6F6F", "#7B7B7B", "#878787", "#949494")
tex("mossy_stone_bricks", "stone_bricks", "#6F6F6F", "#7B7B7B", "#878787", "#949494", accent=["#5B7345", "#6B8747"])
tex("cracked_stone_bricks", "stone_bricks", "#666666", "#737373", "#808080", "#8C8C8C", cracked=True)
tex("bricks", "bricks", "#8E4A3A", "#9C5544", "#A9604E", "#B66B58", accent=["#8F8577", "#A39888"])
tex("clay", "noise", "#9EA3B0", "#A4A9B6", "#AAAFBC", "#B0B5C2")
tex("terracotta", "noise", "#94573D", "#985B41", "#9C5F45", "#A06349")
tex("snow", "noise", "#EEF7F7", "#F3FBFB", "#F8FFFF", "#FFFFFF")
tex("ice", "ice", "#7DAEF5", "#8CBAF7", "#9BC6F9", "#AAD2FB")
tex("packed_ice", "ice", "#7FA6E6", "#8EB2EA", "#9DBEEE", "#ACCAF2", opaque=True)
tex("netherrack", "netherrack", *NETHERRACK)
tex("soul_sand", "pebble", "#4A3B32", "#554438", "#61503F", "#6E5A47")
tex("soul_soil", "noise", "#3E342B", "#4A3E33", "#564A3C", "#635545")
tex("glowstone", "glow", "#8F6B36", "#C49A4E", "#F2C86E", "#FFE9A6")
tex("end_stone", "noise", "#DBDBA1", "#E3E3AC", "#EBEBB7", "#F2F2C2")
tex("nether_bricks", "bricks", "#2C1418", "#371A1F", "#422026", "#4D262D", accent=["#1B0C0F", "#220F12"])
tex("basalt_side", "log_side", "#4A4A50", "#55555B", "#606066", "#6B6B71")
tex("basalt_top", "stone", "#4A4A50", "#55555B", "#606066", "#6B6B71")
tex("blackstone", "stone", "#1E1A20", "#28232A", "#322C34", "#3C353E")
tex("ancient_debris_side", "log_side", "#5E4238", "#6E4E43", "#7E5A4E", "#8E6659")
tex("ancient_debris_top", "rings", "#5E4238", "#6E4E43", "#7E5A4E", "#8E6659")
tex("crimson_nylium", "grass_side", *NETHERRACK, accent=["#7C3247", "#8D3A52", "#9E425D"])
tex("crimson_nylium_top", "noise", "#7C3247", "#8D3A52", "#9E425D", "#AE4A68")
tex("warped_nylium", "grass_side", *NETHERRACK, accent=["#356B5C", "#3D7B6A", "#458B78"])
tex("warped_nylium_top", "noise", "#2E5B4E", "#356B5C", "#3D7B6A", "#458B78")
tex("shroomlight", "glow", "#D8863B", "#E89B4B", "#F5B25C", "#FFC96E")
tex("nether_wart_block", "noise", "#6E0E0E", "#7E1212", "#8E1717", "#9E1C1C")
tex("warped_wart_block", "noise", "#0E6E68", "#127E77", "#178E86", "#1C9E95")
tex("quartz_block", "flat", "#E8E2D8", "#EEE8DE", "#F4EEE6")
tex("purpur_block", "stone_bricks", "#9A6E9A", "#A779A7", "#B485B4", "#C191C1")
tex("prismarine", "stone", "#4F8F82", "#5D9F90", "#6BAF9E", "#79BFAC")
tex("sea_lantern", "glow", "#A6C7BC", "#C4DCD4", "#DDEDE7", "#F5FFFB")
tex("sponge", "pebble", "#B8A63A", "#C6B444", "#D4C24E", "#E2D058")

# Woods (F.2)
WOODS = {
    "oak":      (["#6B511F", "#7A5D26", "#8A6B2E", "#997835"], ["#9C7F4E", "#A88A55", "#B8945F", "#C4A06A"], ["#9A7A48", "#A8875A", "#B5946A", "#C2A177"], "#4F8A2C"),
    "spruce":   (["#3B2A0F", "#4A3515", "#59401C", "#684B24"], ["#6F5233", "#7A5B3A", "#856543", "#906E4B"], ["#5E4428", "#6A4E30", "#765838", "#826140"], "#3D6140"),
    "birch":    (["#D7CFC2", "#E3DCD1", "#F0EAE0", "#F8F4EC"], ["#C2AE77", "#CDB983", "#D6C38F", "#E0CD9B"], ["#BFAB75", "#C9B680", "#D3C08C", "#DDCA98"], "#5E8A3A"),
    "jungle":   (["#544525", "#645331", "#76613D", "#876F49"], ["#A7774E", "#B38158", "#BF8B62", "#CB956C"], ["#9E6E48", "#AA7852", "#B6825C", "#C28C66"], "#3E8A1C"),
    "acacia":   (["#676157", "#7A7267", "#8D8479", "#A0968B"], ["#B05E33", "#BA6337", "#C46D41", "#CE774B"], ["#A55730", "#AF6038", "#B96A42", "#C3744C"], "#5E8A2C"),
    "dark_oak": (["#3D2F16", "#4A381C", "#584223", "#654C2A"], ["#4E3720", "#583F25", "#62472A", "#6C4F30"], ["#432F1A", "#4D371F", "#573F24", "#614729"], "#3A6A1E"),
    "crimson":  (["#6B2A3C", "#7C3247", "#8D3A52", "#9E425D"], ["#7E3A56", "#8A4260", "#964A6A", "#A25274"], ["#6E3149", "#7A3953", "#86415D", "#924967"], None),
    "warped":   (["#2E5B4E", "#356B5C", "#3D7B6A", "#458B78"], ["#2C6E68", "#337A73", "#3A867E", "#419289"], ["#28625C", "#2F6E67", "#367A72", "#3D867D"], None),
}
for w, (bark, inner, planks, leaf) in WOODS.items():
    tex(w + "_log", "birch_side" if w == "birch" else "log_side", *bark)
    tex(w + "_log_top", "rings", *inner, accent=bark)
    tex(w + "_planks", "planks", *planks)
    if leaf:
        tex(w + "_leaves", "leaves", "#2E4D1A", leaf, "#5E9A3A", "#78B24E")
        tex(w + "_sapling", "sapling", "#5B3D1E", leaf)

tex("crafting_table_top", "table_top", "#7A5D26", "#9A7A48", "#B5946A", "#C2A177")
tex("crafting_table_side", "table_side", "#7A5D26", "#9A7A48", "#B5946A", "#C2A177")
tex("crafting_table_front", "table_front", "#7A5D26", "#9A7A48", "#B5946A", "#C2A177")
tex("furnace_side", "furnace_side", "#5E5E5E", "#6E6E6E", "#7E7E7E", "#8E8E8E")
tex("furnace_top", "stone", "#6E6E6E", "#7A7A7A", "#868686", "#929292")
tex("furnace_front", "furnace_front", "#5E5E5E", "#6E6E6E", "#7E7E7E", "#8E8E8E")
tex("furnace_front_on", "furnace_front", "#5E5E5E", "#6E6E6E", "#7E7E7E", "#8E8E8E", lit=True)
tex("chest_side", "chest", "#7A5326", "#8E6230", "#A2713A", "#B68044")
tex("chest_front", "chest", "#7A5326", "#8E6230", "#A2713A", "#B68044", latch=True)
tex("chest_top", "planks", "#8E6230", "#A2713A", "#B68044", "#C28C4E")
tex("bookshelf", "bookshelf", "#9A7A48", "#A8875A", "#B5946A", "#C2A177")
tex("tnt_side", "tnt_side", "#B8231A", "#D63A2E", "#E8E8E8", "#2A2A2A")
tex("tnt_top", "tnt_top", "#B8231A", "#D63A2E", "#E8E8E8", "#2A2A2A")
tex("pumpkin_side", "melon", "#C0661A", "#D07422", "#E0822A", "#F09032")
tex("pumpkin_top", "rings", "#C0661A", "#D07422", "#E0822A", "#F09032", accent=["#4E7A1E", "#5E8A2E"])
tex("carved_pumpkin", "pumpkin_face", "#C0661A", "#D07422", "#E0822A", "#F09032")
tex("jack_o_lantern", "pumpkin_face", "#C0661A", "#D07422", "#E0822A", "#F09032", lit=True)
tex("melon_side", "melon", "#4E8A1E", "#5E9A2A", "#6EAA36", "#A6C83A")
tex("melon_top", "rings", "#4E8A1E", "#5E9A2A", "#6EAA36", "#A6C83A", accent=["#4E8A1E"])
tex("sandstone", "sandstone", "#C9BB84", "#D6C893", "#DFD3A0", "#E8DCAB")
tex("sandstone_top", "noise", "#D6C893", "#DFD3A0", "#E8DCAB", "#F0E4B6")
tex("red_sandstone", "sandstone", "#A2501A", "#B05A20", "#BE6428", "#CC6E30")
tex("cactus_side", "cactus_side", "#0F6B1F", "#146B26", "#1A7D2E", "#208F36")
tex("cactus_top", "cactus_top", "#0F6B1F", "#146B26", "#1A7D2E", "#208F36")
tex("farmland", "farmland", "#4E3524", "#5A3E2A", "#664730", "#725036")
tex("farmland_moist", "farmland", "#2E1E14", "#38261A", "#422E20", "#4C3626")
tex("ladder", "ladder", "#7A5D26", "#9A7A48", "#B5946A")
tex("torch", "torch", "#6B511F", "#997835", "#FFD24A", "#FFF7C0")
tex("soul_torch", "torch", "#6B511F", "#997835", "#4AE0FF", "#C0FAFF")
tex("water", "water", "#2A4FC4", "#3358D0", "#3C61DC", "#456AE8")
tex("lava", "lava", "#C2400A", "#E0600F", "#F58A1E", "#FFC23A")
tex("nether_portal", "portal", "#3A0A7A", "#5A1AAA", "#7A2ADA", "#B05AFF")
tex("glass", "glass", "#C8E4EA", "#E8F6FA", "#FFFFFF")
tex("bed_top", "bed_top", "#8E1E1E", "#A82626", "#C22E2E", "#EEEEEE")
tex("bed_side", "bed_side", "#8E1E1E", "#A82626", "#9A7A48", "#C2A177")
tex("spawner", "glass", "#1E2A36", "#2A3A4A", "#3A4E62", opaque=False)
tex("cobweb", "web", "#DDDDDD", "#F5F5F5")
tex("dead_bush", "dead_bush", "#6B4A20", "#8A6430")
tex("grass", "tuft", "#5B8731", "#6B9B3A", "#7CA94A", "#8FB65C")
tex("fern", "fern", "#4A7A2A", "#5A8A36", "#6A9A42")
tex("sugar_cane", "cane", "#6AA84A", "#82C05A", "#9AD86A")
tex("red_mushroom", "mushroom", "#C81E1E", "#EEEEEE", "#D8C8A8")
tex("brown_mushroom", "mushroom", "#8A6440", "#A57A50", "#D8C8A8")
FLOWERS = {
    "dandelion": "#FFE21E", "poppy": "#E01E1E", "blue_orchid": "#2AA8E8", "allium": "#B85AE8",
    "azure_bluet": "#E8E8F0", "red_tulip": "#E83A2A", "orange_tulip": "#F08A1E", "white_tulip": "#F0F0F0",
    "pink_tulip": "#F0A0C8", "oxeye_daisy": "#F5F5E0", "cornflower": "#4A6AE8", "lily_of_the_valley": "#FAFAFA",
}
for f, c in FLOWERS.items():
    tex(f, "flower", "#3E6B2A", c)
for age in range(8):
    tex("wheat_stage%d" % age, "crop", "#3E8A1C" if age < 7 else "#C8A83A", "#6AAA2A" if age < 7 else "#DCC04A", age=age)

COLORS16 = {
    "white": "#E9ECEC", "orange": "#F07613", "magenta": "#BD44B3", "light_blue": "#3AAFD9", "yellow": "#F8C627",
    "lime": "#70B919", "pink": "#ED8DAC", "gray": "#3E4447", "light_gray": "#8E8E86", "cyan": "#158991",
    "purple": "#792AAC", "blue": "#35399D", "brown": "#724728", "green": "#546D1B", "red": "#A12722", "black": "#141519",
}
def shade(hexc, f):
    r, g, b = int(hexc[1:3], 16), int(hexc[3:5], 16), int(hexc[5:7], 16)
    cl = lambda v: max(0, min(255, int(v * f)))
    return "#%02X%02X%02X" % (cl(r), cl(g), cl(b))
for col, c in COLORS16.items():
    tex(col + "_wool", "wool", shade(c, 0.82), shade(c, 0.92), c, shade(c, 1.08))
    tex(col + "_concrete", "flat", shade(c, 0.94), c, shade(c, 1.03))
    tex(col + "_terracotta", "noise", shade(c, 0.55), shade(c, 0.58), shade(c, 0.61), shade(c, 0.64))
    tex(col + "_stained_glass", "glass", shade(c, 0.9), c, shade(c, 1.1), tint=True)

ORES = {"coal": "#2A2A2A", "iron": "#D8AF93", "gold": "#FCEE4B", "redstone": "#FF1E1E", "lapis": "#2A4FC4",
        "diamond": "#5DECE7", "emerald": "#17DD62"}
for o, c in ORES.items():
    tex(o + "_ore", "ore", *STONE, accent=[shade(c, 0.7), c, shade(c, 1.2)])
tex("nether_gold_ore", "ore", *NETHERRACK, accent=["#C8A81E", "#FCEE4B", "#FFF79A"])
tex("nether_quartz_ore", "ore", *NETHERRACK, accent=["#D8D0C8", "#EEE8E0", "#FFFFFF"])
METALS = {"iron_block": "#DCDCDC", "gold_block": "#F8D93A", "diamond_block": "#62EDE4", "emerald_block": "#2AD06A",
          "lapis_block": "#2A4FAE", "redstone_block": "#B4180E", "coal_block": "#1A1A1A", "netherite_block": "#433D3F"}
for m, c in METALS.items():
    tex(m, "metal", shade(c, 0.7), shade(c, 0.85), c, shade(c, 1.12))

# ----------------------------------------------------------------------------
# Blocks (Section 6). Hardness/tool/tier/blast/light per spec.
# tier: 0 any, 1 wood, 2 stone, 3 iron, 4 diamond (minimum pickaxe tier for drops)
# ----------------------------------------------------------------------------
BLOCKS = []
def block(bid, name, hardness, tool="none", tier=0, blast=None, light=0, opacity=15, layer="solid", shape="cube",
          sound="stone", tex_all=None, top=None, side=None, bottom=None, front=None, drops="self", requires_tool=None,
          flammable=None, gravity=False, slip=0.6, collide=True, item=True, props=None, extra=None):
    if blast is None:
        blast = hardness
    if requires_tool is None:
        requires_tool = tier > 0
    t = tex_all or bid
    d = {
        "id": "minecraft:" + bid, "name": name, "hardness": hardness, "blast_resistance": blast,
        "tool": tool, "min_tier": tier, "requires_tool": requires_tool, "light": light, "opacity": opacity,
        "layer": layer, "shape": shape, "sound": sound,
        "textures": {"top": top or t, "side": side or t, "bottom": bottom or top or t, "front": front or side or t},
        "drops": drops, "flammable": flammable or [0, 0], "gravity": gravity, "slipperiness": slip,
        "collision": collide, "item": item,
    }
    if props:
        d["properties"] = props
    if extra:
        d.update(extra)
    BLOCKS.append(d)

block("air", "Air", 0, opacity=0, layer="none", shape="none", sound="stone", tex_all="stone", drops="none", collide=False, item=False)
block("stone", "Stone", 1.5, "pickaxe", 1, blast=6, drops=[{"item": "minecraft:cobblestone"}])
block("granite", "Granite", 1.5, "pickaxe", 1, blast=6)
block("polished_granite", "Polished Granite", 1.5, "pickaxe", 1, blast=6)
block("diorite", "Diorite", 1.5, "pickaxe", 1, blast=6)
block("polished_diorite", "Polished Diorite", 1.5, "pickaxe", 1, blast=6)
block("andesite", "Andesite", 1.5, "pickaxe", 1, blast=6)
block("polished_andesite", "Polished Andesite", 1.5, "pickaxe", 1, blast=6)
block("grass_block", "Grass Block", 0.6, "shovel", sound="grass", top="grass_block_top", side="grass_block_side", bottom="dirt",
      drops=[{"item": "minecraft:dirt"}], extra={"tint": "grass_top", "random_tick": "grass_spread"})
block("dirt", "Dirt", 0.5, "shovel", sound="gravel")
block("coarse_dirt", "Coarse Dirt", 0.5, "shovel", sound="gravel")
block("podzol", "Podzol", 0.5, "shovel", sound="gravel", top="podzol_top", side="grass_block_side", bottom="dirt", drops=[{"item": "minecraft:dirt"}])
block("mycelium", "Mycelium", 0.6, "shovel", sound="grass", top="mycelium_top", side="mycelium_side", bottom="dirt", drops=[{"item": "minecraft:dirt"}])
block("cobblestone", "Cobblestone", 2.0, "pickaxe", 1, blast=6)
block("mossy_cobblestone", "Mossy Cobblestone", 2.0, "pickaxe", 1, blast=6)
for w in ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"]:
    W = w.replace("_", " ").title()
    block(w + "_planks", W + " Planks", 2.0, "axe", blast=3, sound="wood", flammable=[5, 20])
    block(w + "_log", W + " Log", 2.0, "axe", sound="wood", top=w + "_log_top", side=w + "_log", flammable=[5, 5], props={"axis": ["y", "x", "z"]})
    block(w + "_leaves", W + " Leaves", 0.2, "hoe", opacity=1, layer="cutout", sound="grass", flammable=[30, 60],
          drops=[{"item": "minecraft:" + w + "_sapling", "chance": 0.05}, {"item": "minecraft:stick", "chance": 0.02, "min": 1, "max": 2}] +
                ([{"item": "minecraft:apple", "chance": 0.005}] if w in ("oak", "dark_oak") else []),
          extra={"tint": "foliage", "random_tick": "leaf_decay"})
    block(w + "_sapling", W + " Sapling", 0, opacity=0, layer="cutout", shape="cross", sound="grass", collide=False,
          extra={"random_tick": "sapling_grow", "support": "soil"})
for w in ["crimson", "warped"]:
    W = w.title()
    block(w + "_stem", W + " Stem", 2.0, "axe", sound="nether_wood", top=w + "_log_top", side=w + "_log", props={"axis": ["y", "x", "z"]})
    block(w + "_planks", W + " Planks", 2.0, "axe", blast=3, sound="nether_wood")
block("sand", "Sand", 0.5, "shovel", sound="sand", gravity=True)
block("red_sand", "Red Sand", 0.5, "shovel", sound="sand", gravity=True)
block("gravel", "Gravel", 0.6, "shovel", sound="gravel", gravity=True,
      drops=[{"item": "minecraft:flint", "chance": 0.1, "else": "minecraft:gravel"}])
block("bedrock", "Bedrock", -1, blast=3600000, drops="none")
for o, (tier, drop, mn, mx) in {"coal": (1, "coal", 1, 1), "iron": (2, None, 1, 1), "gold": (3, None, 1, 1),
                                "redstone": (3, "redstone", 4, 5), "lapis": (2, "lapis_lazuli", 4, 9),
                                "diamond": (3, "diamond", 1, 1), "emerald": (3, "emerald", 1, 1)}.items():
    block(o + "_ore", o.title() + " Ore", 3.0, "pickaxe", tier, blast=3, light=9 if o == "redstone" and False else 0,
          drops=[{"item": "minecraft:" + drop, "min": mn, "max": mx}] if drop else "self",
          extra={"xp": [0, 2] if o == "coal" else ([3, 7] if o in ("diamond", "emerald") else ([2, 5] if o == "lapis" else ([1, 5] if o == "redstone" else [0, 0])))})
block("nether_gold_ore", "Nether Gold Ore", 3.0, "pickaxe", 1, blast=3, sound="netherrack", drops=[{"item": "minecraft:gold_nugget", "min": 2, "max": 6}], extra={"xp": [0, 1]})
block("nether_quartz_ore", "Nether Quartz Ore", 3.0, "pickaxe", 1, blast=3, sound="netherrack", drops=[{"item": "minecraft:quartz"}], extra={"xp": [2, 5]})
block("ancient_debris", "Ancient Debris", 30.0, "pickaxe", 4, blast=1200, sound="ancient_debris", top="ancient_debris_top", side="ancient_debris_side")
for m, h in {"coal_block": 5, "iron_block": 5, "gold_block": 3, "diamond_block": 5, "emerald_block": 5, "lapis_block": 3, "redstone_block": 5}.items():
    tier = 3 if m in ("gold_block", "diamond_block", "emerald_block") else (2 if m in ("iron_block", "lapis_block") else 1)
    block(m, "Block of " + m.replace("_block", "").title(), h, "pickaxe", tier, blast=6, sound="metal" if m not in ("coal_block", "lapis_block") else "stone")
block("netherite_block", "Block of Netherite", 50, "pickaxe", 4, blast=1200, sound="netherite_block")
block("obsidian", "Obsidian", 50, "pickaxe", 4, blast=1200)
block("crying_obsidian", "Crying Obsidian", 50, "pickaxe", 4, blast=1200, light=10)
block("glass", "Glass", 0.3, opacity=0, layer="cutout", sound="glass", drops="none")
for col in COLORS16:
    C = col.replace("_", " ").title()
    block(col + "_wool", C + " Wool", 0.8, "shears", sound="wool", flammable=[30, 60])
for col in COLORS16:
    C = col.replace("_", " ").title()
    block(col + "_concrete", C + " Concrete", 1.8, "pickaxe", 1)
    block(col + "_terracotta", C + " Terracotta", 1.25, "pickaxe", 1, blast=4.2)
    block(col + "_stained_glass", C + " Stained Glass", 0.3, opacity=0, layer="translucent", sound="glass", drops="none")
block("terracotta", "Terracotta", 1.25, "pickaxe", 1, blast=4.2)
block("bricks", "Bricks", 2.0, "pickaxe", 1, blast=6)
block("stone_bricks", "Stone Bricks", 1.5, "pickaxe", 1, blast=6)
block("mossy_stone_bricks", "Mossy Stone Bricks", 1.5, "pickaxe", 1, blast=6)
block("cracked_stone_bricks", "Cracked Stone Bricks", 2.0, "pickaxe", 1, blast=6)
block("smooth_stone", "Smooth Stone", 2.0, "pickaxe", 1, blast=6)
block("stone_slab", "Stone Slab", 2.0, "pickaxe", 1, blast=6, opacity=0, shape="slab", tex_all="smooth_stone")
block("cobblestone_slab", "Cobblestone Slab", 2.0, "pickaxe", 1, blast=6, opacity=0, shape="slab", tex_all="cobblestone")
block("oak_slab", "Oak Slab", 2.0, "axe", blast=3, opacity=0, shape="slab", sound="wood", tex_all="oak_planks")
block("sandstone", "Sandstone", 0.8, "pickaxe", 1, top="sandstone_top", side="sandstone")
block("red_sandstone", "Red Sandstone", 0.8, "pickaxe", 1, top="red_sand", side="red_sandstone")
block("clay", "Clay", 0.6, "shovel", sound="gravel", drops=[{"item": "minecraft:clay_ball", "min": 4, "max": 4}])
block("snow_block", "Snow Block", 0.2, "shovel", 1, sound="snow", tex_all="snow", drops=[{"item": "minecraft:snowball", "min": 4, "max": 4}], requires_tool=True)
block("snow", "Snow", 0.1, "shovel", 1, opacity=0, shape="snow_layer", sound="snow", collide=False, drops=[{"item": "minecraft:snowball"}], requires_tool=True)
block("ice", "Ice", 0.5, "pickaxe", opacity=1, layer="translucent", sound="glass", slip=0.98, drops="none")
block("packed_ice", "Packed Ice", 0.5, "pickaxe", sound="glass", slip=0.98, drops="none")
block("netherrack", "Netherrack", 0.4, "pickaxe", 1, sound="netherrack")
block("soul_sand", "Soul Sand", 0.5, "shovel", sound="soul_sand", extra={"speed_factor": 0.4})
block("soul_soil", "Soul Soil", 0.5, "shovel", sound="soul_soil")
block("glowstone", "Glowstone", 0.3, light=15, sound="glass", drops=[{"item": "minecraft:glowstone_dust", "min": 2, "max": 4}])
block("crimson_nylium", "Crimson Nylium", 0.4, "pickaxe", 1, sound="nylium", top="crimson_nylium_top", side="crimson_nylium", bottom="netherrack", drops=[{"item": "minecraft:netherrack"}])
block("warped_nylium", "Warped Nylium", 0.4, "pickaxe", 1, sound="nylium", top="warped_nylium_top", side="warped_nylium", bottom="netherrack", drops=[{"item": "minecraft:netherrack"}])
block("nether_wart_block", "Nether Wart Block", 1.0, "hoe", sound="nether_wart")
block("warped_wart_block", "Warped Wart Block", 1.0, "hoe", sound="nether_wart")
block("shroomlight", "Shroomlight", 1.0, "hoe", light=15, sound="shroomlight")
block("basalt", "Basalt", 1.25, "pickaxe", 1, blast=4.2, sound="basalt", top="basalt_top", side="basalt_side", props={"axis": ["y", "x", "z"]})
block("blackstone", "Blackstone", 1.5, "pickaxe", 1, blast=6, sound="blackstone")
block("nether_bricks", "Nether Bricks", 2.0, "pickaxe", 1, blast=6, sound="nether_bricks")
block("quartz_block", "Block of Quartz", 0.8, "pickaxe", 1)
block("end_stone", "End Stone", 3.0, "pickaxe", 1, blast=9)
block("purpur_block", "Purpur Block", 1.5, "pickaxe", 1, blast=6)
block("prismarine", "Prismarine", 1.5, "pickaxe", 1, blast=6)
block("sea_lantern", "Sea Lantern", 0.3, light=15, sound="glass", drops=[{"item": "minecraft:prismarine_crystals", "min": 2, "max": 3}])
block("sponge", "Sponge", 0.6, "hoe", sound="grass")
block("crafting_table", "Crafting Table", 2.5, "axe", sound="wood", top="crafting_table_top", side="crafting_table_side", front="crafting_table_front", flammable=[0, 0], extra={"interact": "crafting"})
block("furnace", "Furnace", 3.5, "pickaxe", 1, top="furnace_top", side="furnace_side", front="furnace_front", props={"facing": ["north", "south", "west", "east"], "lit": ["false", "true"]}, extra={"interact": "furnace", "lit_light": 13, "lit_front": "furnace_front_on"})
block("chest", "Chest", 2.5, "axe", sound="wood", top="chest_top", side="chest_side", front="chest_front", opacity=0, props={"facing": ["north", "south", "west", "east"]}, extra={"interact": "chest"})
block("bookshelf", "Bookshelf", 1.5, "axe", sound="wood", top="oak_planks", side="bookshelf", drops=[{"item": "minecraft:book", "min": 3, "max": 3}], flammable=[30, 20])
block("tnt", "TNT", 0, sound="grass", top="tnt_top", side="tnt_side", flammable=[15, 100], extra={"interact": "tnt"})
block("pumpkin", "Pumpkin", 1.0, "axe", sound="wood", top="pumpkin_top", side="pumpkin_side")
block("carved_pumpkin", "Carved Pumpkin", 1.0, "axe", sound="wood", top="pumpkin_top", side="pumpkin_side", front="carved_pumpkin", props={"facing": ["north", "south", "west", "east"]})
block("jack_o_lantern", "Jack o'Lantern", 1.0, "axe", light=15, sound="wood", top="pumpkin_top", side="pumpkin_side", front="jack_o_lantern", props={"facing": ["north", "south", "west", "east"]})
block("melon", "Melon", 1.0, "axe", sound="wood", top="melon_top", side="melon_side", drops=[{"item": "minecraft:melon_slice", "min": 3, "max": 7}])
block("cactus", "Cactus", 0.4, opacity=0, layer="cutout", shape="cactus", sound="wool", top="cactus_top", side="cactus_side", extra={"contact_damage": 1, "random_tick": "cactus_grow", "support": "sand"})
block("farmland", "Farmland", 0.6, "shovel", opacity=0, shape="farmland", sound="gravel", top="farmland", side="dirt", bottom="dirt", drops=[{"item": "minecraft:dirt"}], props={"moisture": [str(i) for i in range(8)]}, extra={"random_tick": "farmland"})
block("wheat", "Wheat Crops", 0, opacity=0, layer="cutout", shape="crop", sound="grass", tex_all="wheat_stage0", collide=False, item=False,
      drops=[{"item": "minecraft:wheat_seeds", "min": 1, "max": 1}], props={"age": [str(i) for i in range(8)]}, extra={"random_tick": "crop_grow", "support": "farmland", "ripe_drops": [{"item": "minecraft:wheat"}, {"item": "minecraft:wheat_seeds", "min": 1, "max": 3}]})
block("torch", "Torch", 0, opacity=0, layer="cutout", shape="torch", sound="wood", light=14, collide=False, extra={"support": "solid_below"})
block("soul_torch", "Soul Torch", 0, opacity=0, layer="cutout", shape="torch", sound="wood", light=10, collide=False, extra={"support": "solid_below"})
block("ladder", "Ladder", 0.4, "axe", opacity=0, layer="cutout", shape="ladder", sound="ladder", props={"facing": ["north", "south", "west", "east"]}, extra={"climbable": True})
block("cobweb", "Cobweb", 4.0, "sword", opacity=1, layer="cutout", shape="cross", sound="stone", collide=False, drops=[{"item": "minecraft:string"}], requires_tool=True, extra={"web": True})
block("grass", "Grass", 0, opacity=0, layer="cutout_mipmap", shape="cross", sound="grass", collide=False, drops=[{"item": "minecraft:wheat_seeds", "chance": 0.125}], extra={"tint": "grass", "support": "soil", "replaceable": True})
block("fern", "Fern", 0, opacity=0, layer="cutout_mipmap", shape="cross", sound="grass", collide=False, drops=[{"item": "minecraft:wheat_seeds", "chance": 0.125}], extra={"tint": "grass", "support": "soil", "replaceable": True})
block("dead_bush", "Dead Bush", 0, opacity=0, layer="cutout", shape="cross", sound="grass", collide=False, drops=[{"item": "minecraft:stick", "min": 0, "max": 2}], extra={"support": "sand", "replaceable": True})
for f in FLOWERS:
    block(f, f.replace("_", " ").title(), 0, opacity=0, layer="cutout", shape="cross", sound="grass", collide=False, extra={"support": "soil"})
block("red_mushroom", "Red Mushroom", 0, opacity=0, layer="cutout", shape="cross", sound="grass", collide=False, light=0)
block("brown_mushroom", "Brown Mushroom", 0, opacity=0, layer="cutout", shape="cross", sound="grass", collide=False, light=1)
block("sugar_cane", "Sugar Cane", 0, opacity=0, layer="cutout", shape="cross", sound="grass", collide=False, extra={"random_tick": "cane_grow", "support": "cane"})
block("water", "Water", 100, opacity=1, layer="translucent", shape="fluid", sound="stone", drops="none", collide=False, item=False,
      props={"level": [str(i) for i in range(16)]}, extra={"fluid": "water", "tick_rate": 5, "spread": 7, "replaceable": True})
block("lava", "Lava", 100, opacity=0, light=15, layer="translucent", shape="fluid", sound="stone", drops="none", collide=False, item=False,
      props={"level": [str(i) for i in range(16)]}, extra={"fluid": "lava", "tick_rate": 30, "spread": 3, "nether_spread": 7, "replaceable": True})
block("fire", "Fire", 0, opacity=0, light=15, layer="cutout", shape="cross", sound="wool", tex_all="lava", drops="none", collide=False, item=False, extra={"contact_damage": 1, "replaceable": True})
block("nether_portal", "Nether Portal", -1, opacity=0, light=11, layer="translucent", shape="portal", sound="glass", drops="none", collide=False, item=False, props={"axis": ["x", "z"]})
block("red_bed", "Red Bed", 0.2, opacity=0, shape="bed", sound="wood", top="bed_top", side="bed_side", bottom="oak_planks",
      props={"facing": ["north", "south", "west", "east"], "part": ["foot", "head"]}, extra={"interact": "bed"})
block("spawner", "Spawner", 5.0, "pickaxe", 1, opacity=0, layer="cutout", drops="none", extra={"xp": [15, 43]})

# ----------------------------------------------------------------------------
# Items (Section 7.1). Block items are created automatically by the loader for
# every block with "item": true; this list holds non-block items.
# ----------------------------------------------------------------------------
ITEMS = []
def item(iid, name, stack=64, icon=None, **kw):
    d = {"id": "minecraft:" + iid, "name": name, "max_stack": stack, "icon": icon or {"shape": "generic", "colors": ["#888888"]}}
    d.update(kw)
    ITEMS.append(d)

TIERS = {  # Section 7.1 tool tier table
    "wooden":    {"level": 1, "durability": 59,   "speed": 2,  "bonus": 0, "ench": 15, "color": "#9A7A48", "tier": 1},
    "stone":     {"level": 2, "durability": 131,  "speed": 4,  "bonus": 1, "ench": 5,  "color": "#8C8C8C", "tier": 2},
    "iron":      {"level": 3, "durability": 250,  "speed": 6,  "bonus": 2, "ench": 14, "color": "#E6E6E6", "tier": 3},
    "diamond":   {"level": 4, "durability": 1561, "speed": 8,  "bonus": 3, "ench": 10, "color": "#5DECE7", "tier": 4},
    "golden":    {"level": 1, "durability": 32,   "speed": 12, "bonus": 0, "ench": 22, "color": "#FCEE4B", "tier": 1},
    "netherite": {"level": 4, "durability": 2031, "speed": 9,  "bonus": 4, "ench": 15, "color": "#4A4446", "tier": 4},
}
DMG = {  # weapon damage table (Section 7.1) — authoritative five rows
    "sword":   {"wooden": 4, "stone": 5, "iron": 6, "diamond": 7, "golden": 4, "netherite": 8},
    "axe":     {"wooden": 7, "stone": 9, "iron": 9, "diamond": 9, "golden": 7, "netherite": 10},
    "pickaxe": {"wooden": 2, "stone": 3, "iron": 3, "diamond": 4, "golden": 2, "netherite": 5},
    "shovel":  {"wooden": 2, "stone": 3, "iron": 3, "diamond": 4, "golden": 2, "netherite": 5},
    "hoe":     {t: 1 for t in TIERS},
}
SPD = {"sword": {t: 1.6 for t in TIERS},
       "axe": {"wooden": 0.8, "stone": 0.8, "golden": 0.8, "iron": 0.9, "diamond": 1.0, "netherite": 1.0},
       "pickaxe": {t: 1.2 for t in TIERS}, "shovel": {t: 1.0 for t in TIERS},
       "hoe": {"wooden": 1.0, "stone": 2.0, "iron": 3.0, "golden": 1.0, "diamond": 4.0, "netherite": 4.0}}
for t, td in TIERS.items():
    for kind in ["pickaxe", "axe", "shovel", "sword", "hoe"]:
        item("%s_%s" % (t, kind), "%s %s" % ("Wooden" if t == "wooden" else t.title(), kind.title()), 1,
             {"shape": kind, "colors": [td["color"]]},
             tool={"kind": kind, "tier": td["tier"], "speed": td["speed"], "material": t},
             durability=td["durability"], attack_damage=DMG[kind][t], attack_speed=SPD[kind][t])
ARMOR = {  # Section 7.1 armor points & durability by slot
    "leather": {"points": [1, 3, 2, 1], "dur": [55, 80, 75, 65], "tough": 0, "color": "#9A5A32"},
    "iron": {"points": [2, 6, 5, 2], "dur": [165, 240, 225, 195], "tough": 0, "color": "#E6E6E6"},
    "golden": {"points": [2, 5, 3, 1], "dur": [77, 112, 105, 91], "tough": 0, "color": "#FCEE4B"},
    "diamond": {"points": [3, 8, 6, 3], "dur": [363, 528, 495, 429], "tough": 2, "color": "#5DECE7"},
    "netherite": {"points": [3, 8, 6, 3], "dur": [407, 592, 555, 481], "tough": 3, "color": "#4A4446"},
}
SLOTS = ["helmet", "chestplate", "leggings", "boots"]
for m, ad in ARMOR.items():
    for i, s in enumerate(SLOTS):
        item("%s_%s" % (m, s), "%s %s" % (m.title(), s.title()), 1, {"shape": s, "colors": [ad["color"]]},
             armor={"slot": i, "points": ad["points"][i], "toughness": ad["tough"]}, durability=ad["dur"][i])

FOOD = {  # Section 7.4: hunger / saturation
    "apple": (4, 2.4, "apple", "#D81E1E"), "golden_apple": (4, 9.6, "apple", "#F8D93A"), "bread": (5, 6.0, "bread", "#B8862E"),
    "cookie": (2, 0.4, "ball", "#B8762E"), "melon_slice": (2, 1.2, "slice", "#E83A3A"), "sweet_berries": (2, 0.4, "ball", "#B81E3A"),
    "beef": (3, 1.8, "meat", "#C83A3A"), "cooked_beef": (8, 12.8, "meat", "#7A4A2A"),
    "porkchop": (3, 1.8, "meat", "#F0A0A0"), "cooked_porkchop": (8, 12.8, "meat", "#C8885A"),
    "chicken": (2, 1.2, "drumstick", "#F0C8B0"), "cooked_chicken": (6, 7.2, "drumstick", "#C8884A"),
    "mutton": (2, 1.2, "meat", "#D04A4A"), "cooked_mutton": (6, 9.6, "meat", "#8A5A3A"),
    "cod": (2, 0.4, "fish", "#B8A888"), "cooked_cod": (5, 6.0, "fish", "#D8B888"),
    "carrot": (3, 3.6, "carrot", "#F08A1E"), "potato": (1, 0.6, "potato", "#D8B868"), "baked_potato": (5, 6.0, "potato", "#C8983A"),
    "rotten_flesh": (4, 0.8, "meat", "#6A8A3A"), "spider_eye": (2, 3.2, "ball", "#8A1E3A"),
    "pumpkin_pie": (8, 4.8, "bread", "#D8882A"), "mushroom_stew": (6, 7.2, "bowl", "#B88A5A"),
}
for f, (h, s, shp, c) in FOOD.items():
    eff = None
    if f == "rotten_flesh": eff = [{"effect": "hunger", "seconds": 30, "chance": 0.8}]
    if f == "chicken": eff = [{"effect": "hunger", "seconds": 30, "chance": 0.3}]
    if f == "spider_eye": eff = [{"effect": "poison", "seconds": 5, "chance": 1.0}]
    if f == "golden_apple": eff = [{"effect": "regeneration", "seconds": 5, "chance": 1.0, "amp": 1}, {"effect": "absorption", "seconds": 120, "chance": 1.0}]
    d = {"hunger": h, "saturation": s, "eat_ticks": 32}
    if eff: d["effects"] = eff
    item(f, f.replace("_", " ").title(), 1 if f == "mushroom_stew" else 64, {"shape": shp, "colors": [c]}, food=d)

SIMPLE = {
    "stick": ("stick", "#8A6A3A"), "coal": ("lump", "#2A2A2A"), "charcoal": ("lump", "#3A2E22"),
    "iron_ingot": ("ingot", "#E6E6E6"), "gold_ingot": ("ingot", "#FCEE4B"), "netherite_ingot": ("ingot", "#4A4446"),
    "iron_nugget": ("nugget", "#E6E6E6"), "gold_nugget": ("nugget", "#FCEE4B"), "netherite_scrap": ("lump", "#5E4238"),
    "diamond": ("gem", "#5DECE7"), "emerald": ("gem", "#17DD62"), "lapis_lazuli": ("gem", "#2A4FC4"),
    "quartz": ("gem", "#EEE8E0"), "prismarine_crystals": ("gem", "#A6D7C8"),
    "redstone": ("dust", "#E01E1E"), "glowstone_dust": ("dust", "#F2C86E"), "gunpowder": ("dust", "#5A5A5A"),
    "sugar": ("dust", "#F5F5F5"), "bone_meal": ("dust", "#E8E8E0"),
    "flint": ("lump", "#3A3A3A"), "string": ("string", "#EEEEEE"), "feather": ("feather", "#F5F5F5"),
    "bone": ("bone", "#E8E8D8"), "leather": ("lump", "#9A5A32"), "wheat": ("wheat", "#DCC04A"),
    "wheat_seeds": ("seeds", "#5E9A2A"), "clay_ball": ("ball", "#A4A9B6"), "brick": ("ingot", "#A9604E"),
    "nether_brick": ("ingot", "#4D262D"), "paper": ("paper", "#F0F0E8"), "book": ("book", "#9A5A32"),
    "snowball": ("ball", "#F8FFFF"), "egg": ("egg", "#E8D8B8"), "slime_ball": ("ball", "#7AC85A"),
    "ender_pearl": ("ball", "#1E6A5A"), "blaze_rod": ("stick", "#F8C83A"), "bowl": ("bowl", "#8A6A3A"),
    "green_dye": ("dust", "#546D1B"), "red_dye": ("dust", "#A12722"), "white_dye": ("dust", "#E9ECEC"),
    "arrow": ("arrow", "#CCCCCC"),
}
for s, (shp, c) in SIMPLE.items():
    stack = 16 if s in ("snowball", "egg", "ender_pearl") else 64
    item(s, s.replace("_", " ").title(), stack, {"shape": shp, "colors": [c]})
# block-placing items whose id differs from the block
for k, v in {"wheat_seeds": "minecraft:wheat"}.items():
    for it in ITEMS:
        if it["id"] == "minecraft:" + k:
            it["places"] = v
item("bow", "Bow", 1, {"shape": "bow", "colors": ["#8A6A3A"]}, durability=384, ranged={"draw_ticks": 20, "max_damage": 9})
item("shears", "Shears", 1, {"shape": "shears", "colors": ["#E6E6E6"]}, durability=238, tool={"kind": "shears", "tier": 0, "speed": 1.5, "material": "iron"})
item("flint_and_steel", "Flint and Steel", 1, {"shape": "flint_and_steel", "colors": ["#888888"]}, durability=64, use="ignite")
item("bucket", "Bucket", 16, {"shape": "bucket", "colors": ["#C8C8C8", "#C8C8C8"]}, use="bucket")
item("water_bucket", "Water Bucket", 1, {"shape": "bucket", "colors": ["#C8C8C8", "#3C61DC"]}, use="bucket", places="minecraft:water")
item("lava_bucket", "Lava Bucket", 1, {"shape": "bucket", "colors": ["#C8C8C8", "#F58A1E"]}, use="bucket", places="minecraft:lava")
item("milk_bucket", "Milk Bucket", 1, {"shape": "bucket", "colors": ["#C8C8C8", "#FAFAFA"]}, use="drink_milk")
item("compass", "Compass", 64, {"shape": "ball", "colors": ["#C8C8C8"]})
item("clock", "Clock", 64, {"shape": "ball", "colors": ["#FCEE4B"]})
for mob, (c1, c2) in {"cow": ("#443626", "#A1A1A1"), "pig": ("#F0A5A2", "#DB635F"), "sheep": ("#E7E7E7", "#FFB5B5"),
                      "chicken": ("#A1A1A1", "#FF0000"), "zombie": ("#00AFAF", "#799C65"), "skeleton": ("#C1C1C1", "#494949"),
                      "creeper": ("#0DA70B", "#000000"), "spider": ("#342D27", "#A80E0E")}.items():
    item(mob + "_spawn_egg", mob.title() + " Spawn Egg", 64, {"shape": "egg2", "colors": [c1, c2]}, spawns="minecraft:" + mob)

# ----------------------------------------------------------------------------
# Recipes (Section 7.2 / Appendix A)
# ----------------------------------------------------------------------------
TAGS = {
    "#minecraft:planks": ["minecraft:%s_planks" % w for w in ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "crimson", "warped"]],
    "#minecraft:logs": ["minecraft:%s_log" % w for w in ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"]] + ["minecraft:crimson_stem", "minecraft:warped_stem"],
    "#minecraft:logs_that_burn": ["minecraft:%s_log" % w for w in ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"]],
    "#minecraft:wool": ["minecraft:%s_wool" % c for c in COLORS16],
    "#minecraft:coals": ["minecraft:coal", "minecraft:charcoal"],
    "#minecraft:stone_tool_materials": ["minecraft:cobblestone", "minecraft:blackstone"],
    "#minecraft:soul_fire_base_blocks": ["minecraft:soul_sand", "minecraft:soul_soil"],
}
RECIPES = []
def shaped(out, count, pattern, key):
    RECIPES.append({"type": "shaped", "result": out, "count": count, "pattern": pattern, "key": key})
def shapeless(out, count, ingredients):
    RECIPES.append({"type": "shapeless", "result": out, "count": count, "ingredients": ingredients})
def smelt(inp, out, xp=0.1, ticks=200):
    RECIPES.append({"type": "smelting", "input": inp, "result": out, "xp": xp, "cook_ticks": ticks})
M = lambda s: "minecraft:" + s
for w in ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"]:
    shapeless(M(w + "_planks"), 4, [M(w + "_log")])
for w in ["crimson", "warped"]:
    shapeless(M(w + "_planks"), 4, [M(w + "_stem")])
shaped(M("stick"), 4, ["#", "#"], {"#": "#minecraft:planks"})
shaped(M("crafting_table"), 1, ["##", "##"], {"#": "#minecraft:planks"})
shaped(M("chest"), 1, ["###", "# #", "###"], {"#": "#minecraft:planks"})
shaped(M("furnace"), 1, ["###", "# #", "###"], {"#": "#minecraft:stone_tool_materials"})
shaped(M("torch"), 4, ["C", "S"], {"C": "#minecraft:coals", "S": M("stick")})
shaped(M("soul_torch"), 4, ["C", "S", "B"], {"C": "#minecraft:coals", "S": M("stick"), "B": "#minecraft:soul_fire_base_blocks"})
shaped(M("ladder"), 3, ["S S", "SSS", "S S"], {"S": M("stick")})
shaped(M("bookshelf"), 1, ["###", "BBB", "###"], {"#": "#minecraft:planks", "B": M("book")})
shapeless(M("book"), 1, [M("paper"), M("paper"), M("paper"), M("leather")])
shaped(M("paper"), 3, ["###"], {"#": M("sugar_cane")})
shaped(M("red_bed"), 1, ["WWW", "PPP"], {"W": M("red_wool"), "P": "#minecraft:planks"})
shaped(M("white_wool"), 1, ["SS", "SS"], {"S": M("string")})
shapeless(M("red_wool"), 1, [M("red_dye"), M("white_wool")])
shapeless(M("red_dye"), 1, [M("poppy")])
shapeless(M("white_dye"), 1, [M("bone_meal")])
shapeless(M("bone_meal"), 3, [M("bone")])
shaped(M("tnt"), 1, ["GSG", "SGS", "GSG"], {"G": M("gunpowder"), "S": M("sand")})
shaped(M("stone_bricks"), 4, ["##", "##"], {"#": M("stone")})
shaped(M("bricks"), 1, ["##", "##"], {"#": M("brick")})
shaped(M("nether_bricks"), 1, ["##", "##"], {"#": M("nether_brick")})
shaped(M("sandstone"), 1, ["##", "##"], {"#": M("sand")})
shaped(M("red_sandstone"), 1, ["##", "##"], {"#": M("red_sand")})
shaped(M("snow_block"), 1, ["##", "##"], {"#": M("snowball")})
shaped(M("clay"), 1, ["##", "##"], {"#": M("clay_ball")})
shaped(M("glowstone"), 1, ["##", "##"], {"#": M("glowstone_dust")})
shaped(M("quartz_block"), 1, ["##", "##"], {"#": M("quartz")})
shaped(M("polished_granite"), 4, ["##", "##"], {"#": M("granite")})
shaped(M("polished_diorite"), 4, ["##", "##"], {"#": M("diorite")})
shaped(M("polished_andesite"), 4, ["##", "##"], {"#": M("andesite")})
shapeless(M("mossy_cobblestone"), 1, [M("cobblestone"), M("vine")]) if False else None
shaped(M("stone_slab"), 6, ["###"], {"#": M("smooth_stone")})
shaped(M("cobblestone_slab"), 6, ["###"], {"#": M("cobblestone")})
shaped(M("oak_slab"), 6, ["###"], {"#": M("oak_planks")})
shaped(M("jack_o_lantern"), 1, ["P", "T"], {"P": M("carved_pumpkin"), "T": M("torch")})
for mat, blk in {"iron_ingot": "iron_block", "gold_ingot": "gold_block", "diamond": "diamond_block", "emerald": "emerald_block",
                 "coal": "coal_block", "lapis_lazuli": "lapis_block", "redstone": "redstone_block", "netherite_ingot": "netherite_block"}.items():
    shaped(M(blk), 1, ["###", "###", "###"], {"#": M(mat)})
    shapeless(M(mat), 9, [M(blk)])
shaped(M("iron_ingot"), 1, ["###", "###", "###"], {"#": M("iron_nugget")})
shaped(M("gold_ingot"), 1, ["###", "###", "###"], {"#": M("gold_nugget")})
shapeless(M("iron_nugget"), 9, [M("iron_ingot")])
shapeless(M("gold_nugget"), 9, [M("gold_ingot")])
shapeless(M("netherite_ingot"), 1, [M("netherite_scrap")] * 4 + [M("gold_ingot")] * 4)
TOOL_MAT = {"wooden": "#minecraft:planks", "stone": "#minecraft:stone_tool_materials", "iron": M("iron_ingot"),
            "golden": M("gold_ingot"), "diamond": M("diamond")}
for t, mat in TOOL_MAT.items():
    shaped(M(t + "_pickaxe"), 1, ["XXX", " # ", " # "], {"X": mat, "#": M("stick")})
    shaped(M(t + "_axe"), 1, ["XX", "X#", " #"], {"X": mat, "#": M("stick")})
    shaped(M(t + "_shovel"), 1, ["X", "#", "#"], {"X": mat, "#": M("stick")})
    shaped(M(t + "_sword"), 1, ["X", "X", "#"], {"X": mat, "#": M("stick")})
    shaped(M(t + "_hoe"), 1, ["XX", " #", " #"], {"X": mat, "#": M("stick")})
for m, mat in {"leather": M("leather"), "iron": M("iron_ingot"), "golden": M("gold_ingot"), "diamond": M("diamond")}.items():
    shaped(M(m + "_helmet"), 1, ["XXX", "X X"], {"X": mat})
    shaped(M(m + "_chestplate"), 1, ["X X", "XXX", "XXX"], {"X": mat})
    shaped(M(m + "_leggings"), 1, ["XXX", "X X", "X X"], {"X": mat})
    shaped(M(m + "_boots"), 1, ["X X", "X X"], {"X": mat})
shaped(M("bread"), 1, ["###"], {"#": M("wheat")})
shaped(M("bowl"), 4, ["# #", " # "], {"#": "#minecraft:planks"})
shapeless(M("mushroom_stew"), 1, [M("red_mushroom"), M("brown_mushroom"), M("bowl")])
shapeless(M("pumpkin_pie"), 1, [M("pumpkin"), M("sugar"), M("egg")])
shapeless(M("sugar"), 1, [M("sugar_cane")])
shaped(M("golden_apple"), 1, ["###", "#A#", "###"], {"#": M("gold_ingot"), "A": M("apple")})
shaped(M("melon"), 1, ["###", "###", "###"], {"#": M("melon_slice")})
shaped(M("bucket"), 1, ["# #", " # "], {"#": M("iron_ingot")})
shaped(M("shears"), 1, [" #", "# "], {"#": M("iron_ingot")})
shapeless(M("flint_and_steel"), 1, [M("iron_ingot"), M("flint")])
shaped(M("bow"), 1, [" #S", "# S", " #S"], {"#": M("stick"), "S": M("string")})
shaped(M("arrow"), 4, ["F", "#", "E"], {"F": M("flint"), "#": M("stick"), "E": M("feather")})
shaped(M("compass"), 1, [" # ", "#R#", " # "], {"#": M("iron_ingot"), "R": M("redstone")})
shaped(M("clock"), 1, [" # ", "#R#", " # "], {"#": M("gold_ingot"), "R": M("redstone")})
shaped(M("glass"), 1, ["#"], {"#": M("glass")}) if False else None
RECIPES[:] = [r for r in RECIPES if r]
# Smelting (Section 7.2 furnace list)
smelt(M("iron_ore"), M("iron_ingot"), 0.7); smelt(M("gold_ore"), M("gold_ingot"), 1.0)
smelt(M("cobblestone"), M("stone"), 0.1); smelt(M("stone"), M("smooth_stone"), 0.1)
smelt(M("sand"), M("glass"), 0.1); smelt(M("red_sand"), M("glass"), 0.1)
smelt("#minecraft:logs_that_burn", M("charcoal"), 0.15)
smelt(M("clay_ball"), M("brick"), 0.3); smelt(M("netherrack"), M("nether_brick"), 0.1)
smelt(M("cactus"), M("green_dye"), 1.0); smelt(M("ancient_debris"), M("netherite_scrap"), 2.0)
smelt(M("clay"), M("terracotta"), 0.35); smelt(M("wet_sponge") if False else M("sponge"), M("sponge"), 0.15)
for raw, cooked in [("beef", "cooked_beef"), ("porkchop", "cooked_porkchop"), ("chicken", "cooked_chicken"), ("mutton", "cooked_mutton"),
                    ("cod", "cooked_cod"), ("potato", "baked_potato")]:
    smelt(M(raw), M(cooked), 0.35)
for o, it in [("coal_ore", "coal"), ("diamond_ore", "diamond"), ("emerald_ore", "emerald"), ("lapis_ore", "lapis_lazuli"),
              ("redstone_ore", "redstone"), ("nether_quartz_ore", "quartz"), ("nether_gold_ore", "gold_ingot")]:
    smelt(M(o), M(it), 0.7)
# Fuel (Section 7.2): smelts x 200 ticks
FUEL = {"minecraft:coal": 1600, "minecraft:charcoal": 1600, "#minecraft:planks": 300, "#minecraft:logs_that_burn": 300,
        "minecraft:stick": 100, "minecraft:coal_block": 16000, "minecraft:blaze_rod": 2400, "minecraft:lava_bucket": 20000,
        "minecraft:crafting_table": 300, "minecraft:chest": 300, "minecraft:bookshelf": 300, "minecraft:ladder": 300,
        "minecraft:wooden_pickaxe": 200, "minecraft:wooden_axe": 200, "minecraft:wooden_shovel": 200, "minecraft:wooden_sword": 200,
        "minecraft:wooden_hoe": 200, "minecraft:bowl": 100, "minecraft:oak_slab": 150, "#minecraft:wool": 100}

# ----------------------------------------------------------------------------
# Entities (Sections 8-10, Appendix I2). Models are original box models.
# ----------------------------------------------------------------------------
def part(name, origin, size, color, pattern="noise", face=None, pivot=None, color2=None):
    d = {"name": name, "origin": origin, "size": size, "color": color, "pattern": pattern}
    if face: d["face"] = face
    if pivot: d["pivot"] = pivot
    if color2: d["color2"] = color2
    return d
def quad_legs(color, h, w, xo, zf, zb, pattern="noise", color2=None):
    return [part("leg%d" % i, [x - w / 2, 0, z - w / 2], [w, h, w], color, pattern, pivot=[x, h, z], color2=color2)
            for i, (x, z) in enumerate([(-xo, zf), (xo, zf), (-xo, zb), (xo, zb)])]
QUAD_WALK = {"leg0": {"axis": "x", "amp": 1.4, "phase": 0}, "leg1": {"axis": "x", "amp": 1.4, "phase": 3.14159},
             "leg2": {"axis": "x", "amp": 1.4, "phase": 3.14159}, "leg3": {"axis": "x", "amp": 1.4, "phase": 0}}
BIPED_WALK = {"leg0": {"axis": "x", "amp": 1.4, "phase": 0}, "leg1": {"axis": "x", "amp": 1.4, "phase": 3.14159},
              "arm0": {"axis": "x", "amp": 1.0, "phase": 3.14159}, "arm1": {"axis": "x", "amp": 1.0, "phase": 0}}
ZOMBIE_WALK = {"leg0": {"axis": "x", "amp": 1.4, "phase": 0}, "leg1": {"axis": "x", "amp": 1.4, "phase": 3.14159},
               "arm0": {"axis": "x", "amp": 0.15, "phase": 0, "offset": -1.5708}, "arm1": {"axis": "x", "amp": 0.15, "phase": 3.14159, "offset": -1.5708}}
SPIDER_WALK = {"leg%d" % i: {"axis": "y", "amp": 0.4, "phase": (i % 2) * 3.14159} for i in range(8)}
p16 = lambda *v: [x / 16.0 for x in v]

def biped(skin, shirt, pants, face_col, eye_col, arm_col=None, thin=False):
    aw = 2 if thin else 4
    return [
        part("head", p16(-4, 24, -4), p16(8, 8, 8), skin, face={"eyes": eye_col, "mouth": face_col}, pivot=p16(0, 24, 0)),
        part("body", p16(-4, 12, -2), p16(8, 12, 4), shirt, pivot=p16(0, 24, 0)),
        part("arm0", p16(-4 - aw, 12, -aw / 2), p16(aw, 12, aw), arm_col or skin, pivot=p16(-4 - aw / 2, 22, 0)),
        part("arm1", p16(4, 12, -aw / 2), p16(aw, 12, aw), arm_col or skin, pivot=p16(4 + aw / 2, 22, 0)),
        part("leg0", p16(-4, 0, -2) if not thin else p16(-3, 0, -1), p16(4, 12, 4) if not thin else p16(2, 12, 2), pants, pivot=p16(-2, 12, 0)),
        part("leg1", p16(0, 0, -2) if not thin else p16(1, 0, -1), p16(4, 12, 4) if not thin else p16(2, 12, 2), pants, pivot=p16(2, 12, 0)),
    ]

ENTITIES = [
    {"id": "minecraft:cow", "name": "Cow", "health": 10, "speed": 0.25, "width": 0.9, "height": 1.4, "eye": 1.3, "category": "creature",
     "xp": [1, 3], "drops": [{"item": "minecraft:beef", "min": 1, "max": 3, "cooked": "minecraft:cooked_beef"}, {"item": "minecraft:leather", "min": 0, "max": 2}],
     "goals": [[0, "swim"], [1, "panic"], [3, "tempt:minecraft:wheat"], [5, "wander"], [6, "look_at_player"], [7, "random_look"]],
     "breed_item": "minecraft:wheat", "interact": "milk",
     "sounds": {"ambient": "entity.cow.ambient", "hurt": "entity.cow.hurt", "death": "entity.cow.death", "step": "entity.cow.step"}, "sound_volume": 0.4,
     "model": [part("body", p16(-6, 10, -9), p16(12, 10, 18), "#443626", "spots", color2="#E8E8E8"),
               part("head", p16(-4, 12, -15), p16(8, 8, 6), "#443626", "spots", face={"eyes": "#111111", "mouth": "#C8A8A0"}, color2="#E8E8E8", pivot=p16(0, 16, -9)),
               part("horn0", p16(-5, 19, -13), p16(1, 3, 1), "#D8D0C0"), part("horn1", p16(4, 19, -13), p16(1, 3, 1), "#D8D0C0")]
              + quad_legs("#443626", 12 / 16, 4 / 16, 4 / 16, -6 / 16, 7 / 16, "spots", "#E8E8E8"),
     "animations": {"walk": QUAD_WALK, "attachments": {"horn0": "head", "horn1": "head"}}, "spawn": {"group": [2, 4], "weight": 8, "light_min": 9, "ground": ["minecraft:grass_block"]}},
    {"id": "minecraft:pig", "name": "Pig", "health": 10, "speed": 0.25, "width": 0.9, "height": 0.9, "eye": 0.765, "category": "creature",
     "xp": [1, 3], "drops": [{"item": "minecraft:porkchop", "min": 1, "max": 3, "cooked": "minecraft:cooked_porkchop"}],
     "goals": [[0, "swim"], [1, "panic"], [4, "tempt:minecraft:carrot"], [6, "wander"], [7, "look_at_player"], [8, "random_look"]],
     "breed_item": "minecraft:carrot",
     "sounds": {"ambient": "entity.pig.ambient", "hurt": "entity.pig.hurt", "death": "entity.pig.death", "step": "entity.pig.step"},
     "model": [part("body", p16(-5, 6, -8), p16(10, 8, 16), "#F0A5A2"),
               part("head", p16(-4, 6, -15), p16(8, 8, 8), "#F0A5A2", face={"eyes": "#111111", "snout": "#DB635F"}, pivot=p16(0, 10, -8))]
              + quad_legs("#E8958F", 6 / 16, 4 / 16, 3 / 16, -5 / 16, 6 / 16),
     "animations": {"walk": QUAD_WALK}, "spawn": {"group": [2, 4], "weight": 10, "light_min": 9, "ground": ["minecraft:grass_block"]}},
    {"id": "minecraft:sheep", "name": "Sheep", "health": 8, "speed": 0.23, "width": 0.9, "height": 1.3, "eye": 1.235, "category": "creature",
     "xp": [1, 3], "drops": [{"item": "minecraft:mutton", "min": 1, "max": 2, "cooked": "minecraft:cooked_mutton"}, {"item": "minecraft:white_wool", "min": 1, "max": 1}],
     "goals": [[0, "swim"], [1, "panic"], [3, "tempt:minecraft:wheat"], [5, "eat_grass"], [6, "wander"], [7, "look_at_player"], [8, "random_look"]],
     "breed_item": "minecraft:wheat", "interact": "shear",
     "sounds": {"ambient": "entity.sheep.ambient", "hurt": "entity.sheep.hurt", "death": "entity.sheep.death", "step": "entity.sheep.step"},
     "model": [part("body", p16(-5, 11, -8), p16(10, 9, 16), "#E7E7E7", "wool"),
               part("head", p16(-3, 14, -13), p16(6, 6, 7), "#D8C8B8", face={"eyes": "#111111"}, pivot=p16(0, 17, -8))]
              + quad_legs("#E7E7E7", 12 / 16, 4 / 16, 3 / 16, -5 / 16, 6 / 16, "wool"),
     "animations": {"walk": QUAD_WALK}, "spawn": {"group": [2, 4], "weight": 12, "light_min": 9, "ground": ["minecraft:grass_block"]}},
    {"id": "minecraft:chicken", "name": "Chicken", "health": 4, "speed": 0.25, "width": 0.4, "height": 0.7, "eye": 0.644, "category": "creature",
     "xp": [1, 3], "drops": [{"item": "minecraft:chicken", "min": 1, "max": 1, "cooked": "minecraft:cooked_chicken"}, {"item": "minecraft:feather", "min": 0, "max": 2}],
     "goals": [[0, "swim"], [1, "panic"], [3, "tempt:minecraft:wheat_seeds"], [5, "wander"], [6, "look_at_player"], [7, "random_look"]],
     "breed_item": "minecraft:wheat_seeds", "slow_fall": True, "lays_eggs": True,
     "sounds": {"ambient": "entity.chicken.ambient", "hurt": "entity.chicken.hurt", "death": "entity.chicken.death", "step": "entity.chicken.step"},
     "model": [part("body", p16(-3, 4, -4), p16(6, 6, 8), "#F5F5F5"),
               part("head", p16(-2, 8, -6), p16(4, 6, 3), "#F5F5F5", face={"eyes": "#111111"}, pivot=p16(0, 9, -4)),
               part("beak", p16(-2, 10, -8), p16(4, 2, 2), "#F0B020"), part("wattle", p16(-1, 8, -7), p16(2, 2, 1), "#E01E1E"),
               part("leg0", p16(-2, 0, -1), p16(1, 5, 1), "#F0B020", pivot=p16(-1.5, 5, -0.5)),
               part("leg1", p16(1, 0, -1), p16(1, 5, 1), "#F0B020", pivot=p16(1.5, 5, -0.5))],
     "animations": {"walk": {"leg0": {"axis": "x", "amp": 1.4, "phase": 0}, "leg1": {"axis": "x", "amp": 1.4, "phase": 3.14159}},
                    "attachments": {"beak": "head", "wattle": "head"}}, "spawn": {"group": [2, 4], "weight": 10, "light_min": 9, "ground": ["minecraft:grass_block"]}},
    {"id": "minecraft:zombie", "name": "Zombie", "health": 20, "damage": [2, 3, 4], "speed": 0.23, "width": 0.6, "height": 1.95, "eye": 1.74, "category": "monster",
     "xp": [5, 5], "drops": [{"item": "minecraft:rotten_flesh", "min": 0, "max": 2}], "undead": True, "burns_in_sun": True,
     "goals": [[0, "swim"], [2, "melee_attack"], [5, "move_towards_target"], [7, "wander"], [8, "look_at_player"], [8, "random_look"], [1, "target:player"]],
     "sounds": {"ambient": "entity.zombie.ambient", "hurt": "entity.zombie.hurt", "death": "entity.zombie.death", "step": "entity.zombie.step"},
     "model": biped("#5E8A4A", "#2AA8A8", "#3A3AAA", "#2A4A2A", "#111111"),
     "animations": {"walk": ZOMBIE_WALK}, "spawn": {"group": [4, 4], "weight": 100, "light_max": 7}},
    {"id": "minecraft:skeleton", "name": "Skeleton", "health": 20, "damage": [2, 3, 4], "speed": 0.25, "width": 0.6, "height": 1.99, "eye": 1.74, "category": "monster",
     "xp": [5, 5], "drops": [{"item": "minecraft:bone", "min": 0, "max": 2}, {"item": "minecraft:arrow", "min": 0, "max": 2}], "undead": True, "burns_in_sun": True,
     "goals": [[0, "swim"], [4, "ranged_attack"], [5, "wander"], [6, "look_at_player"], [6, "random_look"], [1, "target:player"]],
     "ranged": {"range": 15, "preferred": 10, "draw_ticks": 20, "interval": 40},
     "sounds": {"ambient": "entity.skeleton.ambient", "hurt": "entity.skeleton.hurt", "death": "entity.skeleton.death", "step": "entity.skeleton.step", "shoot": "entity.skeleton.shoot"},
     "model": biped("#C8C8C8", "#B8B8B8", "#A8A8A8", "#3A3A3A", "#1A1A1A", thin=True),
     "animations": {"walk": BIPED_WALK}, "spawn": {"group": [4, 4], "weight": 100, "light_max": 7}},
    {"id": "minecraft:creeper", "name": "Creeper", "health": 20, "speed": 0.25, "width": 0.6, "height": 1.7, "eye": 1.445, "category": "monster",
     "xp": [5, 5], "drops": [{"item": "minecraft:gunpowder", "min": 0, "max": 2}], "fuse": 30, "explosion": 3,
     "goals": [[1, "swim"], [2, "creeper_swell"], [4, "melee_attack"], [5, "wander"], [6, "look_at_player"], [6, "random_look"], [1, "target:player"]],
     "sounds": {"hurt": "entity.creeper.hurt", "death": "entity.creeper.death", "primed": "entity.creeper.primed"},
     "model": [part("head", p16(-4, 18, -4), p16(8, 8, 8), "#4FA83A", "creeper", face={"creeper": "#111111"}, pivot=p16(0, 18, 0)),
               part("body", p16(-4, 6, -2), p16(8, 12, 4), "#4FA83A", "creeper")]
              + quad_legs("#4FA83A", 6 / 16, 4 / 16, 2 / 16, -4 / 16, 4 / 16, "creeper"),
     "animations": {"walk": QUAD_WALK}, "spawn": {"group": [4, 4], "weight": 100, "light_max": 7}},
    {"id": "minecraft:spider", "name": "Spider", "health": 16, "damage": [2, 2, 3], "speed": 0.3, "width": 1.4, "height": 0.9, "eye": 0.65, "category": "monster",
     "xp": [5, 5], "drops": [{"item": "minecraft:string", "min": 0, "max": 2}, {"item": "minecraft:spider_eye", "min": 0, "max": 1, "chance": 0.333}],
     "climbs": True, "neutral_in_light": 12,
     "goals": [[1, "swim"], [3, "leap_at_target"], [4, "melee_attack"], [5, "wander"], [6, "look_at_player"], [6, "random_look"], [1, "target:player"]],
     "sounds": {"ambient": "entity.spider.ambient", "hurt": "entity.spider.hurt", "death": "entity.spider.death", "step": "entity.spider.step"},
     "model": [part("head", p16(-4, 5, -11), p16(8, 8, 8), "#342D27", face={"spider": "#C81E1E"}, pivot=p16(0, 9, -3)),
               part("body0", p16(-3, 6, -3), p16(6, 6, 6), "#342D27"),
               part("body1", p16(-5, 5, 3), p16(10, 8, 12), "#2A2420")]
              + [part("leg%d" % i, p16(-15 if i % 2 == 0 else 1, 8, -4 + (i // 2) * 2.5), p16(14, 2, 2), "#2A2420", pivot=p16(-1 if i % 2 == 0 else 1, 9, -3 + (i // 2) * 2.5)) for i in range(8)],
     "animations": {"walk": SPIDER_WALK}, "spawn": {"group": [4, 4], "weight": 100, "light_max": 7}},
    {"id": "minecraft:item", "name": "Item", "health": 5, "speed": 0, "width": 0.25, "height": 0.25, "eye": 0.2, "category": "misc", "model": [], "sounds": {}},
    {"id": "minecraft:experience_orb", "name": "Experience Orb", "health": 5, "speed": 0, "width": 0.5, "height": 0.5, "eye": 0.25, "category": "misc", "model": [], "sounds": {}},
    {"id": "minecraft:arrow", "name": "Arrow", "health": 1, "speed": 0, "width": 0.5, "height": 0.5, "eye": 0.13, "category": "misc", "model": [], "sounds": {"hit": "entity.arrow.hit"}},
    {"id": "minecraft:tnt", "name": "Primed TNT", "health": 1, "speed": 0, "width": 0.98, "height": 0.98, "eye": 0.15, "category": "misc", "fuse": 80, "explosion": 4, "model": [], "sounds": {"primed": "entity.tnt.primed"}},
    {"id": "minecraft:falling_block", "name": "Falling Block", "health": 1, "speed": 0, "width": 0.98, "height": 0.98, "eye": 0.49, "category": "misc", "model": [], "sounds": {}},
    {"id": "minecraft:player", "name": "Player", "health": 20, "speed": 0.1, "width": 0.6, "height": 1.8, "eye": 1.62, "category": "misc",
     "model": biped("#C89A7A", "#2AA8C8", "#3A3AAA", "#7A4A3A", "#3A2A8A"), "animations": {"walk": BIPED_WALK},
     "sounds": {"hurt": "entity.player.hurt", "death": "entity.player.death"}},
]

# ----------------------------------------------------------------------------
# Biomes (Section 4.2, Appendix BC/AM). temperature/humidity bands.
# ----------------------------------------------------------------------------
PASSIVE = [{"type": "minecraft:sheep", "weight": 12}, {"type": "minecraft:pig", "weight": 10}, {"type": "minecraft:chicken", "weight": 10}, {"type": "minecraft:cow", "weight": 8}]
HOSTILE = [{"type": "minecraft:spider", "weight": 100}, {"type": "minecraft:zombie", "weight": 95}, {"type": "minecraft:skeleton", "weight": 100}, {"type": "minecraft:creeper", "weight": 100}]
def biome(bid, temp, hum, top="minecraft:grass_block", filler="minecraft:dirt", under="minecraft:gravel", grass="#91BD59", foliage="#77AB2F",
          trees=None, density=0.0, flowers=0.0, grass_tufts=0.0, creatures=PASSIVE, monsters=HOSTILE, precip="rain", amp=1.0, extra=None):
    d = {"id": "minecraft:" + bid, "temperature": temp, "humidity": hum, "top": top, "filler": filler, "underwater": under,
         "grass_color": grass, "foliage_color": foliage, "trees": trees or [], "tree_density": density, "flower_density": flowers,
         "grass_density": grass_tufts, "spawns": {"creature": creatures, "monster": monsters}, "precipitation": "snow" if temp < 0.15 else precip,
         "terrain_amp": amp}
    if extra: d.update(extra)
    return d
BIOMES = [
    biome("ocean", 0.5, 0.5, under="minecraft:gravel", creatures=[]),
    biome("deep_ocean", 0.5, 0.5, under="minecraft:gravel", creatures=[]),
    biome("frozen_ocean", 0.0, 0.5, under="minecraft:gravel", creatures=[], extra={"freeze": True}),
    biome("cold_ocean", 0.5, 0.5, under="minecraft:gravel", creatures=[]),
    biome("lukewarm_ocean", 0.5, 0.5, under="minecraft:sand", creatures=[]),
    biome("warm_ocean", 0.5, 0.5, under="minecraft:sand", creatures=[]),
    biome("river", 0.5, 0.5, under="minecraft:sand", creatures=[]),
    biome("frozen_river", 0.0, 0.5, under="minecraft:sand", creatures=[], extra={"freeze": True}),
    biome("beach", 0.8, 0.4, top="minecraft:sand", filler="minecraft:sand", under="minecraft:sand", creatures=[]),
    biome("snowy_beach", 0.05, 0.3, top="minecraft:sand", filler="minecraft:sand", under="minecraft:sand", creatures=[]),
    biome("stone_shore", 0.2, 0.3, top="minecraft:stone", filler="minecraft:stone", under="minecraft:stone", creatures=[]),
    biome("plains", 0.8, 0.4, trees=["oak"], density=0.003, flowers=0.01, grass_tufts=0.25),
    biome("sunflower_plains", 0.8, 0.4, trees=["oak"], density=0.003, flowers=0.03, grass_tufts=0.25),
    biome("forest", 0.7, 0.8, trees=["oak", "oak", "birch"], density=0.08, flowers=0.01, grass_tufts=0.08, grass="#79C05A", foliage="#59AE30"),
    biome("flower_forest", 0.7, 0.8, trees=["oak", "birch"], density=0.04, flowers=0.12, grass_tufts=0.05, grass="#79C05A", foliage="#59AE30"),
    biome("birch_forest", 0.6, 0.6, trees=["birch"], density=0.08, flowers=0.01, grass_tufts=0.08, grass="#88BB67", foliage="#6BA941"),
    biome("dark_forest", 0.7, 0.8, trees=["dark_oak", "dark_oak", "oak"], density=0.16, grass_tufts=0.04, grass="#507A32", foliage="#59AE30"),
    biome("taiga", 0.25, 0.8, trees=["spruce"], density=0.08, grass_tufts=0.1, grass="#86B783", foliage="#68A464", extra={"ferns": True}),
    biome("snowy_taiga", -0.5, 0.4, trees=["spruce"], density=0.06, grass="#80B497", foliage="#60A17B"),
    biome("giant_tree_taiga", 0.3, 0.8, top="minecraft:podzol", trees=["spruce"], density=0.12, grass_tufts=0.1, grass="#86B87F", foliage="#68A55F"),
    biome("snowy_tundra", 0.0, 0.5, trees=["spruce"], density=0.001, grass="#80B497", foliage="#60A17B", creatures=[{"type": "minecraft:chicken", "weight": 10}]),
    biome("ice_spikes", 0.0, 0.5, top="minecraft:snow_block", creatures=[]),
    biome("desert", 2.0, 0.0, top="minecraft:sand", filler="minecraft:sand", under="minecraft:sand", grass="#BFB755", foliage="#AEA42A",
          creatures=[], extra={"cactus": 0.006, "dead_bush": 0.004, "stone_below": "minecraft:sandstone"}),
    biome("savanna", 1.2, 0.0, trees=["acacia"], density=0.005, grass_tufts=0.5, grass="#BFB755", foliage="#AEA42A"),
    biome("jungle", 0.95, 0.9, trees=["jungle", "jungle", "oak"], density=0.18, grass_tufts=0.3, grass="#59C93C", foliage="#30BB0B",
          creatures=[{"type": "minecraft:chicken", "weight": 10}, {"type": "minecraft:pig", "weight": 10}]),
    biome("swamp", 0.8, 0.9, trees=["oak"], density=0.02, grass_tufts=0.05, grass="#6A7039", foliage="#6A7039", under="minecraft:clay",
          extra={"mushrooms": 0.01}),
    biome("badlands", 2.0, 0.0, top="minecraft:red_sand", filler="minecraft:orange_terracotta", under="minecraft:red_sand", grass="#90814D", foliage="#9E814D",
          creatures=[], extra={"cactus": 0.003, "dead_bush": 0.004, "bands": True}),
    biome("mountains", 0.2, 0.3, trees=["spruce", "oak"], density=0.01, grass_tufts=0.05, grass="#8AB689", foliage="#6DA36B", amp=2.2,
          extra={"emerald": True, "stony_peaks": 110}),
    biome("gravelly_mountains", 0.2, 0.3, top="minecraft:gravel", filler="minecraft:gravel", trees=["spruce"], density=0.003, amp=2.2, grass="#8AB689", foliage="#6DA36B"),
    biome("snowy_mountains", 0.0, 0.5, trees=["spruce"], density=0.004, amp=1.8, grass="#80B497", foliage="#60A17B"),
    biome("mushroom_fields", 0.9, 1.0, top="minecraft:mycelium", creatures=[], monsters=[], extra={"mushrooms": 0.03}),
    # Nether (Section 5.2)
    biome("nether_wastes", 2.0, 0.0, top="minecraft:netherrack", filler="minecraft:netherrack", creatures=[], monsters=[], precip="none"),
    biome("crimson_forest", 2.0, 0.0, top="minecraft:crimson_nylium", filler="minecraft:netherrack", creatures=[], monsters=[], precip="none"),
    biome("warped_forest", 2.0, 0.0, top="minecraft:warped_nylium", filler="minecraft:netherrack", creatures=[], monsters=[], precip="none"),
    biome("soul_sand_valley", 2.0, 0.0, top="minecraft:soul_sand", filler="minecraft:soul_soil", creatures=[], monsters=[{"type": "minecraft:skeleton", "weight": 20}], precip="none"),
    biome("basalt_deltas", 2.0, 0.0, top="minecraft:basalt", filler="minecraft:blackstone", creatures=[], monsters=[], precip="none"),
    # End (Section 5.5)
    biome("the_end", 0.5, 0.5, top="minecraft:end_stone", filler="minecraft:end_stone", creatures=[], monsters=[], precip="none"),
]

# ----------------------------------------------------------------------------
# Sounds (Section 13): block families x 5 events + entity/ui events.
# Audio is synthesized from these parameters (original, no copied assets).
# ----------------------------------------------------------------------------
FAMILY_SYNTH = {
    "stone": (1800, 0.10), "gravel": (900, 0.12), "grass": (1300, 0.10), "sand": (2600, 0.10), "wood": (500, 0.09),
    "wool": (700, 0.08), "glass": (4200, 0.18), "metal": (3200, 0.14), "snow": (2000, 0.10), "ladder": (550, 0.08),
    "netherrack": (1100, 0.10), "soul_sand": (800, 0.12), "soul_soil": (850, 0.11), "nylium": (1000, 0.10),
    "nether_wood": (520, 0.09), "nether_wart": (900, 0.09), "shroomlight": (1100, 0.09), "basalt": (1500, 0.10),
    "blackstone": (1600, 0.10), "nether_bricks": (1700, 0.10), "ancient_debris": (1200, 0.14), "netherite_block": (2600, 0.14),
}
SOUNDS = {}
for fam, (freq, dur) in FAMILY_SYNTH.items():
    for ev, (dm, vol) in {"break": (1.6, 1.0), "place": (1.0, 0.9), "step": (0.55, 0.35), "hit": (0.45, 0.4), "fall": (0.8, 0.5)}.items():
        SOUNDS["block.%s.%s" % (fam, ev)] = {"synth": "noise", "freq": freq, "duration": round(dur * dm, 3), "volume": vol}
VOICE = {
    "cow": ("moo", 140), "pig": ("oink", 420), "sheep": ("baa", 380), "chicken": ("cluck", 1100),
    "zombie": ("groan", 120), "skeleton": ("rattle", 900), "creeper": ("hiss", 3000), "spider": ("hiss", 700), "player": ("oof", 300),
}
for mob, (kind, f) in VOICE.items():
    for ev in ["ambient", "hurt", "death", "step"]:
        if ev == "step":
            SOUNDS["entity.%s.step" % mob] = {"synth": "noise", "freq": 900, "duration": 0.06, "volume": 0.25}
        else:
            SOUNDS["entity.%s.%s" % (mob, ev)] = {"synth": kind, "freq": f * (1.25 if ev == "hurt" else (0.8 if ev == "death" else 1.0)),
                                                  "duration": 0.25 if ev == "hurt" else (0.6 if ev == "death" else 0.5), "volume": 0.8}
SOUNDS.update({
    "entity.creeper.primed": {"synth": "hiss", "freq": 3500, "duration": 1.2, "volume": 0.9},
    "entity.skeleton.shoot": {"synth": "twang", "freq": 300, "duration": 0.25, "volume": 0.7},
    "entity.arrow.shoot": {"synth": "twang", "freq": 300, "duration": 0.25, "volume": 0.7},
    "entity.arrow.hit": {"synth": "noise", "freq": 2000, "duration": 0.08, "volume": 0.6},
    "entity.generic.explode": {"synth": "boom", "freq": 60, "duration": 1.4, "volume": 1.0},
    "entity.tnt.primed": {"synth": "hiss", "freq": 2500, "duration": 1.0, "volume": 0.8},
    "entity.generic.eat": {"synth": "crunch", "freq": 1600, "duration": 0.18, "volume": 0.6},
    "entity.player.burp": {"synth": "oof", "freq": 140, "duration": 0.35, "volume": 0.6},
    "entity.item.pickup": {"synth": "pop", "freq": 1400, "duration": 0.08, "volume": 0.4},
    "entity.experience_orb.pickup": {"synth": "chime", "freq": 1700, "duration": 0.12, "volume": 0.35},
    "entity.player.levelup": {"synth": "chime", "freq": 880, "duration": 0.7, "volume": 0.7},
    "entity.player.attack.strong": {"synth": "noise", "freq": 700, "duration": 0.08, "volume": 0.6},
    "entity.player.attack.crit": {"synth": "noise", "freq": 1200, "duration": 0.1, "volume": 0.7},
    "entity.player.splash": {"synth": "noise", "freq": 2400, "duration": 0.4, "volume": 0.6},
    "entity.generic.splash": {"synth": "noise", "freq": 2400, "duration": 0.3, "volume": 0.5},
    "entity.player.big_fall": {"synth": "oof", "freq": 200, "duration": 0.25, "volume": 0.8},
    "entity.player.small_fall": {"synth": "noise", "freq": 600, "duration": 0.1, "volume": 0.5},
    "entity.chicken.egg": {"synth": "pop", "freq": 500, "duration": 0.12, "volume": 0.6},
    "entity.cow.milk": {"synth": "noise", "freq": 1800, "duration": 0.3, "volume": 0.5},
    "entity.sheep.shear": {"synth": "noise", "freq": 3600, "duration": 0.15, "volume": 0.6},
    "entity.item.break": {"synth": "noise", "freq": 3000, "duration": 0.2, "volume": 0.8},
    "block.chest.open": {"synth": "creak", "freq": 260, "duration": 0.35, "volume": 0.6},
    "block.chest.close": {"synth": "creak", "freq": 200, "duration": 0.25, "volume": 0.6},
    "block.furnace.fire_crackle": {"synth": "noise", "freq": 1500, "duration": 0.2, "volume": 0.4},
    "block.fire.ambient": {"synth": "noise", "freq": 900, "duration": 0.5, "volume": 0.3},
    "block.fire.extinguish": {"synth": "hiss", "freq": 2500, "duration": 0.4, "volume": 0.5},
    "item.flintandsteel.use": {"synth": "noise", "freq": 4000, "duration": 0.1, "volume": 0.6},
    "item.bucket.fill": {"synth": "noise", "freq": 1500, "duration": 0.3, "volume": 0.6},
    "item.bucket.empty": {"synth": "noise", "freq": 1300, "duration": 0.3, "volume": 0.6},
    "block.portal.trigger": {"synth": "chime", "freq": 200, "duration": 1.5, "volume": 0.5},
    "block.portal.travel": {"synth": "chime", "freq": 150, "duration": 2.0, "volume": 0.5},
    "ui.button.click": {"synth": "pop", "freq": 900, "duration": 0.05, "volume": 0.5},
    "weather.rain": {"synth": "noise", "freq": 5000, "duration": 1.0, "volume": 0.15},
    "entity.lightning_bolt.thunder": {"synth": "boom", "freq": 45, "duration": 2.5, "volume": 1.0},
    "ambient.cave": {"synth": "groan", "freq": 70, "duration": 2.0, "volume": 0.3},
    # vanilla client ambience (Ambience.java); played from resource packs / Minecraft assets when available
    "block.portal.ambient": {"synth": "chime", "freq": 120, "duration": 2.0, "volume": 0.4},
    "block.lava.pop": {"synth": "pop", "freq": 300, "duration": 0.15, "volume": 0.5},
    "block.lava.ambient": {"synth": "noise", "freq": 400, "duration": 0.8, "volume": 0.3},
    "block.water.ambient": {"synth": "noise", "freq": 1800, "duration": 1.2, "volume": 0.2},
    "weather.rain.above": {"synth": "noise", "freq": 2500, "duration": 1.0, "volume": 0.1},
    "ambient.underwater.enter": {"synth": "noise", "freq": 700, "duration": 0.6, "volume": 0.5},
    "ambient.underwater.exit": {"synth": "noise", "freq": 1200, "duration": 0.5, "volume": 0.5},
    "ambient.underwater.loop": {"synth": "noise", "freq": 250, "duration": 3.0, "volume": 0.3},
    "ambient.underwater.loop.additions": {"synth": "pop", "freq": 500, "duration": 0.3, "volume": 0.3},
    "ambient.underwater.loop.additions.rare": {"synth": "groan", "freq": 90, "duration": 1.5, "volume": 0.3},
    "ambient.underwater.loop.additions.ultra_rare": {"synth": "groan", "freq": 60, "duration": 2.5, "volume": 0.3},
})
for nb in ["nether_wastes", "crimson_forest", "warped_forest", "soul_sand_valley", "basalt_deltas"]:
    SOUNDS["ambient.%s.loop" % nb] = {"synth": "noise", "freq": 200, "duration": 3.0, "volume": 0.3}
    SOUNDS["ambient.%s.mood" % nb] = {"synth": "groan", "freq": 65, "duration": 2.0, "volume": 0.3}
    SOUNDS["ambient.%s.additions" % nb] = {"synth": "groan", "freq": 110, "duration": 1.2, "volume": 0.3}

TUNING = {
    "_comment": "All gameplay tuning values (Section 1.6: never inline constants). Units: blocks, ticks.",
    "ticks_per_second": 20, "max_catchup_ticks": 2,
    "gravity": 0.08, "vertical_drag": 0.98, "default_slipperiness": 0.6, "air_drag": 0.91,
    "ground_accel_factor": 0.21600002, "air_accel": 0.02, "walk_speed": 0.1, "sprint_multiplier": 1.3, "sneak_multiplier": 0.3,
    "jump_velocity": 0.42, "sprint_jump_boost": 0.2, "step_height": 0.6, "water_drag": 0.8, "lava_drag": 0.5, "swim_up": 0.04,
    "ladder_max_fall": 0.15, "ladder_climb": 0.2, "fly_speed": 0.05, "creative_fly_vertical": 0.15,
    "player_width": 0.6, "player_height": 1.8, "player_eye": 1.62, "sneak_eye_drop": 0.25,
    "reach_survival": 4.5, "reach_creative": 5.0, "attack_reach": 3.0,
    "fall_damage_threshold": 3.0, "void_y": -64, "void_damage": 4,
    "knockback_horizontal": 0.4, "knockback_vertical": 0.36,
    "max_air": 300, "drown_damage": 2,
    "exhaustion_sprint_per_m": 0.1, "exhaustion_jump": 0.05, "exhaustion_sprint_jump": 0.2, "exhaustion_attack": 0.1,
    "exhaustion_mine": 0.005, "exhaustion_regen": 6.0, "exhaustion_swim_per_m": 0.01, "exhaustion_damage": 0.1,
    "regen_food_threshold": 18, "regen_interval": 80, "saturated_regen_interval": 10, "starve_interval": 80,
    "day_length": 24000, "autosave_ticks": 900, "default_view_distance": 8, "simulation_distance": 6,
    "spawn_caps": {"monster": 70, "creature": 10, "ambient": 15, "water_creature": 5, "water_ambient": 20},
    "spawn_exclusion_radius": 24, "despawn_soft": 32, "despawn_hard": 128, "despawn_chance": 800,
    "pathfinder_node_budget": 512, "item_pickup_delay": 10, "item_despawn_ticks": 6000, "eat_ticks": 32,
    "sea_level": 63, "lava_level": 10, "nether_lava_level": 31, "world_height": 256,
    "portal_ticks": 80, "bed_explosion_strength": 5, "sleep_start": 12542, "sleep_end": 23459,
    "rain_min_ticks": 12000, "rain_max_ticks": 24000, "clear_min_ticks": 24000, "clear_max_ticks": 72000, "thunder_chance": 0.1,
    "random_tick_speed": 3, "mining_reset_ticks": 6,
}

ORES = [  # Section 4.3 ore table
    {"block": "minecraft:coal_ore", "min_y": 0, "max_y": 127, "size": 17, "attempts": 20},
    {"block": "minecraft:iron_ore", "min_y": 0, "max_y": 63, "size": 9, "attempts": 20},
    {"block": "minecraft:gold_ore", "min_y": 0, "max_y": 31, "size": 9, "attempts": 2},
    {"block": "minecraft:redstone_ore", "min_y": 0, "max_y": 15, "size": 8, "attempts": 4},
    {"block": "minecraft:diamond_ore", "min_y": 1, "max_y": 15, "size": 8, "attempts": 1},
    {"block": "minecraft:lapis_ore", "min_y": 0, "max_y": 30, "size": 7, "attempts": 1},
    {"block": "minecraft:emerald_ore", "min_y": 4, "max_y": 31, "size": 1, "attempts": 11, "biomes": ["minecraft:mountains", "minecraft:gravelly_mountains", "minecraft:snowy_mountains"]},
    {"block": "minecraft:gravel", "min_y": 0, "max_y": 255, "size": 33, "attempts": 8},
    {"block": "minecraft:dirt", "min_y": 0, "max_y": 255, "size": 33, "attempts": 10},
    {"block": "minecraft:andesite", "min_y": 0, "max_y": 79, "size": 33, "attempts": 10},
    {"block": "minecraft:diorite", "min_y": 0, "max_y": 79, "size": 33, "attempts": 10},
    {"block": "minecraft:granite", "min_y": 0, "max_y": 79, "size": 33, "attempts": 10},
]
NETHER_ORES = [
    {"block": "minecraft:nether_quartz_ore", "min_y": 10, "max_y": 117, "size": 14, "attempts": 16},
    {"block": "minecraft:nether_gold_ore", "min_y": 10, "max_y": 117, "size": 10, "attempts": 10},
    {"block": "minecraft:ancient_debris", "min_y": 8, "max_y": 22, "size": 3, "attempts": 1},
    {"block": "minecraft:gravel", "min_y": 5, "max_y": 41, "size": 33, "attempts": 2},
]

def dump(name, obj):
    with open(os.path.join(DATA, name), "w") as f:
        json.dump(obj, f, indent=1)

if __name__ == "__main__":
    os.makedirs(DATA, exist_ok=True)
    for old in ["mobs.json"]:
        p = os.path.join(DATA, old)
        if os.path.exists(p):
            os.remove(p)
    dump("textures.json", {"textures": TEX})
    dump("blocks.json", {"blocks": BLOCKS})
    dump("items.json", {"items": ITEMS})
    dump("recipes.json", {"tags": TAGS, "recipes": RECIPES, "fuel": FUEL})
    dump("entities.json", {"entities": ENTITIES})
    dump("biomes.json", {"biomes": BIOMES})
    dump("sounds.json", {"sounds": SOUNDS})
    dump("tuning.json", TUNING)
    dump("worldgen.json", {"ores": ORES, "nether_ores": NETHER_ORES})
    print("blocks", len(BLOCKS), "items(non-block)", len(ITEMS), "recipes", len(RECIPES), "entities", len(ENTITIES),
          "biomes", len(BIOMES), "sounds", len(SOUNDS), "textures", len(TEX))
