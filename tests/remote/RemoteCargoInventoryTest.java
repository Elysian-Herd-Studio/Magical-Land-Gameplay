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
        check(new RemoteCargoInventory().size()==9 && new RemoteCargoInventory().unlockedSlots()==1);
        check(RemoteCargoInventory.carriedView(new ItemStack(Items.STONE,8)).getCount()==8);
        check(RemoteCargoInventory.carriedView(new ItemStack(Items.DIAMOND_PICKAXE)).getCount()==1);
        for (var item : new net.minecraft.item.Item[] {Items.STONE,Items.ENDER_PEARL,Items.DIAMOND_PICKAXE}) {
            int limit=Math.min(8,item.getMaxCount());
            for (int initial=0;initial<=limit;initial++) for (int incoming=1;incoming<=64;incoming++) {
                var cargo=new RemoteCargoInventory();
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
        var cargo=new RemoteCargoInventory(); cargo.addStack(new ItemStack(Items.STONE,5));
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
        realTransfers(); multiSlot(); legacyMigration(); drops();
        System.out.println("PASS RemoteCargoInventoryTest: "+checks+" checks");
    }
    private static void realTransfers() {
        var cargo=new RemoteCargoInventory();
        var body=new SimpleInventory(new ItemStack(Items.STONE,64));
        check(cargo.loadFrom(body,0)==8);
        check(body.getStack(0).getCount()==56 && cargo.selectedStack().getCount()==8);
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
    }
    private static void multiSlot() {
        var inventory=new RemoteCargoInventory();
        check(!inventory.select(1) && !inventory.unlock(0) && !inventory.unlock(10));
        check(inventory.unlock(3));
        check(inventory.addStack(new ItemStack(Items.STONE,64)).getCount()==40);
        check(inventory.getStack(0).getCount()==8 && inventory.getStack(1).getCount()==8 && inventory.getStack(2).getCount()==8);
        check(!inventory.unlock(2));
        inventory.removeStack(1); inventory.removeStack(2); check(inventory.select(2));
        ItemStack named=new ItemStack(Items.IRON_SWORD); named.addEnchantment(Enchantments.UNBREAKING,2); named.setDamage(7);
        inventory.setStack(2,named);
        NbtCompound saved=new NbtCompound(); inventory.writeSaved(saved);
        var restored=new RemoteCargoInventory(); restored.readSaved(saved);
        check(restored.unlockedSlots()==3 && restored.selectedSlot()==2);
        check(restored.getStack(1).isEmpty() && ItemStack.areEqual(restored.getStack(2),named));
        restored.takeAll(); check(restored.unlock(1) && restored.selectedSlot()==0);
        var state=new RemoteCargoState(); UUID owner=UUID.randomUUID(); state.inventory(owner).unlock(9);
        var roundTrip=RemoteCargoState.read(state.writeNbt(new NbtCompound()));
        check(roundTrip.inventory(owner).unlockedSlots()==9 && roundTrip.inventory(owner).isEmpty());
    }
    private static void legacyMigration() {
        UUID owner=UUID.randomUUID();
        ItemStack old=new ItemStack(Items.STONE,8); old.setCustomName(Text.literal("Legacy"));
        NbtCompound player=new NbtCompound(); player.putUuid("Owner",owner);
        player.put("Items",new SimpleInventory(old).toNbtList());
        NbtList players=new NbtList(); players.add(player); NbtCompound legacy=new NbtCompound(); legacy.put("Players",players);
        var migrated=RemoteCargoState.read(legacy); var inventory=migrated.inventory(owner);
        check(inventory.unlockedSlots()==1 && inventory.selectedSlot()==0 && ItemStack.areEqual(inventory.getStack(0),old));
        NbtCompound version2=migrated.writeNbt(new NbtCompound()); check(version2.getInt("Version")==2);
        var twice=RemoteCargoState.read(version2); check(ItemStack.areEqual(twice.inventory(owner).getStack(0),old));
        check(twice.inventory(owner).takeAll().size()==1 && twice.inventory(owner).takeAll().isEmpty());
        player.put("Items",new SimpleInventory(new ItemStack(Items.STONE,64),new ItemStack(Items.DIRT,5)).toNbtList());
        var surplus=RemoteCargoState.read(legacy); var bag=new SimpleInventory(3);
        surplus.inventory(owner).returnTo(bag,3); surplus.inventory(owner).returnTo(bag,3);
        check(surplus.inventory(owner).isEmpty());
        int total=0; for (int i=0;i<bag.size();i++) total+=bag.getStack(i).getCount(); check(total==69);
    }
    private static void drops() {
        for (var item:new net.minecraft.item.Item[]{Items.STONE,Items.ENDER_PEARL,Items.DIAMOND_PICKAXE}) {
            for (int count=1;count<=Math.min(8,item.getMaxCount());count++) for (boolean all:new boolean[]{false,true}) {
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
}
