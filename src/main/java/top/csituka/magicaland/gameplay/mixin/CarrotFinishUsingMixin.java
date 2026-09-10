package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.magicaland.gameplay.easteregg.CarrotMisunderstanding;

@Mixin(Item.class)
public abstract class CarrotFinishUsingMixin {
    @Inject(method = "finishUsing", at = @At("RETURN"))
    private void magicaland$afterEating(ItemStack stack, World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (user instanceof ServerPlayerEntity player)
            CarrotMisunderstanding.carrotFinished(player, (Item) (Object) this);
    }
}
