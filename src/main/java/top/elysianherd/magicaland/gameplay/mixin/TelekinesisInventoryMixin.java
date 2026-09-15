package top.elysianherd.magicaland.gameplay.mixin;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.elysianherd.magicaland.gameplay.remote.TelekinesisToken;

@Mixin(PlayerInventory.class)
public abstract class TelekinesisInventoryMixin {
    @Shadow public int selectedSlot;
    @Shadow public abstract ItemStack getStack(int slot);
    @Inject(method="dropSelectedItem", at=@At("HEAD"), cancellable=true)
    private void reservedDrop(boolean entireStack, CallbackInfoReturnable<ItemStack> ci) {
        if (TelekinesisToken.isToken(getStack(selectedSlot))) ci.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method="swapSlotWithHotbar", at=@At("HEAD"), cancellable=true)
    private void reservedSwap(int slot, CallbackInfo ci) {
        if (TelekinesisToken.isToken(getStack(selectedSlot)) || TelekinesisToken.isToken(getStack(slot))) ci.cancel();
    }
    @Inject(method="getSwappableHotbarSlot", at=@At("RETURN"), cancellable=true)
    private void reservedPick(CallbackInfoReturnable<Integer> ci) {
        if (!TelekinesisToken.isToken(getStack(ci.getReturnValue()))) return;
        for (int i = 0; i < 9; i++) if (!TelekinesisToken.isToken(getStack(i))) { ci.setReturnValue(i); return; }
    }
}
