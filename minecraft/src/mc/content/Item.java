package mc.content;

import java.util.*;

/** Item definition (Section 7.1). Block items are generated for every block with "item": true. */
public final class Item {
    public final String id, name;
    public final int index;
    public final int maxStack, durability;
    public final String toolKind; // pickaxe/axe/shovel/sword/hoe/shears or null
    public final int toolTier;
    public final double toolSpeed, attackDamage, attackSpeed;
    public final int armorSlot, armorPoints;
    public final double armorToughness;
    public final int foodHunger;
    public final double foodSaturation;
    public final List<Map<String, Object>> foodEffects;
    public final int placesState; // block state placed on use, -1 if none
    public final String use, spawns;
    public final String iconShape;
    public final String[] iconColors;
    public final Block block; // non-null for block items
    public int eatTicks = 32;

    Item(int index, String id, String name, int maxStack, int durability, String toolKind, int toolTier, double toolSpeed,
         double attackDamage, double attackSpeed, int armorSlot, int armorPoints, double armorToughness, int foodHunger,
         double foodSaturation, List<Map<String, Object>> foodEffects, int placesState, String use, String spawns,
         String iconShape, String[] iconColors, Block block) {
        this.index = index; this.id = id; this.name = name; this.maxStack = maxStack; this.durability = durability;
        this.toolKind = toolKind; this.toolTier = toolTier; this.toolSpeed = toolSpeed; this.attackDamage = attackDamage;
        this.attackSpeed = attackSpeed; this.armorSlot = armorSlot; this.armorPoints = armorPoints; this.armorToughness = armorToughness;
        this.foodHunger = foodHunger; this.foodSaturation = foodSaturation; this.foodEffects = foodEffects;
        this.placesState = placesState; this.use = use; this.spawns = spawns; this.iconShape = iconShape; this.iconColors = iconColors;
        this.block = block;
    }

    public boolean isFood() { return foodHunger > 0; }

    public boolean isTool() { return toolKind != null; }

    public boolean isArmor() { return armorSlot >= 0; }

    @Override public String toString() { return id; }
}
