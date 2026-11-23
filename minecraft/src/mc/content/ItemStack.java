package mc.content;

/** Mutable stack of items. EMPTY is represented by null in containers. */
public final class ItemStack {
    public final Item item;
    public int count;
    public int damage;

    public ItemStack(Item item, int count) { this(item, count, 0); }

    public ItemStack(Item item, int count, int damage) {
        this.item = item;
        this.count = count;
        this.damage = damage;
    }

    public static ItemStack of(String id, int count) {
        Item it = Items.get(id);
        return it == null ? null : new ItemStack(it, count);
    }

    public ItemStack copy() { return new ItemStack(item, count, damage); }

    public ItemStack copy(int n) { return new ItemStack(item, n, damage); }

    public int max() { return item.maxStack; }

    public boolean canStack(ItemStack o) {
        return o != null && o.item == item && damage == 0 && o.damage == 0 && item.maxStack > 1;
    }

    public static boolean isEmpty(ItemStack s) { return s == null || s.count <= 0; }

    @Override public String toString() { return item.id + " x" + count + (damage > 0 ? " dmg" + damage : ""); }
}
