package top.elysianherd.magicaland.gameplay.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.elysianherd.magicaland.gameplay.remote.TelekinesisToken;

@Mixin(Slot.class)
public abstract class TelekinesisSlotMixin {
    @Shadow public abstract ItemStack getStack();
    @Inject(method="canInsert", at=@At("HEAD"), cancellable=true)
    private void reservedInsert(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (TelekinesisToken.isToken(stack) || TelekinesisToken.isToken(getStack())) ci.setReturnValue(false);
    }
    @Inject(method="canTakeItems", at=@At("HEAD"), cancellable=true)
    private void reservedTake(PlayerEntity player, CallbackInfoReturnable<Boolean> ci) {
        if (TelekinesisToken.isToken(getStack())) ci.setReturnValue(false);
    }
}
