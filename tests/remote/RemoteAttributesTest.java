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

public final class RemoteAttributesTest {
    private static int checks;
    private static void near(double actual,double expected) { checks++; if (Math.abs(actual-expected)>1e-6) throw new AssertionError(actual+" != "+expected); }
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
        System.out.println("PASS RemoteAttributesTest: "+checks+" checks");
    }
}
