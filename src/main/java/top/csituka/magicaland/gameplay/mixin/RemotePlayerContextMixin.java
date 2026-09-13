package top.csituka.magicaland.gameplay.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.magicaland.gameplay.remote.RemoteActionContext;

@Mixin(PlayerEntity.class)
public abstract class RemotePlayerContextMixin {
    @Unique
    private static boolean magicaland$automaticMining(PlayerEntity player) {
        var tool=RemoteActionContext.toolFor(player);
        return tool!=null && tool.autonomous();
    }
    @Redirect(method="getBlockBreakingSpeed",at=@At(value="INVOKE",target="Lnet/minecraft/entity/player/PlayerEntity;isOnGround()Z"),require=1,allow=1)
    private boolean magicaland$automaticMiningGround(PlayerEntity player) {
        return magicaland$automaticMining(player) || player.isOnGround();
    }
    @Redirect(method="getBlockBreakingSpeed",at=@At(value="INVOKE",target="Lnet/minecraft/entity/player/PlayerEntity;isSubmergedIn(Lnet/minecraft/registry/tag/TagKey;)Z"),require=1,allow=1)
    private boolean magicaland$automaticMiningSubmerged(PlayerEntity player,TagKey<Fluid> fluid) {
        return !magicaland$automaticMining(player) && player.isSubmergedIn(fluid);
    }
    @Inject(method="getEquippedStack",at=@At("HEAD"),cancellable=true)
    private void remoteEquipment(EquipmentSlot slot,CallbackInfoReturnable<ItemStack> ci) {
        var action=RemoteActionContext.forPlayer((PlayerEntity)(Object)this);
        if (action!=null && slot==EquipmentSlot.MAINHAND) ci.setReturnValue(action.stack());
    }
    @Inject(method="equipStack",at=@At("HEAD"),cancellable=true)
    private void remoteEquip(EquipmentSlot slot,ItemStack stack,CallbackInfo ci) {
        var action=RemoteActionContext.forPlayer((PlayerEntity)(Object)this);
        if (action!=null && slot==EquipmentSlot.MAINHAND) { action.setStack(stack); ci.cancel(); }
    }
    @Inject(method="getAttackCooldownProgress",at=@At("HEAD"),cancellable=true)
    private void remoteCooldown(float partial,CallbackInfoReturnable<Float> ci) {
        var action=RemoteActionContext.forPlayer((PlayerEntity)(Object)this);
        if (action!=null) ci.setReturnValue(action.attackCooldown(partial));
    }
    @Inject(method="resetLastAttackedTicks",at=@At("HEAD"),cancellable=true)
    private void remoteResetCooldown(CallbackInfo ci) {
        var action=RemoteActionContext.forPlayer((PlayerEntity)(Object)this);
        if (action!=null) { action.resetAttackCooldown(); ci.cancel(); }
    }
}
