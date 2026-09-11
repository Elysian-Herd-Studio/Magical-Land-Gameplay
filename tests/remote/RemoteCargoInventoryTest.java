import java.util.UUID;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.text.Text;
import top.csituka.magicaland.gameplay.remote.RemoteCargoInventory;
import top.csituka.magicaland.gameplay.remote.RemoteCargoState;

public final class RemoteCargoInventoryTest {
    private static int checks;
    private static void check(boolean result) { checks++; if (!result) throw new AssertionError("check "+checks); }
    public static void main(String[] args) {
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        check(new RemoteCargoInventory().size()==9 && new RemoteCargoInventory().unlockedSlots()==9);
        check(RemoteCargoInventory.carriedView(new ItemStack(Items.STONE,8)).getCount()==8);
        check(RemoteCargoInventory.carriedView(new ItemStack(Items.DIAMOND_PICKAXE)).getCount()==1);
        for (var item : new net.minecraft.item.Item[] {Items.STONE,Items.ENDER_PEARL,Items.DIAMOND_PICKAXE}) {
            int limit=item.getMaxCount();
            for (int initial=0;initial<=limit;initial++) for (int incoming=1;incoming<=64;incoming++) {
                var cargo=new RemoteCargoInventory(); cargo.unlock(1);
                if (initial>0) cargo.addStack(new ItemStack(item,initial));
                var source=new ItemStack(item,incoming);
                var remainder=cargo.addStack(source);
                check(source.getCount()==incoming);
                check(cargo.getStack(0).getCount()==Math.min(limit,initial+incoming));
                check(cargo.getStack(0).getCount()+remainder.getCount()==initial+incoming);
                var bag=new SimpleInventory(1);
                bag.setStack(0,new ItemStack(item,item.getMaxCount()-1));
                int before=bag.getStack(0).getCount()+cargo.getStack(0).getCount();
                cargo.returnTo(bag,1);
                check(before==bag.getStack(0).getCount()+cargo.getStack(0).getCount());
                check(bag.getStack(0).getCount()<=item.getMaxCount());
            }
        }
        var cargo=new RemoteCargoInventory(); cargo.unlock(1); cargo.addStack(new ItemStack(Items.STONE,5));
        check(cargo.addStack(new ItemStack(Items.DIRT,8)).getCount()==8);
        var named=new ItemStack(Items.STONE,3); named.setCustomName(Text.literal("Different"));
        check(cargo.addStack(named).getCount()==3);
        var full=new SimpleInventory(new ItemStack(Items.STONE,64)); cargo.returnTo(full,1);
        check(cargo.getStack(0).getCount()==5 && full.getStack(0).getCount()==64);
        var empty=new SimpleInventory(2); cargo.returnTo(empty,2);
        check(cargo.isEmpty() && empty.getStack(0).getCount()==5);
        var bag=new SimpleInventory(ItemStack.EMPTY,new ItemStack(Items.STONE,60));
        cargo.addStack(new ItemStack(Items.STONE,8)); cargo.returnTo(bag,2);
        check(bag.getStack(1).getCount()==64 && bag.getStack(0).getCount()==4 && cargo.isEmpty());

        var state=new RemoteCargoState(); UUID owner=UUID.randomUUID(),other=UUID.randomUUID();
        state.inventory(owner).addStack(named); check(state.isDirty());
        var restored=RemoteCargoState.read(state.writeNbt(new NbtCompound()));
        check(ItemStack.areEqual(restored.inventory(owner).getStack(0),named));
        check(restored.inventory(other).isEmpty());
        restored.setDirty(false); restored.inventory(owner).removeStack(0);
        check(restored.isDirty());
        var again=RemoteCargoState.read(restored.writeNbt(new NbtCompound()));
        check(again.inventory(owner).isEmpty());
        realTransfers(); multiSlot(); fullCargo(); legacyMigration(); drops(); settlement();
        System.out.println("PASS RemoteCargoInventoryTest: "+checks+" checks");
    }
    private static void realTransfers() {
        var cargo=new RemoteCargoInventory(); cargo.unlock(1);
        var body=new SimpleInventory(new ItemStack(Items.STONE,64));
        check(cargo.loadFrom(body,0)==64);
        check(body.getStack(0).isEmpty() && cargo.selectedStack().getCount()==64);
        check(!cargo.canLoad(new ItemStack(Items.DIRT)) && cargo.canLoad(ItemStack.EMPTY));
        cargo.returnTo(body,1,0); cargo.returnTo(body,1,0);
        check(body.getStack(0).getCount()==64 && cargo.isEmpty());
        ItemStack tool=new ItemStack(Items.DIAMOND_PICKAXE);
        tool.addEnchantment(Enchantments.EFFICIENCY,3); tool.setDamage(29); tool.setCustomName(Text.literal("Remote tool"));
        var tools=new SimpleInventory(tool.copy());
        check(cargo.loadFrom(tools,0)==1 && tools.getStack(0).isEmpty());
        check(ItemStack.areEqual(cargo.selectedStack(),tool));
        cargo.selectedStack().setDamage(30); cargo.markDirty();
        check(tool.getDamage()==29 && tools.getStack(0).isEmpty());
        cargo.returnTo(tools,1,0);
        check(tools.getStack(0).getDamage()==30 && tools.getStack(0).hasEnchantments());
        cargo.loadFrom(tools,0); cargo.selectedStack().decrement(1); cargo.markDirty();
        check(cargo.selectedStack().isEmpty() && tools.getStack(0).isEmpty());
        check(cargo.addStack(new ItemStack(Items.COBBLESTONE,8)).isEmpty());
        check(cargo.takeAll().get(0).getCount()==8 && cargo.takeAll().isEmpty());
        var mixed=new RemoteCargoInventory(); mixed.addStack(new ItemStack(Items.STONE,64)); mixed.select(8);
        var originalSlot=new SimpleInventory(ItemStack.EMPTY,tool.copy(),ItemStack.EMPTY);
        check(mixed.loadFrom(originalSlot,1)==1 && mixed.selectedSlot()==8);
        mixed.select(0); mixed.returnTo(originalSlot,3,1,8); mixed.returnTo(originalSlot,3,1,8);
        check(ItemStack.areEqual(originalSlot.getStack(1),tool));
        check(mixed.isEmpty() && originalSlot.getStack(0).getCount()==64);
    }
    private static void multiSlot() {
        var inventory=new RemoteCargoInventory();
        check(inventory.select(8) && !inventory.select(9) && !inventory.unlock(0) && !inventory.unlock(10));
        check(inventory.unlock(3));
        check(inventory.addStack(new ItemStack(Items.STONE,200)).getCount()==8);
        check(inventory.getStack(0).getCount()==64 && inventory.getStack(1).getCount()==64 && inventory.getStack(2).getCount()==64);
        check(!inventory.unlock(2));
        inventory.removeStack(1); inventory.removeStack(2); check(inventory.select(2));
        ItemStack named=new ItemStack(Items.IRON_SWORD); named.addEnchantment(Enchantments.UNBREAKING,2); named.setDamage(7);
        inventory.setStack(2,named);
        NbtCompound saved=new NbtCompound(); inventory.writeSaved(saved);
        var restored=new RemoteCargoInventory(); restored.readSaved(saved);
        check(restored.unlockedSlots()==9 && restored.selectedSlot()==2);
        check(restored.getStack(1).isEmpty() && ItemStack.areEqual(restored.getStack(2),named));
        restored.takeAll(); check(restored.unlock(1) && restored.selectedSlot()==0);
        var state=new RemoteCargoState(); UUID owner=UUID.randomUUID(); state.inventory(owner).unlock(9);
        state.inventory(owner).select(8);
        var roundTrip=RemoteCargoState.read(state.writeNbt(new NbtCompound()));
        check(roundTrip.inventory(owner).unlockedSlots()==9 && roundTrip.inventory(owner).isEmpty()
                && roundTrip.inventory(owner).selectedSlot()==8);
    }
    private static void fullCargo() {
        for (var item:new net.minecraft.item.Item[]{Items.STONE,Items.ENDER_PEARL,Items.DIAMOND_PICKAXE}) {
            var cargo=new RemoteCargoInventory(); int limit=item.getMaxCount();
            ItemStack original=new ItemStack(item,limit*9+7); original.setCustomName(Text.literal("Full tier"));
            ItemStack remainder=cargo.addStack(original);
            check(remainder.getCount()==7 && original.getCount()==limit*9+7);
            for (int slot=0;slot<9;slot++) {
                check(cargo.select(slot) && cargo.selectedStack().getCount()==limit);
                check(ItemStack.canCombine(cargo.selectedStack(),original));
            }
            check(!cargo.canLoad(original));
            NbtCompound saved=new NbtCompound(); cargo.writeSaved(saved);
            var restored=new RemoteCargoInventory(); restored.readSaved(saved);
            check(restored.selectedSlot()==8 && restored.unlockedSlots()==9);
            var bag=new SimpleInventory(9); restored.returnTo(bag,9,5); restored.returnTo(bag,9,5);
            int total=0; for (int slot=0;slot<9;slot++) { total+=bag.getStack(slot).getCount(); check(bag.getStack(slot).getCount()<=limit); }
            check(restored.isEmpty() && total==limit*9);
        }
    }
    private static void legacyMigration() {
        UUID owner=UUID.randomUUID();
        ItemStack old=new ItemStack(Items.STONE,8); old.setCustomName(Text.literal("Legacy"));
        NbtCompound player=new NbtCompound(); player.putUuid("Owner",owner);
        player.put("Items",new SimpleInventory(old).toNbtList());
        NbtList players=new NbtList(); players.add(player); NbtCompound legacy=new NbtCompound(); legacy.put("Players",players);
        var migrated=RemoteCargoState.read(legacy); var inventory=migrated.inventory(owner);
        check(inventory.unlockedSlots()==9 && inventory.selectedSlot()==0 && ItemStack.areEqual(inventory.getStack(0),old));
        NbtCompound version2=migrated.writeNbt(new NbtCompound()); check(version2.getInt("Version")==2);
        var twice=RemoteCargoState.read(version2); check(ItemStack.areEqual(twice.inventory(owner).getStack(0),old));
        check(twice.inventory(owner).takeAll().size()==1 && twice.inventory(owner).takeAll().isEmpty());
        player.put("Items",new SimpleInventory(new ItemStack(Items.STONE,64),new ItemStack(Items.DIRT,5)).toNbtList());
        var surplus=RemoteCargoState.read(legacy); var bag=new SimpleInventory(3);
        surplus.inventory(owner).returnTo(bag,3); surplus.inventory(owner).returnTo(bag,3);
        check(surplus.inventory(owner).isEmpty());
        int total=0; for (int i=0;i<bag.size();i++) total+=bag.getStack(i).getCount(); check(total==69);
        NbtCompound sparse=new NbtCompound(); sparse.putInt("Unlocked",1); sparse.putInt("Selected",8);
        ItemStack enchanted=new ItemStack(Items.DIAMOND_PICKAXE); enchanted.setDamage(31);
        enchanted.addEnchantment(Enchantments.UNBREAKING,3); enchanted.setCustomName(Text.literal("Saved tool"));
        NbtCompound slot=enchanted.writeNbt(new NbtCompound()); slot.putInt("Slot",8);
        NbtList entries=new NbtList(); entries.add(slot); sparse.put("Items",entries);
        NbtList recovery=new NbtList(); recovery.add(old.writeNbt(new NbtCompound())); sparse.put("Recovery",recovery);
        var recovered=new RemoteCargoInventory(); recovered.readSaved(sparse);
        check(recovered.unlockedSlots()==9 && recovered.selectedSlot()==8 && ItemStack.areEqual(recovered.getStack(8),enchanted));
        NbtCompound resaved=new NbtCompound(); recovered.writeSaved(resaved);
        var recoveredAgain=new RemoteCargoInventory(); recoveredAgain.readSaved(resaved);
        var settlement=new SimpleInventory(2); recoveredAgain.returnTo(settlement,2,0,8);
        check(ItemStack.areEqual(settlement.getStack(0),enchanted) && ItemStack.areEqual(settlement.getStack(1),old));
        check(recoveredAgain.isEmpty() && recoveredAgain.takeAll().isEmpty());
    }
    private static void drops() {
        for (var item:new net.minecraft.item.Item[]{Items.STONE,Items.ENDER_PEARL,Items.DIAMOND_PICKAXE}) {
            for (int count=1;count<=item.getMaxCount();count++) for (boolean all:new boolean[]{false,true}) {
                for (int slot=0;slot<9;slot++) {
                    var inventory=new RemoteCargoInventory(); inventory.unlock(9);
                    ItemStack original=new ItemStack(item,count); original.setCustomName(Text.literal("Dropped cargo"));
                    if (original.isDamageable()) { original.setDamage(17); original.addEnchantment(Enchantments.UNBREAKING,2); }
                    inventory.setStack(slot,original.copy()); inventory.select((slot+1)%9);
                    int amount=all?count:1;
                    check(!inventory.dropFrom(slot,all,stack -> {
                        check(stack.getCount()==amount && ItemStack.canCombine(original,stack));
                        stack.setCount(0); return false;
                    }));
                    check(ItemStack.areEqual(inventory.getStack(slot),original));
                    ItemStack[] spawned={ItemStack.EMPTY};
                    check(inventory.dropFrom(slot,all,stack -> { spawned[0]=stack; return true; }));
                    check(spawned[0].getCount()==amount && ItemStack.canCombine(original,spawned[0]));
                    check(inventory.getStack(slot).getCount()+spawned[0].getCount()==count);
                    check(inventory.selectedSlot()==(slot+1)%9 && inventory.selectedStack().isEmpty());
                    check(original.getCount()==count);
                    NbtCompound saved=new NbtCompound(); inventory.writeSaved(saved);
                    var restored=new RemoteCargoInventory(); restored.readSaved(saved);
                    check(restored.getStack(slot).getCount()==count-amount);
                }
            }
        }
        var inventory=new RemoteCargoInventory(); inventory.setStack(0,new ItemStack(Items.STONE,8));
        for (int slot:new int[]{-1,1,8,9}) check(!inventory.dropFrom(slot,true,stack -> { throw new AssertionError("invalid slot spawned"); }));
        try { inventory.dropFrom(0,true,stack -> { throw new IllegalStateException("spawn failed"); }); }
        catch (IllegalStateException expected) { check(inventory.getStack(0).getCount()==8); }
        var state=new RemoteCargoState(); var owner=UUID.randomUUID(); state.inventory(owner).setStack(0,new ItemStack(Items.STONE,2));
        state.setDirty(false); check(state.inventory(owner).dropFrom(0,false,stack -> true) && state.isDirty());
        state.setDirty(false); check(!state.inventory(owner).dropFrom(0,true,stack -> false) && !state.isDirty());
        check(state.inventory(owner).dropFrom(0,true,stack -> true));
        check(!state.inventory(owner).dropFrom(0,true,stack -> { throw new AssertionError("empty slot spawned"); }));
    }
    private static ItemStack settlementStack(net.minecraft.item.Item item,int count,String name) {
        ItemStack stack=new ItemStack(item,count); stack.setCustomName(Text.literal(name));
        NbtCompound custom=new NbtCompound(); custom.putString("Owner","Original owner");
        custom.putIntArray("History",new int[]{7,19,41}); stack.getOrCreateNbt().put("CustomCargo",custom);
        if (stack.isDamageable()) { stack.setDamage(23); stack.addEnchantment(Enchantments.UNBREAKING,3); }
        return stack;
    }
    private static void settlement() {
        ItemStack tool=settlementStack(Items.DIAMOND_PICKAXE,1,"Returning tool");
        ItemStack stone=settlementStack(Items.STONE,20,"Returning stone");
        ItemStack dirt=settlementStack(Items.DIRT,7,"Returning dirt");
        var cargo=new RemoteCargoInventory(); cargo.setStack(0,stone.copy());
        cargo.setStack(3,dirt.copy()); cargo.setStack(8,tool.copy()); cargo.select(4);
        check(cargo.selectedStack().isEmpty() && !cargo.isEmpty());
        ItemStack display=cargo.displayStack(); check(ItemStack.areEqual(display,stone));
        display.getOrCreateNbt().getCompound("CustomCargo").putString("Owner","Display mutation"); display.setCount(0);
        check(ItemStack.areEqual(cargo.getStack(0),stone));
        cargo.select(8); display=cargo.displayStack(); check(ItemStack.areEqual(display,tool));
        display.setDamage(99); check(ItemStack.areEqual(cargo.getStack(8),tool)); cargo.select(4);
        ItemStack changed=settlementStack(Items.IRON_SWORD,1,"Changed body slot");
        ItemStack almostFull=stone.copy(); almostFull.setCount(60);
        var bag=new SimpleInventory(changed.copy(),almostFull,ItemStack.EMPTY);
        cargo.returnTo(bag,3,0,8);
        check(ItemStack.areEqual(bag.getStack(0),changed) && ItemStack.areEqual(bag.getStack(2),tool));
        check(bag.getStack(1).getCount()==64 && ItemStack.canCombine(bag.getStack(1),stone));
        check(cargo.getStack(0).getCount()==16 && ItemStack.areEqual(cargo.getStack(3),dirt));
        var spawned=new java.util.ArrayList<ItemStack>();
        check(cargo.dropRemainder(stack -> { spawned.add(stack); return true; })==2);
        check(cargo.isEmpty() && cargo.displayStack().isEmpty() && spawned.get(0).getCount()==16 && ItemStack.canCombine(spawned.get(0),stone));
        check(ItemStack.areEqual(spawned.get(1),dirt));
        check(cargo.dropRemainder(stack -> { throw new AssertionError("settled cargo spawned again"); })==0);
        cargo.returnTo(bag,3,0,8);
        check(bag.getStack(1).getCount()==64 && ItemStack.areEqual(bag.getStack(0),changed));

        var fullBag=new SimpleInventory(new ItemStack(Items.STONE,64));
        var multi=new RemoteCargoInventory(); var originals=new java.util.ArrayList<ItemStack>();
        for (int slot=0;slot<9;slot++) {
            ItemStack stack=settlementStack(slot%2==0?Items.STONE:Items.DIAMOND_PICKAXE,slot%2==0?slot+1:1,"Cargo "+slot);
            originals.add(stack.copy()); multi.setStack(slot,stack);
        }
        multi.returnTo(fullBag,1,0,8);
        check(fullBag.getStack(0).getCount()==64);
        int[] attempts={0};
        check(multi.dropRemainder(stack -> {
            int slot=attempts[0]++;
            check(ItemStack.areEqual(stack,originals.get(slot)));
            if (slot%2!=0) return true;
            stack.getOrCreateNbt().getCompound("CustomCargo").putString("Owner","Mutation");
            stack.setCount(0); return false;
        })==4);
        check(attempts[0]==9);
        for (int slot=0;slot<9;slot++) check(slot%2==0?ItemStack.areEqual(multi.getStack(slot),originals.get(slot)):multi.getStack(slot).isEmpty());
        spawned.clear();
        check(multi.dropRemainder(stack -> { spawned.add(stack); return true; })==5 && multi.isEmpty());
        for (int index=0;index<spawned.size();index++) check(ItemStack.areEqual(spawned.get(index),originals.get(index*2)));

        NbtList extras=new NbtList();
        ItemStack[] recovery={settlementStack(Items.STONE,65,"Recovery stone"),
                settlementStack(Items.ENDER_PEARL,33,"Recovery pearl"),settlementStack(Items.DIAMOND_PICKAXE,2,"Recovery tools")};
        for (ItemStack stack:recovery) extras.add(stack.writeNbt(new NbtCompound()));
        NbtCompound saved=new NbtCompound(); saved.put("Recovery",extras);
        var recovered=new RemoteCargoInventory(); recovered.readSaved(saved);
        check(recovered.selectedStack().isEmpty() && !recovered.isEmpty());
        display=recovered.displayStack(); check(ItemStack.areEqual(display,recovery[0]));
        display.getOrCreateNbt().getCompound("CustomCargo").putString("Owner","Display mutation"); display.setCount(0);
        check(ItemStack.areEqual(recovered.displayStack(),recovery[0]));
        NbtCompound before=new NbtCompound(); recovered.writeSaved(before);
        check(recovered.dropRemainder(stack -> {
            stack.getOrCreateNbt().getCompound("CustomCargo").putString("Owner","Mutation");
            stack.setCount(0); return false;
        })==0);
        NbtCompound after=new NbtCompound(); recovered.writeSaved(after); check(before.equals(after));
        recovered.returnTo(fullBag,1,0,8); spawned.clear();
        check(recovered.dropRemainder(stack -> { spawned.add(stack); return true; })==7 && recovered.isEmpty());
        int total=0;
        for (ItemStack stack:spawned) {
            total+=stack.getCount(); check(stack.getCount()<=stack.getMaxCount());
            check(java.util.Arrays.stream(recovery).anyMatch(original -> ItemStack.canCombine(original,stack)));
        }
        check(total==100 && recovered.dropRemainder(stack -> { throw new AssertionError("recovery spawned again"); })==0);
        recovered.writeSaved(after); check(!after.contains("Recovery") && recovered.displayStack().isEmpty());
        var emptyRecovery=new RemoteCargoInventory(); emptyRecovery.readSaved(after); check(emptyRecovery.isEmpty());
        var recoveryRetry=new RemoteCargoInventory(); recoveryRetry.readSaved(saved); attempts[0]=0;
        check(recoveryRetry.dropRemainder(stack -> {
            if (attempts[0]++==0) { check(stack.getCount()==64 && ItemStack.canCombine(stack,recovery[0])); return true; }
            stack.getOrCreateNbt().getCompound("CustomCargo").putString("Owner","Failed retry mutation");
            return false;
        })==1 && attempts[0]==4);
        NbtCompound pending=new NbtCompound(); recoveryRetry.writeSaved(pending);
        var pendingReload=new RemoteCargoInventory(); pendingReload.readSaved(pending); spawned.clear();
        check(pendingReload.dropRemainder(stack -> { spawned.add(stack); return true; })==6 && pendingReload.isEmpty());
        total=0;
        for (ItemStack stack:spawned) {
            total+=stack.getCount(); check(java.util.Arrays.stream(recovery).anyMatch(original -> ItemStack.canCombine(original,stack)));
        }
        check(total==36);

        var bounded=new RemoteCargoInventory(); bounded.unlock(1); bounded.setStack(0,new ItemStack(Items.STONE,64));
        bounded.retain(new ItemStack(Items.DIAMOND_PICKAXE,RemoteCargoInventory.SETTLEMENT_SPAWN_LIMIT+2));
        attempts[0]=0;
        check(bounded.dropRemainder(stack -> { attempts[0]++; return true; })==RemoteCargoInventory.SETTLEMENT_SPAWN_LIMIT);
        check(attempts[0]==RemoteCargoInventory.SETTLEMENT_SPAWN_LIMIT && !bounded.isEmpty());
        check(bounded.selectedStack().isEmpty() && bounded.displayStack().isOf(Items.DIAMOND_PICKAXE));
        check(bounded.dropRemainder(stack -> true)==3 && bounded.isEmpty());

        var state=new RemoteCargoState(); UUID owner=UUID.randomUUID(); var tracked=state.inventory(owner);
        tracked.setStack(0,stone.copy()); tracked.setStack(8,tool.copy()); state.setDirty(false);
        check(tracked.dropRemainder(stack -> false)==0 && !state.isDirty());
        attempts[0]=0;
        try {
            tracked.dropRemainder(stack -> {
                if (attempts[0]++==0) return true;
                stack.getOrCreateNbt().getCompound("CustomCargo").putString("Owner","Mutation");
                throw new IllegalStateException("spawn failed");
            });
            throw new AssertionError("spawn exception swallowed");
        } catch (IllegalStateException expected) {
            check(state.isDirty() && tracked.getStack(0).isEmpty() && ItemStack.areEqual(tracked.getStack(8),tool));
        }
        var retry=RemoteCargoState.read(state.writeNbt(new NbtCompound())).inventory(owner);
        check(retry.dropRemainder(stack -> { check(ItemStack.areEqual(stack,tool)); return true; })==1 && retry.isEmpty());
    }
}
