import java.util.UUID;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import top.csituka.magicaland.gameplay.remote.RemoteAttributes;
import top.csituka.magicaland.gameplay.remote.RemoteCombat;

public final class RemoteAttributesTest {
    private static int checks;
    private static void near(double actual,double expected) { checks++; if (!Double.isFinite(actual) || Math.abs(actual-expected)>1e-6) throw new AssertionError(actual+" != "+expected); }
    private static void check(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        for (var attribute:new net.minecraft.entity.attribute.EntityAttribute[]{EntityAttributes.GENERIC_ATTACK_DAMAGE,EntityAttributes.GENERIC_ATTACK_SPEED}) {
            var source=new ItemStack(Items.DIAMOND_SWORD); var remote=new ItemStack(Items.WOODEN_AXE);
            var original=new EntityAttributeInstance(attribute,changed -> {}); original.setBaseValue(attribute.getDefaultValue());
            original.addTemporaryModifier(new EntityAttributeModifier(UUID.randomUUID(),"status",2,EntityAttributeModifier.Operation.ADDITION));
            original.addTemporaryModifier(new EntityAttributeModifier(UUID.randomUUID(),"other",.1,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
            var expected=new EntityAttributeInstance(attribute,changed -> {}); expected.setFrom(original);
            for (var modifier:remote.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute)) expected.addTemporaryModifier(modifier);
            near(RemoteAttributes.project(original,source,ItemStack.EMPTY,remote),expected.getValue());
            for (var modifier:source.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute)) original.addTemporaryModifier(modifier);
            double before=original.getValue();
            near(RemoteAttributes.project(original,source,ItemStack.EMPTY,remote),expected.getValue());
            near(original.getValue(),before);
            near(RemoteAttributes.project(original,source,source,remote),expected.getValue());
            var emptyExpected=new EntityAttributeInstance(attribute,changed -> {}); emptyExpected.setFrom(original);
            for (var modifier:source.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute)) emptyExpected.removeModifier(modifier.getId());
            near(RemoteAttributes.project(original,source,source,ItemStack.EMPTY),emptyExpected.getValue());
        }
        combat();
        System.out.println("PASS RemoteAttributesTest: "+checks+" checks");
    }
    private static void combat() {
        check(!RemoteCombat.canAttack(ItemStack.EMPTY),"empty projection cannot attack");
        near(RemoteCombat.damageScale(ItemStack.EMPTY),0);
        for (var item:new net.minecraft.item.Item[]{Items.STONE,Items.WHEAT,Items.CARROT,Items.COD,Items.APPLE,
                Items.STICK,Items.BOW,Items.CROSSBOW,Items.SHEARS}) {
            var stack=new ItemStack(item); var saved=stack.copy();
            check(RemoteCombat.canAttack(stack),"every nonempty ordinary item can attack");
            near(RemoteCombat.damageScale(stack),.5);
            check(ItemStack.areEqual(stack,saved),"classification does not consume or mutate cargo");
        }
        for (var item:new net.minecraft.item.Item[]{Items.DIAMOND_SWORD,Items.WOODEN_AXE,Items.IRON_PICKAXE,
                Items.IRON_SHOVEL,Items.IRON_HOE,Items.TRIDENT}) {
            var stack=new ItemStack(item);
            check(RemoteCombat.canAttack(stack),"weapon or attributed tool can attack");
            near(RemoteCombat.damageScale(stack),1);
        }
        var speedOnly=new ItemStack(Items.STICK);
        speedOnly.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED,
                new EntityAttributeModifier(new UUID(10,1),"custom speed",1,EntityAttributeModifier.Operation.ADDITION),EquipmentSlot.MAINHAND);
        near(RemoteCombat.damageScale(speedOnly),.5);
        var offhand=new ItemStack(Items.STICK);
        offhand.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(new UUID(10,2),"offhand damage",4,EntityAttributeModifier.Operation.ADDITION),EquipmentSlot.OFFHAND);
        near(RemoteCombat.damageScale(offhand),.5);
        var attributed=new ItemStack(Items.STICK);
        attributed.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(new UUID(10,3),"custom damage",4,EntityAttributeModifier.Operation.ADDITION),EquipmentSlot.MAINHAND);
        near(RemoteCombat.damageScale(attributed),1);
        var damage=new EntityAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE,changed->{});
        damage.setBaseValue(1);
        damage.addTemporaryModifier(new EntityAttributeModifier(new UUID(10,4),"status damage",2,EntityAttributeModifier.Operation.ADDITION));
        near(RemoteAttributes.project(damage,ItemStack.EMPTY,ItemStack.EMPTY,new ItemStack(Items.CARROT))
                *RemoteCombat.damageScale(new ItemStack(Items.CARROT)),1.5);
        near(RemoteAttributes.project(damage,ItemStack.EMPTY,ItemStack.EMPTY,attributed)*RemoteCombat.damageScale(attributed),7);
        near(damage.getValue(),3);
        var depleted=new ItemStack(Items.DIAMOND_SWORD); depleted.setCount(0);
        check(!RemoteCombat.canAttack(depleted),"consumed last item cannot keep attacking");
        near(RemoteCombat.damageScale(depleted),0);
    }
}
