package top.csituka.magicaland.gameplay.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.magicaland.gameplay.client.RemoteToolClient;

@Mixin(MinecraftClient.class)
public abstract class RemoteInteractionMixin {
    @Inject(method="handleInputEvents",at=@At("HEAD"))
    private void remoteKeys(CallbackInfo ci) { RemoteToolClient.consumeBodyActions(); }
    @Inject(method="doAttack",at=@At("HEAD"),cancellable=true)
    private void remoteAttack(CallbackInfoReturnable<Boolean> ci) {
        if (RemoteToolClient.active()) ci.setReturnValue(false);
    }
    @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
    private void remoteUse(CallbackInfo ci) { if (RemoteToolClient.active()) ci.cancel(); }
    @Inject(method="handleBlockBreaking",at=@At("HEAD"),cancellable=true)
    private void remoteBreaking(boolean breaking,CallbackInfo ci) { if (RemoteToolClient.active()) ci.cancel(); }
}
