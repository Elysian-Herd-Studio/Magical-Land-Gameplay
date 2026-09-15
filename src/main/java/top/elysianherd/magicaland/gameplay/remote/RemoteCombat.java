package top.elysianherd.magicaland.gameplay.remote;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;

public final class RemoteCombat {
    private RemoteCombat() {}
    public static boolean canAttack(ItemStack stack) { return !stack.isEmpty(); }
    public static double damageScale(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(EntityAttributes.GENERIC_ATTACK_DAMAGE).isEmpty() ? .5 : 1;
    }
}
