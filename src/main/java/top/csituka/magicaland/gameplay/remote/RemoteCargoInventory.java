package top.csituka.magicaland.gameplay.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.slot.Slot;

public final class RemoteCargoInventory extends SimpleInventory {
    public static final int CAPACITY=RemoteCapabilities.CARGO_STACK_LIMIT, MAX_SLOTS=9;
    public static final int SETTLEMENT_SPAWN_LIMIT=64;
    private int unlocked=RemoteCapabilities.CARGO_SLOTS, selected;
    private final List<ItemStack> recovery=new ArrayList<>();
    public RemoteCargoInventory() { super(MAX_SLOTS); }
    public int unlockedSlots() { return unlocked; }
    public int selectedSlot() { return selected; }
    public ItemStack selectedStack() { return getStack(selected); }
    public ItemStack displayStack() {
        if (!selectedStack().isEmpty()) return selectedStack().copy();
        for (int slot=0;slot<MAX_SLOTS;slot++) if (!getStack(slot).isEmpty()) return getStack(slot).copy();
        for (ItemStack stack:recovery) if (!stack.isEmpty()) return stack.copy();
        return ItemStack.EMPTY;
    }
    public boolean select(int slot) {
        if (slot<0 || slot>=unlocked) return false;
        if (selected!=slot) { selected=slot; markDirty(); }
        return true;
    }
    public boolean unlock(int count) {
        if (count<1 || count>MAX_SLOTS) return false;
        for (int i=count;i<MAX_SLOTS;i++) if (!getStack(i).isEmpty()) return false;
        unlocked=count; selected=Math.min(selected,count-1); markDirty(); return true;
    }
    public static ItemStack carriedView(ItemStack source) { return source.copy(); }
    public boolean dropFrom(int slot,boolean wholeStack,Predicate<ItemStack> spawn) {
        if (slot<0 || slot>=unlocked || getStack(slot).isEmpty()) return false;
        ItemStack original=getStack(slot),dropped=original.copy();
        int count=wholeStack?original.getCount():1;
        dropped.setCount(count);
        if (!spawn.test(dropped)) return false;
        removeStack(slot,count); markDirty(); return true;
    }
    @Override public int getMaxCountPerStack() { return CAPACITY; }
    @Override public boolean isValid(int slot,ItemStack stack) { return slot>=0 && slot<unlocked; }
    @Override public boolean isEmpty() { return super.isEmpty() && recovery.isEmpty(); }
    @Override public ItemStack removeStack(int slot) {
        ItemStack removed=super.removeStack(slot);
        if (!removed.isEmpty()) markDirty();
        return removed;
    }
    @Override public ItemStack addStack(ItemStack source) {
        ItemStack remainder=source.copy();
        for (boolean empty:new boolean[]{false,true}) {
            for (int i=0;i<unlocked && !remainder.isEmpty();i++) {
                Slot slot=new Slot(this,i,0,0);
                if (slot.getStack().isEmpty()==empty) remainder=slot.insertStack(remainder);
            }
        }
        return remainder;
    }
    public boolean canLoad(ItemStack source) {
        if (source.isEmpty()) return true;
        for (int i=0;i<unlocked;i++) {
            ItemStack stack=getStack(i);
            if (stack.isEmpty() || ItemStack.canCombine(stack,source)
                    && stack.getCount()<Math.min(CAPACITY,stack.getMaxCount())) return true;
        }
        return false;
    }
    public int loadFrom(Inventory source,int sourceSlot) {
        ItemStack original=source.getStack(sourceSlot);
        if (original.isEmpty()) return 0;
        for (int offset=0;offset<unlocked;offset++) {
            int index=(selected+offset)%unlocked;
            ItemStack offered=original.copy();
            offered.setCount(Math.min(CAPACITY,Math.min(original.getCount(),original.getMaxCount())));
            int before=offered.getCount();
            ItemStack remainder=new Slot(this,index,0,0).insertStack(offered);
            int accepted=before-remainder.getCount();
            if (accepted>0) {
                source.removeStack(sourceSlot,accepted); source.markDirty(); select(index); return accepted;
            }
        }
        return 0;
    }
    public void returnTo(Inventory target,int slots) { returnTo(target,slots,-1); }
    public void returnTo(Inventory target,int slots,int preferred) {
        returnTo(target,slots,preferred,-1);
    }
    public void returnTo(Inventory target,int slots,int preferred,int sourceCargoSlot) {
        if (sourceCargoSlot>=0 && sourceCargoSlot<MAX_SLOTS)
            setStack(sourceCargoSlot,returnStack(getStack(sourceCargoSlot),target,slots,preferred));
        for (int i=0;i<MAX_SLOTS;i++) if (i!=sourceCargoSlot)
            setStack(i,returnStack(getStack(i),target,slots,preferred));
        for (int i=0;i<recovery.size();i++) recovery.set(i,returnStack(recovery.get(i),target,slots,-1));
        recovery.removeIf(ItemStack::isEmpty); markDirty();
    }
    private static ItemStack returnStack(ItemStack stack,Inventory target,int slots,int preferred) {
        ItemStack remainder=stack.copy();
        int limit=Math.min(slots,target.size());
        if (preferred>=0 && preferred<limit) remainder=new Slot(target,preferred,0,0).insertStack(remainder);
        for (boolean empty:new boolean[]{false,true}) {
            for (int index=0;index<limit && !remainder.isEmpty();index++) {
                Slot slot=new Slot(target,index,0,0);
                if (slot.getStack().isEmpty()==empty) remainder=slot.insertStack(remainder);
            }
        }
        return remainder;
    }
    public int dropOnDeath(Predicate<ItemStack> spawn) {
        return dropRemainder(stack -> net.minecraft.enchantment.EnchantmentHelper.hasVanishingCurse(stack) || spawn.test(stack));
    }
    public int dropRemainder(Predicate<ItemStack> spawn) {
        int attempts=0,dropped=0;
        for (int slot=0;slot<MAX_SLOTS && attempts<SETTLEMENT_SPAWN_LIMIT;slot++) {
            while (!getStack(slot).isEmpty() && attempts<SETTLEMENT_SPAWN_LIMIT) {
                ItemStack original=getStack(slot),offered=original.copy();
                int count=Math.min(original.getCount(),Math.min(CAPACITY,original.getMaxCount()));
                offered.setCount(count); attempts++;
                if (!spawn.test(offered)) break;
                removeStack(slot,count); markDirty(); dropped++;
            }
        }
        for (var entries=recovery.iterator();entries.hasNext() && attempts<SETTLEMENT_SPAWN_LIMIT;) {
            ItemStack original=entries.next();
            while (!original.isEmpty() && attempts<SETTLEMENT_SPAWN_LIMIT) {
                ItemStack offered=original.copy();
                int count=Math.min(original.getCount(),Math.min(CAPACITY,original.getMaxCount()));
                offered.setCount(count); attempts++;
                if (!spawn.test(offered)) break;
                original.decrement(count); markDirty(); dropped++;
            }
            if (original.isEmpty()) entries.remove();
        }
        return dropped;
    }
    public List<ItemStack> takeAll() {
        List<ItemStack> result=new ArrayList<>();
        for (int i=0;i<MAX_SLOTS;i++) { ItemStack stack=removeStack(i); if (!stack.isEmpty()) result.add(stack); }
        result.addAll(recovery); recovery.clear(); markDirty(); return result;
    }
    public void retain(ItemStack stack) {
        ItemStack remainder=addStack(stack);
        if (!remainder.isEmpty()) recovery.add(remainder);
        markDirty();
    }
    @Override public NbtList toNbtList() {
        NbtList items=new NbtList();
        for (int i=0;i<MAX_SLOTS;i++) if (!getStack(i).isEmpty()) {
            NbtCompound entry=getStack(i).writeNbt(new NbtCompound()); entry.putInt("Slot",i); items.add(entry);
        }
        return items;
    }
    @Override public void readNbtList(NbtList items) {
        clear(); recovery.clear();
        for (int i=0;i<items.size();i++) {
            NbtCompound entry=items.getCompound(i); ItemStack stack=ItemStack.fromNbt(entry);
            if (stack.isEmpty()) continue;
            if (entry.contains("Slot",NbtElement.NUMBER_TYPE)) {
                int index=entry.getInt("Slot");
                if (index>=0 && index<MAX_SLOTS && getStack(index).isEmpty()) {
                    int accepted=Math.min(stack.getCount(),Math.min(CAPACITY,stack.getMaxCount()));
                    setStack(index,stack.split(accepted)); unlocked=Math.max(unlocked,index+1);
                }
            } else stack=addStack(stack);
            if (!stack.isEmpty()) recovery.add(stack.copy());
        }
        markDirty();
    }
    public void readSaved(NbtCompound entry) {
        unlocked=Math.max(RemoteCapabilities.CARGO_SLOTS,
                Math.max(1,Math.min(MAX_SLOTS,entry.contains("Unlocked")?entry.getInt("Unlocked"):1)));
        readNbtList(entry.getList("Items",NbtElement.COMPOUND_TYPE));
        selected=Math.max(0,Math.min(unlocked-1,entry.getInt("Selected")));
        NbtList extra=entry.getList("Recovery",NbtElement.COMPOUND_TYPE);
        for (int i=0;i<extra.size();i++) { ItemStack stack=ItemStack.fromNbt(extra.getCompound(i)); if (!stack.isEmpty()) recovery.add(stack); }
    }
    public void writeSaved(NbtCompound entry) {
        entry.putInt("Unlocked",unlocked); entry.putInt("Selected",selected); entry.put("Items",toNbtList());
        NbtList extra=new NbtList();
        for (ItemStack stack:recovery) extra.add(stack.writeNbt(new NbtCompound()));
        if (!extra.isEmpty()) entry.put("Recovery",extra); else entry.remove("Recovery");
    }
}
