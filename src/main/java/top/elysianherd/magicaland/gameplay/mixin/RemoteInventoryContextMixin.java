package top.elysianherd.magicaland.gameplay.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.elysianherd.magicaland.gameplay.remote.RemoteActionContext;

@Mixin(PlayerInventory.class)
public abstract class RemoteInventoryContextMixin {
    @Shadow @Final public PlayerEntity player;
    @Inject(method="getMainHandStack",at=@At("HEAD"),cancellable=true)
    private void remoteStack(CallbackInfoReturnable<ItemStack> ci) {
        var action=RemoteActionContext.forPlayer(player);
        if (action!=null) ci.setReturnValue(action.stack());
    }
    @Inject(method="getBlockBreakingSpeed",at=@At("HEAD"),cancellable=true)
    private void remoteMiningSpeed(BlockState state,CallbackInfoReturnable<Float> ci) {
        var action=RemoteActionContext.forPlayer(player);
        if (action!=null) ci.setReturnValue(action.stack().getMiningSpeedMultiplier(state));
    }
}
