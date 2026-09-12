import java.util.ArrayList;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import top.csituka.magicaland.gameplay.remote.RemoteCargoInventory;

public final class RemoteDeathDropTest {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        var cargo=new RemoteCargoInventory();
        var tool=new ItemStack(Items.DIAMOND_PICKAXE);
        tool.setDamage(73); tool.setCustomName(Text.literal("回航工具")); tool.addEnchantment(Enchantments.EFFICIENCY,3);
        var cursed=new ItemStack(Items.IRON_SWORD); cursed.addEnchantment(Enchantments.VANISHING_CURSE,1);
        cargo.setStack(0,tool.copy()); cargo.setStack(1,cursed);
        cargo.setStack(2,new ItemStack(Items.STONE,64));
        check(cargo.dropOnDeath(stack -> false)==1,"vanishing item removed even when world rejects all drops");
        check(ItemStack.areEqual(tool,cargo.getStack(0)),"failed spawn preserves name enchantments and durability");
        check(cargo.getStack(1).isEmpty() && cargo.getStack(2).getCount()==64,"failed stack retained and cursed item not retained");
        var saved=new NbtCompound(); cargo.writeSaved(saved);
        var recovered=new RemoteCargoInventory(); recovered.readSaved(saved);
        check(ItemStack.areEqual(tool,recovered.getStack(0)),"failed death drop survives persistence");
        var spawned=new ArrayList<ItemStack>();
        check(recovered.dropOnDeath(stack -> {spawned.add(stack);return true;})==2,"retry transfers exactly remaining stacks");
        check(recovered.isEmpty() && spawned.size()==2 && ItemStack.areEqual(tool,spawned.get(0)),"successful spawn removes cargo once");
        check(recovered.dropOnDeath(stack -> {throw new AssertionError("duplicate drop");})==0,"second retry does not duplicate");
        System.out.println("PASS RemoteDeathDropTest: "+checks+" checks");
    }
    private static void check(boolean ok,String label) { checks++; if (!ok) throw new AssertionError(label); }
}
