import java.util.UUID;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import top.csituka.magicaland.gameplay.remote.RemoteCargoInventory;
import top.csituka.magicaland.gameplay.remote.RemoteCargoState;

public final class RemoteCargoInventoryTest {
    private static int checks;
    private static void check(boolean result) { checks++; if (!result) throw new AssertionError("check "+checks); }
    public static void main(String[] args) {
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        check(RemoteCargoInventory.carriedView(new ItemStack(Items.STONE,64)).getCount()==8);
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
        System.out.println("PASS RemoteCargoInventoryTest: "+checks+" checks");
    }
}
