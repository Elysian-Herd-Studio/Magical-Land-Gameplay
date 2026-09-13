package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.magicaland.gameplay.remote.RemoteActionContext;
import top.csituka.magicaland.gameplay.remote.TelekinesisToken;

@Mixin(PlayerEntity.class)
public abstract class TelekinesisPlayerMixin {
    @Inject(method="attack", at=@At("HEAD"), cancellable=true)
    private void reservedAttack(Entity target, CallbackInfo ci) {
        var player = (PlayerEntity)(Object)this;
        if (RemoteActionContext.forPlayer(player) == null && TelekinesisToken.isToken(player.getMainHandStack())) ci.cancel();
    }
    @Inject(method="dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at=@At("HEAD"), cancellable=true)
    private void reservedDrop(ItemStack stack, boolean spread, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> ci) {
        if (TelekinesisToken.isToken(stack)) ci.setReturnValue(null);
    }
}
