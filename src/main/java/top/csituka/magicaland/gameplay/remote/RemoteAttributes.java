package top.csituka.magicaland.gameplay.remote;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.item.ItemStack;

public final class RemoteAttributes {
    private RemoteAttributes() {}
    public static double project(EntityAttributeInstance original,ItemStack source,ItemStack body,ItemStack carried) {
        var attribute=original.getAttribute();
        EntityAttributeInstance projected=new EntityAttributeInstance(attribute,changed -> {});
        projected.setFrom(original);
        for (ItemStack stack:new ItemStack[]{source,body})
            for (var modifier:stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute)) projected.removeModifier(modifier.getId());
        for (var modifier:carried.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute)) {
            projected.removeModifier(modifier.getId()); projected.addTemporaryModifier(modifier);
        }
        return projected.getValue();
    }
}
