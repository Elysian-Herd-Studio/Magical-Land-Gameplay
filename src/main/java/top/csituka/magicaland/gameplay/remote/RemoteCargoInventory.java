package top.csituka.magicaland.gameplay.remote;

import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

public final class RemoteCargoInventory extends SimpleInventory {
    public static final int CAPACITY = 8;
    public RemoteCargoInventory() { super(1); }
    public static ItemStack carriedView(ItemStack source) {
        ItemStack shown=source.copy();
        shown.setCount(Math.min(source.getCount(),Math.min(CAPACITY,source.getMaxCount())));
        return shown;
    }
    @Override public int getMaxCountPerStack() { return CAPACITY; }
    @Override public ItemStack removeStack(int slot) {
        ItemStack removed=super.removeStack(slot);
        if (!removed.isEmpty()) markDirty();
        return removed;
    }

    @Override public ItemStack addStack(ItemStack source) {
        if (source.isEmpty()) return ItemStack.EMPTY;
        // 原版 SimpleInventory 的空槽路径会截断超限堆叠，先限制本次投入量。
        ItemStack offered=source.copy();
        offered.setCount(Math.min(source.getCount(),Math.min(CAPACITY,source.getMaxCount())));
        int offeredCount=offered.getCount();
        ItemStack refused=super.addStack(offered);
        ItemStack remainder=source.copy();
        remainder.decrement(offeredCount-refused.getCount());
        return remainder;
    }

    public void returnTo(Inventory target, int slots) {
        ItemStack remainder=getStack(0).copy();
        // 先补已有堆叠，再占空槽；Slot 保留原版物品/NBT/数量规则。
        for (boolean empty : new boolean[] {false,true}) {
            for (int index=0;index<Math.min(slots,target.size()) && !remainder.isEmpty();index++) {
                Slot slot=new Slot(target,index,0,0);
                if (slot.getStack().isEmpty()==empty) remainder=slot.insertStack(remainder);
            }
        }
        setStack(0,remainder);
    }
}
