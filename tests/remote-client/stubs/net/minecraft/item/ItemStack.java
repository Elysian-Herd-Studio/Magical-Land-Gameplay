package net.minecraft.item;
public final class ItemStack {
    public static final ItemStack EMPTY = new ItemStack(null, 0);
    private final Item item;
    private int count;
    public ItemStack(Item item, int count) { this.item = item; this.count = count; }
    public Item getItem() { return item; }
    public int getCount() { return count; }
    public boolean isEmpty() { return item == null || count <= 0; }
    public void setCount(int value) { count = value; }
    public ItemStack copy() { return isEmpty() ? EMPTY : new ItemStack(item, count); }
}
